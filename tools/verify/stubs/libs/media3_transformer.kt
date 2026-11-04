@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.media3.transformer

import androidx.media3.common.MediaItem

class Composition
class ProgressHolder { @JvmField var progress: Int = 0 }

class EditedMediaItem private constructor() {
    class Builder(mediaItem: MediaItem) {
        fun setRemoveAudio(removeAudio: Boolean): Builder = this
        fun setSpeed(parameters: androidx.media3.common.SpeedParameters): Builder = this
        fun setSpeed(provider: androidx.media3.common.audio.SpeedProvider): Builder = this
        fun setEffects(effects: Effects): Builder = this
        fun build(): EditedMediaItem = EditedMediaItem()
    }
}

class Effects(
    val audioProcessors: List<Any> = emptyList(),
    val videoEffects: List<Any> = emptyList(),
)

class ExportResult {
    @JvmField val fileSizeBytes: Long = 0
    @JvmField val optimizationResult: Int = 0
    @JvmField val videoEncoderName: String? = null
    @JvmField val audioEncoderName: String? = null
}

class ExportException : Exception() {
    @JvmField val errorCode: Int = 0
    val errorCodeName: String = ""
    companion object {
        const val ERROR_CODE_IO_FILE_NOT_FOUND = 2005
        const val ERROR_CODE_IO_NO_PERMISSION = 2006
        const val ERROR_CODE_DECODER_INIT_FAILED = 3001
        const val ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED = 4003
    }
}

class Transformer private constructor() {
    interface Listener {
        fun onCompleted(composition: Composition, exportResult: ExportResult) {}
        fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {}
    }

    class Builder(context: android.content.Context) {
        fun setLooper(looper: android.os.Looper): Builder = this
        fun experimentalSetTrimOptimizationEnabled(enabled: Boolean): Builder = this
        fun addListener(listener: Listener): Builder = this
        fun build(): Transformer = Transformer()
    }

    fun start(editedMediaItem: EditedMediaItem, path: String) {}
    fun getProgress(progressHolder: ProgressHolder): Int = 0
    fun cancel() {}

    companion object {
        const val PROGRESS_STATE_AVAILABLE = 2
    }
}
