package com.femi.gallery.domain.edit

/**
 * Color math for the editor. A matrix is 20 numbers in Android's `ColorMatrix` layout: four rows
 * (red, green, blue, alpha) of five columns, the last column being an offset in 0..255 units.
 * The same array drives both the live preview and the saved picture.
 */
object ColorMatrices {

    private const val LUMA_R = 0.213f
    private const val LUMA_G = 0.715f
    private const val LUMA_B = 0.072f

    fun identity(): FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    /** The matrix that applies [second] and then [first]-style composition: result = first x second. */
    fun times(first: FloatArray, second: FloatArray): FloatArray {
        val result = FloatArray(20)
        for (row in 0 until 4) {
            for (column in 0 until 5) {
                var sum = 0f
                for (k in 0 until 4) sum += first[row * 5 + k] * second[k * 5 + column]
                if (column == 4) sum += first[row * 5 + 4]
                result[row * 5 + column] = sum
            }
        }
        return result
    }

    /** Adds a fixed amount of light: -1..1 shifts each channel by up to about 35% of its range. */
    fun brightness(amount: Float): FloatArray {
        val offset = amount * 90f
        return identity().also {
            it[4] = offset
            it[9] = offset
            it[14] = offset
        }
    }

    /** Stretches (or squeezes) values around mid-gray: -1 is flat gray, 1 doubles the contrast. */
    fun contrast(amount: Float): FloatArray {
        val scale = 1f + amount
        val offset = 128f * (1f - scale)
        return identity().also {
            it[0] = scale
            it[6] = scale
            it[12] = scale
            it[4] = offset
            it[9] = offset
            it[14] = offset
        }
    }

    /** -1 is black and white, 0 unchanged, 1 doubles the color. */
    fun saturation(amount: Float): FloatArray {
        val v = 1f + amount
        val inverse = 1f - v
        return floatArrayOf(
            LUMA_R * inverse + v, LUMA_G * inverse, LUMA_B * inverse, 0f, 0f,
            LUMA_R * inverse, LUMA_G * inverse + v, LUMA_B * inverse, 0f, 0f,
            LUMA_R * inverse, LUMA_G * inverse, LUMA_B * inverse + v, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    /** Positive is warmer (more red, less blue), negative is cooler. */
    fun warmth(amount: Float): FloatArray = identity().also {
        it[0] = 1f + 0.12f * amount
        it[12] = 1f - 0.12f * amount
        it[4] = 12f * amount
        it[14] = -12f * amount
    }

    fun filter(filter: EditFilter): FloatArray = when (filter) {
        EditFilter.None -> identity()
        EditFilter.Vivid -> times(saturation(0.35f), contrast(0.12f))
        EditFilter.Warm -> times(saturation(0.1f), warmth(0.5f))
        EditFilter.Cool -> warmth(-0.5f)
        EditFilter.Mono -> saturation(-1f)
        EditFilter.Noir -> times(brightness(-0.1f), times(contrast(0.35f), saturation(-1f)))
        EditFilter.Sepia -> floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        EditFilter.Fade -> times(saturation(-0.2f), times(brightness(0.1f), contrast(-0.25f)))
        EditFilter.Dramatic -> times(brightness(-0.05f), times(saturation(0.2f), contrast(0.3f)))
    }

    /** Brightness, then contrast, saturation and warmth, and finally the filter on top. */
    fun forEdit(state: EditState): FloatArray {
        var matrix = identity()
        if (state.brightness != 0f) matrix = times(brightness(state.brightness), matrix)
        if (state.contrast != 0f) matrix = times(contrast(state.contrast), matrix)
        if (state.saturation != 0f) matrix = times(saturation(state.saturation), matrix)
        if (state.warmth != 0f) matrix = times(warmth(state.warmth), matrix)
        if (state.filter != EditFilter.None) matrix = times(filter(state.filter), matrix)
        return matrix
    }

    /** Applies [matrix] to one pixel (channels 0..255) and clamps; mainly for tests. */
    fun apply(matrix: FloatArray, red: Float, green: Float, blue: Float): FloatArray = FloatArray(3) { row ->
        (matrix[row * 5] * red + matrix[row * 5 + 1] * green + matrix[row * 5 + 2] * blue + matrix[row * 5 + 4])
            .coerceIn(0f, 255f)
    }
}
