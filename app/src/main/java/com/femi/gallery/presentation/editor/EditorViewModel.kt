package com.femi.gallery.presentation.editor

import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.femi.gallery.R
import com.femi.gallery.domain.edit.CropCorner
import com.femi.gallery.domain.edit.CropMath
import com.femi.gallery.domain.edit.CropRect
import com.femi.gallery.domain.edit.EditFilter
import com.femi.gallery.domain.edit.EditState
import com.femi.gallery.domain.usecase.ApplyEditEffectsUseCase
import com.femi.gallery.domain.usecase.ApplyEditGeometryUseCase
import com.femi.gallery.domain.usecase.LoadEditPreviewUseCase
import com.femi.gallery.domain.usecase.SaveEditedCopyUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class EditorTool { Crop, Adjust, Filters }

/** Shapes the crop box can be locked to. */
enum class CropAspect(@StringRes val label: Int, private val fixedRatio: Float?) {
    Free(R.string.crop_free, null),
    Original(R.string.crop_original, null),
    Square(R.string.crop_square, 1f),
    Ratio4x3(R.string.crop_4_3, 4f / 3f),
    Ratio3x4(R.string.crop_3_4, 3f / 4f),
    Ratio16x9(R.string.crop_16_9, 16f / 9f),
    Ratio9x16(R.string.crop_9_16, 9f / 16f);

    /** Width / height the crop must have, or null when it can be any shape. */
    fun ratio(imageWidth: Int, imageHeight: Int): Float? = when (this) {
        Free -> null
        Original -> imageWidth.toFloat() / imageHeight
        else -> fixedRatio
    }
}

data class EditorUiState(
    /** The photo with rotation and flips applied; the crop tool shows this one. */
    val oriented: Bitmap? = null,
    /** [oriented] with the crop applied; the other tools show this one. */
    val cropped: Bitmap? = null,
    /** [cropped] with sharpen and vignette applied; null when neither is used. */
    val effects: Bitmap? = null,
    val edit: EditState = EditState(),
    val tool: EditorTool = EditorTool.Adjust,
    val aspect: CropAspect = CropAspect.Free,
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val saving: Boolean = false,
)

sealed interface EditorEvent {
    data object Saved : EditorEvent
    data object SaveFailed : EditorEvent
}

@HiltViewModel
class EditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val loadPreview: LoadEditPreviewUseCase,
    private val applyGeometry: ApplyEditGeometryUseCase,
    private val applyEffects: ApplyEditEffectsUseCase,
    private val saveCopy: SaveEditedCopyUseCase,
) : ViewModel() {

    private val uri: String = checkNotNull(savedStateHandle.get<String>(URI_ARG))

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private val _events = Channel<EditorEvent>(Channel.BUFFERED)
    val events: Flow<EditorEvent> = _events.receiveAsFlow()

    private var source: Bitmap? = null
    private var geometryJob: Job? = null
    private var effectsJob: Job? = null

    init {
        viewModelScope.launch {
            val bitmap = loadPreview(uri, PREVIEW_SIZE)
            if (bitmap == null) {
                _state.update { it.copy(loading = false, loadFailed = true) }
            } else {
                source = bitmap
                _state.update { it.copy(oriented = bitmap, cropped = bitmap, loading = false) }
            }
        }
    }

    fun selectTool(tool: EditorTool) {
        val leavingCrop = _state.value.tool == EditorTool.Crop && tool != EditorTool.Crop
        _state.update { it.copy(tool = tool) }
        if (leavingCrop) refreshGeometry()
    }

    // ---- geometry -------------------------------------------------------------------------

    fun rotate(clockwise: Boolean) = changeGeometry {
        it.copy(rotationTurns = (it.rotationTurns + if (clockwise) 1 else 3) % 4)
    }

    fun flipHorizontal() = changeGeometry { it.copy(flipHorizontal = !it.flipHorizontal) }

    /** Turning the picture trims its corners, so the crop box starts over (as it does for rotate and flip). */
    fun setStraighten(angle: Float) = changeGeometry { it.copy(straightenAngle = angle) }

    private fun changeGeometry(change: (EditState) -> EditState) {
        _state.update { it.copy(edit = change(it.edit).copy(crop = CropRect.Full), aspect = CropAspect.Free) }
        refreshGeometry()
    }

    private fun refreshGeometry() {
        val photo = source ?: return
        geometryJob?.cancel()
        geometryJob = viewModelScope.launch {
            val edit = _state.value.edit
            val oriented = applyGeometry(photo, edit, includeCrop = false)
            val cropped = applyGeometry(photo, edit, includeCrop = true)
            _state.update { it.copy(oriented = oriented, cropped = cropped, effects = null) }
            refreshEffects()
        }
    }

    // ---- crop -----------------------------------------------------------------------------

    fun selectAspect(aspect: CropAspect) {
        val oriented = _state.value.oriented ?: return
        val ratio = aspect.ratio(oriented.width, oriented.height)
        _state.update {
            val crop = if (ratio == null) it.edit.crop else CropMath.fitAspect(oriented.width, oriented.height, ratio)
            it.copy(aspect = aspect, edit = it.edit.copy(crop = crop))
        }
    }

    fun dragCropCorner(corner: CropCorner, dx: Float, dy: Float) {
        val oriented = _state.value.oriented ?: return
        _state.update {
            val ratio = it.aspect.ratio(oriented.width, oriented.height)
            val crop = CropMath.dragCorner(it.edit.crop, corner, dx, dy, oriented.width, oriented.height, ratio)
            it.copy(edit = it.edit.copy(crop = crop))
        }
    }

    fun moveCrop(dx: Float, dy: Float) {
        _state.update { it.copy(edit = it.edit.copy(crop = CropMath.move(it.edit.crop, dx, dy))) }
    }

    // ---- adjustments and filters ----------------------------------------------------------

    fun setBrightness(value: Float) = updateEdit { it.copy(brightness = value) }

    fun setContrast(value: Float) = updateEdit { it.copy(contrast = value) }

    fun setSaturation(value: Float) = updateEdit { it.copy(saturation = value) }

    fun setWarmth(value: Float) = updateEdit { it.copy(warmth = value) }

    fun setSharpen(value: Float) {
        updateEdit { it.copy(sharpen = value) }
        refreshEffects()
    }

    fun setVignette(value: Float) {
        updateEdit { it.copy(vignette = value) }
        refreshEffects()
    }

    fun setFilter(filter: EditFilter) = updateEdit { it.copy(filter = filter) }

    /** Renders sharpen and vignette onto the cropped preview; a newer slider position replaces an older one. */
    private fun refreshEffects() {
        val base = _state.value.cropped ?: return
        val edit = _state.value.edit
        effectsJob?.cancel()
        if (edit.sharpen <= 0f && edit.vignette <= 0f) {
            _state.update { it.copy(effects = null) }
            return
        }
        effectsJob = viewModelScope.launch {
            val rendered = applyEffects(base, edit)
            _state.update { if (it.cropped === base) it.copy(effects = rendered) else it }
        }
    }

    private fun updateEdit(change: (EditState) -> EditState) {
        _state.update { it.copy(edit = change(it.edit)) }
    }

    fun reset() {
        _state.update { it.copy(edit = EditState(), aspect = CropAspect.Free) }
        refreshGeometry()
    }

    // ---- saving ---------------------------------------------------------------------------

    fun save() {
        val current = _state.value
        if (current.saving || current.edit.isUnchanged) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val saved = saveCopy(uri, current.edit)
            _state.update { it.copy(saving = false) }
            _events.send(if (saved) EditorEvent.Saved else EditorEvent.SaveFailed)
        }
    }

    companion object {
        const val URI_ARG = "uri"

        /** Longest side of the on-screen preview; the saved photo is rendered from the original. */
        private const val PREVIEW_SIZE = 2048
    }
}
