package com.femi.gallery.domain.edit

/**
 * A vignette darkens the picture towards its corners. It is drawn as a radial gradient that is
 * clear in the middle and, at the corners, black at [edgeAlpha] opacity. The preview and the saved
 * photo use the same numbers, so what you see is what you save.
 */
object Vignette {

    /** Where along the radius (centre 0, corner 1) the darkening begins. */
    const val CLEAR_UNTIL = 0.45f

    /** Opacity of the black at the corners for the slider position [value] (0..1). */
    fun edgeAlpha(value: Float): Float = value.coerceIn(0f, 1f) * MAX_ALPHA

    /** Opacity at [radius] (centre 0, corner 1) for the slider position [value]; smooth, not linear. */
    fun alphaAt(radius: Float, value: Float): Float {
        if (radius <= CLEAR_UNTIL) return 0f
        val t = ((radius - CLEAR_UNTIL) / (1f - CLEAR_UNTIL)).coerceIn(0f, 1f)
        return edgeAlpha(value) * t * t * (3f - 2f * t)
    }

    /**
     * (radius, opacity) pairs, centre 0 to corner 1, to build a gradient from for the slider position
     * [value]. Several stops approximate the smooth fall-off with plain linear blending.
     */
    fun stops(value: Float): List<Pair<Float, Float>> =
        listOf(0f, CLEAR_UNTIL, 0.6f, 0.75f, 0.9f, 1f).map { it to alphaAt(it, value) }

    private const val MAX_ALPHA = 0.85f
}
