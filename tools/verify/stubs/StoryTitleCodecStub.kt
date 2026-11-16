package com.femi.gallery.data.story

import com.femi.gallery.domain.model.StorySubtitleSpec
import com.femi.gallery.domain.model.StoryTitleSpec

/**
 * Stand-in for `StoryTitleCodec.kt`, which the verification scripts leave out: its `Json` and the generated
 * `serializer()` come from kotlinx.serialization's compiler plugin, which is not available here. It keeps the
 * public signatures, so callers are still type-checked; it does no encoding, so its behaviour (and
 * `StoryTitleCodecTest`) can only be checked by the Gradle build.
 */
object StoryTitleCodec {
    fun encodeTitle(spec: StoryTitleSpec?): String? = null
    fun decodeTitle(encoded: String?): StoryTitleSpec? = null
    fun encodeSubtitle(spec: StorySubtitleSpec?): String? = null
    fun decodeSubtitle(encoded: String?): StorySubtitleSpec? = null
}
