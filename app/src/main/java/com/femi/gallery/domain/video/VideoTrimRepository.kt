package com.femi.gallery.domain.video

import kotlinx.coroutines.flow.Flow

/** What happens while a trimmed copy of a video is made. */
sealed interface TrimEvent {
    /** [percent] of the export that is done (0 to 100). */
    data class Progress(val percent: Int) : TrimEvent

    /** The copy is in the library: its content [uri] and the file [name] it was given. */
    data class Done(val uri: String, val name: String) : TrimEvent

    data class Failed(val reason: TrimFailure) : TrimEvent
}

enum class TrimFailure {
    /** The original cannot be read (gone, or no permission). */
    SourceUnreadable,

    /** There is not room for the copy. */
    NotEnoughSpace,

    /** This phone cannot decode or encode the video. */
    Unsupported,

    Other,
}

interface VideoTrimRepository {
    /**
     * Exports the part [range] of the video [uri] at [speed] as a new MP4 next to the original (the original is never changed).
     * If [muteAudio] is true, the audio track is stripped.
     * If [rotationDegrees] is non-zero, the video is rotated clockwise by the given degrees.
     * The flow ends after [TrimEvent.Done] or [TrimEvent.Failed]; cancelling the collector cancels the export.
     */
    fun trim(
        uri: String,
        range: TrimRange,
        speed: Float = 1f,
        muteAudio: Boolean = false,
        rotationDegrees: Int = 0,
    ): Flow<TrimEvent>

    /** The times (ms, sorted) of the key frames of the video's picture track; empty when they cannot be read. */
    suspend fun keyFrames(uri: String): LongArray
}
