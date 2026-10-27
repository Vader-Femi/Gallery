package com.femi.gallery.domain.color

/**
 * Pure Kotlin colour classifier that takes an array of RGB pixel integers (packed 0xRRGGBB or 0xAARRGGBB),
 * width, and height, and classifies the dominant colours and brightness into a set of color/tone tags.
 */
object ColorClassifier {

    private const val COLOUR_SHARE = 0.12
    private const val NEUTRAL_SHARE = 0.25
    private const val TONE_SHARE = 0.40

    fun classify(pixels: IntArray, width: Int, height: Int): Set<String> {
        if (pixels.isEmpty() || width <= 0 || height <= 0) return emptySet()

        val votes = HashMap<String, Int>()
        var totalSamples = 0

        val stepX = maxOf(1, width / 32)
        val stepY = maxOf(1, height / 32)

        for (y in 0 until height step stepY) {
            for (x in 0 until width step stepX) {
                val index = y * width + x
                if (index !in pixels.indices) continue
                val p = pixels[index]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF

                val tags = classifyPixel(r, g, b)
                for (tag in tags) {
                    votes[tag] = (votes[tag] ?: 0) + 1
                }
                totalSamples++
            }
        }

        if (totalSamples == 0) return emptySet()

        val result = HashSet<String>()
        for ((tag, count) in votes) {
            if (count >= totalSamples * minimumShare(tag)) result.add(tag)
        }
        return result
    }

    /**
     * How much of the picture a colour must cover to be named. A vivid colour is noticed in a small
     * area, but the neutrals and the dark/light tones are everywhere (a patch of shadow, a white
     * wall), so they must dominate or "dark" and "white" would match nearly every photo.
     */
    fun minimumShare(tag: String): Double = when (tag) {
        "black", "white", "gray", "grey" -> NEUTRAL_SHARE
        "dark", "light" -> TONE_SHARE
        else -> COLOUR_SHARE
    }

    private fun classifyPixel(r: Int, g: Int, b: Int): List<String> {
        val rf = r / 255.0f
        val gf = g / 255.0f
        val bf = b / 255.0f

        val max = maxOf(rf, gf, bf)
        val min = minOf(rf, gf, bf)
        val delta = max - min

        val value = max
        val saturation = if (max == 0f) 0f else delta / max

        val tags = ArrayList<String>(2)

        if (value < 0.18f) {
            tags.add("black")
            tags.add("dark")
            return tags
        }
        if (value > 0.85f && saturation < 0.15f) {
            tags.add("white")
            tags.add("light")
            return tags
        }
        if (saturation < 0.2f) {
            tags.add("gray")
            tags.add("grey")
            if (value < 0.45f) tags.add("dark")
            else if (value > 0.65f) tags.add("light")
            return tags
        }

        var hue = 0f
        if (delta != 0f) {
            hue = when (max) {
                rf -> 60f * (((gf - bf) / delta) % 6f)
                gf -> 60f * (((bf - rf) / delta) + 2f)
                else -> 60f * (((rf - gf) / delta) + 4f)
            }
            if (hue < 0f) hue += 360f
        }

        val color = when {
            hue < 15f || hue >= 345f -> "red"
            hue in 15f..<45f -> if (value < 0.55f && saturation > 0.3f) "brown" else "orange"
            hue in 45f..<70f -> "yellow"
            hue in 70f..<170f -> "green"
            hue in 170f..<260f -> "blue"
            hue in 260f..<315f -> "purple"
            else -> "pink"
        }
        tags.add(color)

        if (value < 0.35f) tags.add("dark")
        else if (value > 0.7f) tags.add("light")

        return tags
    }
}
