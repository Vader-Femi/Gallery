package com.femi.gallery.presentation.trim

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.femi.gallery.domain.usecase.TrimVideoUseCase
import com.femi.gallery.domain.video.TrimEvent
import com.femi.gallery.domain.video.TrimFailure
import com.femi.gallery.domain.video.TrimMath
import com.femi.gallery.domain.video.TrimRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface TrimExport {
    data object Idle : TrimExport
    data class Running(val percent: Int) : TrimExport
    data class Done(val name: String) : TrimExport
    data class Failed(val reason: TrimFailure) : TrimExport
}

data class TrimUiState(
    /** 0 until the player knows the length of the video. */
    val durationMs: Long = 0L,
    val range: TrimRange = TrimRange(0L, 0L),
    val speed: Float = 1f,
    val isMuted: Boolean = false,
    val rotationDegrees: Int = 0,
    val export: TrimExport = TrimExport.Idle,
) {
    val isReady: Boolean get() = durationMs > 0L
    val canSave: Boolean get() = isReady && (
        !range.isWholeVideo(durationMs) ||
        speed != 1f ||
        isMuted ||
        rotationDegrees != 0
    ) && export !is TrimExport.Running
}

@HiltViewModel
class TrimViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val trimVideo: TrimVideoUseCase,
) : ViewModel() {

    val uri: String = checkNotNull(savedStateHandle.get<String>(URI_ARG))

    private val _state = MutableStateFlow(TrimUiState())
    val state: StateFlow<TrimUiState> = _state.asStateFlow()

    private var exportJob: Job? = null

    /** Called by the player when it knows how long the video is; only the first call sets the range. */
    fun onDuration(durationMs: Long) {
        if (durationMs <= 0L) return
        _state.update { if (it.isReady) it else it.copy(durationMs = durationMs, range = TrimMath.whole(durationMs)) }
    }

    fun setStart(startMs: Long) = _state.update { it.copy(range = TrimMath.withStart(it.range, startMs, it.durationMs)) }

    fun setEnd(endMs: Long) = _state.update { it.copy(range = TrimMath.withEnd(it.range, endMs, it.durationMs)) }

    fun setSpeed(speed: Float) {
        if (speed in TrimMath.SupportedSpeeds) {
            _state.update { it.copy(speed = speed) }
        }
    }

    fun toggleMute() = _state.update { it.copy(isMuted = !it.isMuted) }

    fun rotateClockwise() = _state.update { it.copy(rotationDegrees = (it.rotationDegrees + 90) % 360) }

    fun save() {
        val current = _state.value
        if (!current.canSave) return
        exportJob = viewModelScope.launch {
            _state.update { it.copy(export = TrimExport.Running(0)) }
            trimVideo(
                uri = uri,
                range = current.range,
                speed = current.speed,
                muteAudio = current.isMuted,
                rotationDegrees = current.rotationDegrees,
            ).collect { event ->
                _state.update {
                    it.copy(
                        export = when (event) {
                            is TrimEvent.Progress -> TrimExport.Running(event.percent)
                            is TrimEvent.Done -> TrimExport.Done(event.name)
                            is TrimEvent.Failed -> TrimExport.Failed(event.reason)
                        },
                    )
                }
            }
        }
    }

    /** Stops the export; the copy that was being made is removed. */
    fun cancel() {
        exportJob?.cancel()
        exportJob = null
        _state.update { it.copy(export = TrimExport.Idle) }
    }

    /** The person has seen the failure message. */
    fun dismissFailure() = _state.update { if (it.export is TrimExport.Failed) it.copy(export = TrimExport.Idle) else it }

    companion object {
        const val URI_ARG = "uri"
    }
}
