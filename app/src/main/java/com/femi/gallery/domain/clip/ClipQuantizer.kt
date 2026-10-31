package com.femi.gallery.domain.clip

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Embeddings are compared by the angle between them, so only their direction matters. Scaled to length
 * one, every number lies between -1 and 1 and can be kept as a signed byte (value times 127). The error
 * that adds to a comparison is a few thousandths, far below the difference between a match and a miss.
 */
object ClipQuantizer {
    private const val SCALE = 127f

    /** [vector] scaled to length one; a vector of zeros stays zeros. */
    fun normalize(vector: FloatArray): FloatArray {
        var sum = 0.0
        for (x in vector) sum += x.toDouble() * x
        val length = sqrt(sum).toFloat()
        if (length == 0f) return vector.copyOf()
        return FloatArray(vector.size) { vector[it] / length }
    }

    /** The bytes that keep [vector] (any length; it is normalised first). Empty for an empty or all-zero vector. */
    fun quantize(vector: FloatArray): ByteArray {
        if (vector.isEmpty() || vector.all { it == 0f }) return ByteArray(0)
        val unit = normalize(vector)
        return ByteArray(unit.size) { (unit[it] * SCALE).roundToInt().coerceIn(-127, 127).toByte() }
    }

    /** The cosine of the angle between [query] (length one) and the vector stored at [offset] in [data]. */
    fun cosine(query: FloatArray, data: ByteArray, offset: Int): Float {
        var dot = 0f
        for (i in query.indices) dot += query[i] * data[offset + i]
        return dot / SCALE
    }

    /** The largest difference between two vectors' numbers; used to check that keeping a vector as bytes loses little. */
    fun maxDifference(unit: FloatArray, quantized: ByteArray): Float {
        var worst = 0f
        for (i in unit.indices) worst = maxOf(worst, abs(unit[i] - quantized[i] / SCALE))
        return worst
    }
}
