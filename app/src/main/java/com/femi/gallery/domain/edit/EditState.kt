package com.femi.gallery.domain.edit

/** A crop rectangle as fractions (0..1) of the image's width and height. */
data class CropRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val isFull: Boolean get() = left <= 0f && top <= 0f && right >= 1f && bottom >= 1f

    companion object {
        val Full = CropRect(0f, 0f, 1f, 1f)
    }
}

enum class EditFilter { None, Vivid, Warm, Cool, Mono, Noir, Sepia, Fade, Dramatic }

/**
 * Everything the editor can change, kept separate from the photo so edits are non-destructive
 * until saved. Adjustments run from -1 to 1 (0 = untouched).
 */
data class EditState(
    /** Quarter turns clockwise, 0..3. */
    val rotationTurns: Int = 0,
    val straightenAngle: Float = 0f, // -45..45 degrees
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val crop: CropRect = CropRect.Full,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
    val sharpen: Float = 0f,
    val vignette: Float = 0f,
    val filter: EditFilter = EditFilter.None,
) {
    val isUnchanged: Boolean get() = this == EditState()

    /** The 4x5 color matrix (Android's layout) for the adjustments and filter. */
    fun colorMatrix(): FloatArray = ColorMatrices.forEdit(this)
}
