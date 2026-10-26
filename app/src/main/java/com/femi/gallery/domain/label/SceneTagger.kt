package com.femi.gallery.domain.label

object SceneTagger {
    fun tag(
        displayName: String,
        relativePath: String,
        ocrText: String,
        colors: Set<String>,
        width: Int,
        height: Int,
    ): Set<String> {
        val tags = HashSet<String>()
        val nameLower = displayName.lowercase()
        val pathLower = relativePath.lowercase()
        val textLower = ocrText.lowercase()
        val combined = "$nameLower $pathLower $textLower"

        val keywords = mapOf(
            "screenshot" to listOf("screenshot", "screen_", "screencast"),
            "document" to listOf("invoice", "receipt", "doc", "pdf", "scan", "bill", "note", "text"),
            "food" to listOf("food", "meal", "lunch", "dinner", "breakfast", "restaurant", "cafe", "coffee", "dish", "recipe"),
            "car" to listOf("car", "auto", "vehicle", "drive", "parking", "road"),
            "pet" to listOf("dog", "cat", "pet", "puppy", "kitten", "animal"),
            "nature" to listOf("park", "forest", "tree", "plant", "flower", "garden", "hiking", "mountain"),
            "beach" to listOf("beach", "ocean", "sea", "sand", "wave", "coast"),
            "sunset" to listOf("sunset", "sunrise", "dusk", "dawn", "golden"),
            "travel" to listOf("trip", "vacation", "holiday", "tour", "flight", "hotel"),
            "selfie" to listOf("selfie", "portrait", "face"),
        )

        for ((tag, words) in keywords) {
            if (words.any { combined.contains(it) }) {
                tags.add(tag)
            }
        }

        if (colors.contains("blue") && colors.contains("yellow")) {
            tags.add("beach")
            tags.add("outdoors")
        }
        if (colors.contains("green")) {
            tags.add("nature")
            tags.add("outdoors")
        }
        if (colors.contains("dark") && colors.contains("black")) {
            tags.add("night")
            tags.add("indoor")
        }
        if (width > 0 && height > 0) {
            val ratio = height.toFloat() / width.toFloat()
            if (ratio > 1.7f) tags.add("screenshot")
            else if (ratio < 0.6f) tags.add("panorama")
            else if (ratio in 0.9f..1.1f) tags.add("square")
        }

        return tags
    }
}
