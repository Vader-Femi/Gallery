package com.femi.gallery.domain.clip

/** What an ONNX model says about one of its inputs or outputs. */
data class ModelPort(
    val name: String,
    /** The size of each dimension; a dimension the model leaves open (such as the batch) is -1. */
    val shape: LongArray,
    val type: ElementType,
) {
    enum class ElementType { Float, Int32, Int64, Other }

    val rank: Int get() = shape.size
}

/**
 * CLIP models come from several exporters, which name their inputs and outputs differently
 * ("pixel_values" or "image", "text_embeds" or "output"...). This chooses the right ones from what a model
 * reports about itself, so that files from any of them can be used.
 */
object ClipModelIo {

    /** The picture input: a decimal-number input shaped batch, 3 colour planes, height, width. */
    fun imageInput(inputs: List<ModelPort>): ModelPort? =
        inputs.firstOrNull { it.type == ModelPort.ElementType.Float && it.rank == 4 && it.shape[1].let { c -> c == 3L || c < 0 } }

    /** The side of the square picture the model wants; models that leave it open get the CLIP standard. */
    fun imageSize(input: ModelPort): Int {
        val h = input.shape.getOrElse(2) { -1 }
        val w = input.shape.getOrElse(3) { -1 }
        return if (h > 0 && h == w) h.toInt() else ClipPreprocess.SIZE
    }

    /** The text input: whole numbers shaped batch, length. Prefers one called "input_ids". */
    fun textIds(inputs: List<ModelPort>): ModelPort? {
        val whole = inputs.filter { (it.type == ModelPort.ElementType.Int64 || it.type == ModelPort.ElementType.Int32) && it.rank == 2 }
        return whole.firstOrNull { it.name.contains("input_ids", ignoreCase = true) }
            ?: whole.firstOrNull { !it.name.contains("mask", ignoreCase = true) }
    }

    /** The attention mask input of a text model, if it has one (it marks which of the numbers are real words). */
    fun textMask(inputs: List<ModelPort>): ModelPort? =
        inputs.firstOrNull { it.name.contains("mask", ignoreCase = true) && it.rank == 2 }

    /** How many numbers a text model takes: what it says, or CLIP's 77 when it leaves that open. */
    fun contextLength(ids: ModelPort): Int {
        val n = ids.shape.getOrElse(1) { -1 }
        return if (n > 0) n.toInt() else ClipTokenizer.CONTEXT_LENGTH
    }

    /**
     * The output that holds the embedding: one shaped batch, vector, preferring one called "...embeds...".
     * A model that only gives the raw features of every word or patch (three dimensions) has no embedding and is not usable.
     */
    fun embeddingOutput(outputs: List<ModelPort>): ModelPort? {
        val vectors = outputs.filter { it.type == ModelPort.ElementType.Float && it.rank == 2 }
        return vectors.firstOrNull { it.name.contains("embed", ignoreCase = true) } ?: vectors.firstOrNull()
    }
}
