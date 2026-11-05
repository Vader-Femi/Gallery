package com.femi.gallery.domain.clip

import kotlin.math.max
import kotlin.math.roundToInt

enum class ClipNormalization(
    val displayName: String,
    val mean: FloatArray,
    val std: FloatArray,
) {
    OpenAiClip(
        displayName = "OpenAI CLIP",
        mean = floatArrayOf(0.48145466f, 0.4578275f, 0.40821073f),
        std = floatArrayOf(0.26862954f, 0.26130258f, 0.27577711f),
    ),
    ImageNet(
        displayName = "ImageNet (MobileCLIP / SigLIP)",
        mean = floatArrayOf(0.485f, 0.456f, 0.406f),
        std = floatArrayOf(0.229f, 0.224f, 0.225f),
    ),
}

/**
 * What a CLIP image model expects as input: a [SIZE] x [SIZE] picture (the photo scaled until its shorter
 * side fits, then cropped in the middle) as three planes of numbers, red then green then blue, each pixel
 * moved to 0..1 and then shifted and scaled by the averages and spreads of the training photos.
 */
object ClipPreprocess {
    const val SIZE = 224

    val MEAN = ClipNormalization.OpenAiClip.mean
    val STD = ClipNormalization.OpenAiClip.std

    /** The size of a [width] x [height] photo scaled until its shorter side is [size]. */
    fun scaledSize(width: Int, height: Int, size: Int = SIZE): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return size to size
        val scale = size.toDouble() / minOf(width, height)
        return max(size, (width * scale).roundToInt()) to max(size, (height * scale).roundToInt())
    }

    /** The top left corner of the middle [size] x [size] square of a picture that is [scaledWidth] x [scaledHeight]. */
    fun cropOrigin(scaledWidth: Int, scaledHeight: Int, size: Int = SIZE): Pair<Int, Int> =
        ((scaledWidth - size) / 2).coerceAtLeast(0) to ((scaledHeight - size) / 2).coerceAtLeast(0)

    /** Packed ARGB [pixels] of a [size] x [size] picture as the planar, normalised numbers the model takes. */
    fun toTensor(
        pixels: IntArray,
        size: Int = SIZE,
        normalization: ClipNormalization = ClipNormalization.OpenAiClip,
    ): FloatArray {
        require(pixels.size == size * size) { "Expected ${size * size} pixels, got ${pixels.size}" }
        val plane = size * size
        val out = FloatArray(3 * plane)
        val mean = normalization.mean
        val std = normalization.std
        for (i in 0 until plane) {
            val p = pixels[i]
            out[i] = (((p shr 16) and 0xFF) / 255f - mean[0]) / std[0]
            out[plane + i] = (((p shr 8) and 0xFF) / 255f - mean[1]) / std[1]
            out[2 * plane + i] = ((p and 0xFF) / 255f - mean[2]) / std[2]
        }
        return out
    }
}
