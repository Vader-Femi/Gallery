package com.femi.gallery.data.story

import com.femi.gallery.domain.model.StorySubtitleSpec
import com.femi.gallery.domain.model.StoryTitleSpec
import kotlinx.serialization.json.Json

/** Codec to serialize structured [StoryTitleSpec] and [StorySubtitleSpec] for Room database storage. */
object StoryTitleCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    fun encodeTitle(spec: StoryTitleSpec?): String? {
        if (spec == null) return null
        return runCatching { json.encodeToString(StoryTitleSpec.serializer(), spec) }.getOrNull()
    }

    fun decodeTitle(encoded: String?): StoryTitleSpec? {
        if (encoded.isNullOrBlank()) return null
        return runCatching { json.decodeFromString(StoryTitleSpec.serializer(), encoded) }
            .getOrElse {
                // Backward compatibility for pre-migration cached plain strings
                StoryTitleSpec.Custom(encoded)
            }
    }

    fun encodeSubtitle(spec: StorySubtitleSpec?): String? {
        if (spec == null) return null
        return runCatching { json.encodeToString(StorySubtitleSpec.serializer(), spec) }.getOrNull()
    }

    fun decodeSubtitle(encoded: String?): StorySubtitleSpec? {
        if (encoded.isNullOrBlank()) return null
        return runCatching { json.decodeFromString(StorySubtitleSpec.serializer(), encoded) }.getOrNull()
    }
}
