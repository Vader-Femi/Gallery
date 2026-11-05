package com.femi.gallery.domain.model

object JunkPhotoFilter {
    private val JUNK_KEYWORDS = listOf(
        "screenshot",
        "screen_recording",
        "screenrecording",
        "whatsapp stickers",
        "whatsapp animated gifs",
    )

    private val DOCUMENT_LABELS = setOf(
        "receipt",
        "document",
        "paper",
        "invoice",
        "passport",
        "text",
        "envelope",
    )

    private val MEMORY_LABELS = setOf(
        "person",
        "human",
        "face",
        "smile",
        "beach",
        "nature",
        "food",
        "pet",
        "dog",
        "cat",
        "animal",
        "sky",
        "sunset",
        "tree",
        "flower",
        "mountain",
        "lake",
        "ocean",
        "sea",
    )

    fun isJunk(
        displayName: String,
        relativePath: String,
        bucketName: String,
        labels: Collection<String> = emptyList(),
        hasFace: Boolean = false,
        isFavorite: Boolean = false,
    ): Boolean {
        if (isFavorite) return false
        if (hasFace) return false

        val lowerName = displayName.lowercase()
        val lowerPath = relativePath.lowercase()
        val lowerBucket = bucketName.lowercase()

        for (kw in JUNK_KEYWORDS) {
            if (lowerPath.contains(kw) || lowerBucket.contains(kw)) return true
        }
        if (lowerName.startsWith("screenshot") || lowerName.startsWith("screen_") || lowerName.contains("_screenshot_")) {
            return true
        }
        if (lowerName.startsWith("screenrecording") || lowerName.startsWith("screen_recording")) {
            return true
        }
        if (lowerName.endsWith(".webp") && lowerPath.contains("whatsapp")) {
            return true
        }

        if (labels.isNotEmpty()) {
            val lowerLabels = labels.map { it.lowercase() }
            val isDoc = lowerLabels.any { it in DOCUMENT_LABELS }
            val isMemory = lowerLabels.any { it in MEMORY_LABELS }
            if (isDoc && !isMemory) {
                return true
            }
        }

        return false
    }
}
