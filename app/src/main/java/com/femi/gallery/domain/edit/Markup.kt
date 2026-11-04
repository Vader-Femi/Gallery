package com.femi.gallery.domain.edit

/** A position on the photo as fractions (0..1) of its width and height, so that it means the same at any size. */
data class MarkupPoint(val x: Float, val y: Float)

/**
 * Something drawn on top of the photo. Sizes are fractions of the photo's shorter side, so that the preview and the
 * saved photo look the same whatever their pixel sizes. [id] is how the editor tells items apart.
 */
sealed interface MarkupItem {
    val id: Long
}

/** A line drawn with a finger; [highlighter] makes it wide and see-through. */
data class MarkupStroke(
    override val id: Long,
    val points: List<MarkupPoint>,
    val colorArgb: Int,
    val widthFraction: Float,
    val highlighter: Boolean = false,
) : MarkupItem

/** A piece of text (lines separated by line breaks) centred on [center], turned by [rotationDegrees]. */
data class MarkupText(
    override val id: Long,
    val text: String,
    val center: MarkupPoint,
    /** The height of one line of text. */
    val sizeFraction: Float,
    val rotationDegrees: Float = 0f,
    val colorArgb: Int = MarkupPalette.White,
) : MarkupItem

/** An emoji centred on [center]. */
data class MarkupSticker(
    override val id: Long,
    val emoji: String,
    val center: MarkupPoint,
    val sizeFraction: Float,
    val rotationDegrees: Float = 0f,
) : MarkupItem

object MarkupPalette {
    const val White = 0xFFFFFFFF.toInt()
    const val Black = 0xFF000000.toInt()
    const val Red = 0xFFE53935.toInt()

    /** The colours offered for drawing and text. */
    val colors: List<Int> = listOf(
        White,
        Black,
        Red,
        0xFFFB8C00.toInt(), // orange
        0xFFFDD835.toInt(), // yellow
        0xFF43A047.toInt(), // green
        0xFF1E88E5.toInt(), // blue
        0xFF8E24AA.toInt(), // purple
        0xFFEC407A.toInt(), // pink
    )

    /** Emoji offered as stickers. */
    val stickers: List<String> = listOf(
        "😀", "😂", "😍", "😎", "🥳", "😢", "😡", "🤔", "👍", "👎", "👏", "🙏",
        "❤️", "🔥", "⭐", "✨", "🎉", "🎂", "🎁", "🌈", "☀️", "🌙", "🌸", "🍕",
        "🐶", "🐱", "🚗", "✈️", "📍", "💯", "✅", "❌",
    )
}
