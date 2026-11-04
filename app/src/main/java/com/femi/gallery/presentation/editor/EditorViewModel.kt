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
import com.femi.gallery.domain.edit.MarkupItem
import com.femi.gallery.domain.edit.MarkupMath
import com.femi.gallery.domain.edit.MarkupPalette
import com.femi.gallery.domain.edit.MarkupPoint
import com.femi.gallery.domain.edit.MarkupSticker
import com.femi.gallery.domain.edit.MarkupStroke
import com.femi.gallery.domain.edit.MarkupText
import com.femi.gallery.domain.usecase.ApplyEditEffectsUseCase
import com.femi.gallery.domain.usecase.ApplyEditGeometryUseCase
import com.femi.gallery.domain.usecase.LoadEditPreviewUseCase
import com.femi.gallery.domain.usecase.ReplaceOriginalUseCase
import com.femi.gallery.domain.usecase.RequestWriteOriginalUseCase
import com.femi.gallery.domain.usecase.SaveEditedCopyUseCase
import com.femi.gallery.presentation.selection.WriteBatch
import com.femi.gallery.presentation.selection.WriteKind
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

enum class EditorTool { Crop, Adjust, Filters, Markup }

/** What a touch on the photo does while the markup tool is open. */
enum class MarkupMode { Draw, Text, Sticker }

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
    val markupMode: MarkupMode = MarkupMode.Draw,
    val penColor: Int = MarkupPalette.Red,
    val penWidth: Float = MarkupMath.DEFAULT_PEN_WIDTH,
    val highlighter: Boolean = false,
    /** The text or sticker that moves, turns and scales with the fingers; null when none is chosen. */
    val selectedMarkupId: Long? = null,
    val canUndoMarkup: Boolean = false,
    /** Cropping, turning or straightening would remove the markup: the person is asked first. */
    val confirmClearMarkup: Boolean = false,
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
    private val replaceOriginal: ReplaceOriginalUseCase,
    private val requestWrite: RequestWriteOriginalUseCase,
) : ViewModel() {

    private val uri: String = checkNotNull(savedStateHandle.get<String>(URI_ARG))

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private val _events = Channel<EditorEvent>(Channel.BUFFERED)
    val events: Flow<EditorEvent> = _events.receiveAsFlow()

    private val _batches = Channel<WriteBatch>(Channel.BUFFERED)
    val batches: Flow<WriteBatch> = _batches.receiveAsFlow()

    private var source: Bitmap? = null
    private var geometryJob: Job? = null
    private var effectsJob: Job? = null

    /** What the markup looked like before each change, newest last, for Undo. */
    private val markupHistory = ArrayDeque<List<MarkupItem>>()

    /** What to do once the person has agreed that the markup is removed. */
    private var afterClearingMarkup: (() -> Unit)? = null

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
        if (tool == EditorTool.Crop && _state.value.tool != EditorTool.Crop) {
            guardMarkup { selectToolNow(tool) }
        } else {
            selectToolNow(tool)
        }
    }

    private fun selectToolNow(tool: EditorTool) {
        val leavingCrop = _state.value.tool == EditorTool.Crop && tool != EditorTool.Crop
        _state.update { it.copy(tool = tool, selectedMarkupId = null) }
        if (leavingCrop) refreshGeometry()
    }

    // ---- geometry -------------------------------------------------------------------------

    fun rotate(clockwise: Boolean) = changeGeometry {
        it.copy(rotationTurns = (it.rotationTurns + if (clockwise) 1 else 3) % 4)
    }

    fun flipHorizontal() = changeGeometry { it.copy(flipHorizontal = !it.flipHorizontal) }

    /** Turning the picture trims its corners, so the crop box starts over (as it does for rotate and flip). */
    fun setStraighten(angle: Float) = changeGeometry { it.copy(straightenAngle = angle) }

    private fun changeGeometry(change: (EditState) -> EditState) = guardMarkup { changeGeometryNow(change) }

    private fun changeGeometryNow(change: (EditState) -> EditState) {
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
        markupHistory.clear()
        _state.update { it.copy(edit = EditState(), aspect = CropAspect.Free, selectedMarkupId = null, canUndoMarkup = false) }
        refreshGeometry()
    }

    // ---- markup: drawings, text and stickers ----------------------------------------------

    /**
     * Markup is placed on the finished photo, so cropping, turning or straightening afterwards would put it in the
     * wrong place: the person is asked, and [action] runs once they agree and the markup is gone.
     */
    private fun guardMarkup(action: () -> Unit) {
        if (_state.value.edit.markup.isEmpty()) {
            action()
        } else {
            afterClearingMarkup = action
            _state.update { it.copy(confirmClearMarkup = true) }
        }
    }

    fun confirmClearingMarkup() {
        val action = afterClearingMarkup
        afterClearingMarkup = null
        markupHistory.clear()
        _state.update { it.copy(edit = it.edit.copy(markup = emptyList()), confirmClearMarkup = false, selectedMarkupId = null, canUndoMarkup = false) }
        action?.invoke()
    }

    fun cancelClearingMarkup() {
        afterClearingMarkup = null
        _state.update { it.copy(confirmClearMarkup = false) }
    }

    fun setMarkupMode(mode: MarkupMode) = _state.update { it.copy(markupMode = mode, selectedMarkupId = null) }

    fun setPenColor(color: Int) {
        val selected = _state.value.selectedMarkupId
        val target = _state.value.edit.markup.firstOrNull { it.id == selected }
        if (target is MarkupText) {
            pushMarkupHistory()
            replaceMarkup(target.copy(colorArgb = color))
        }
        _state.update { it.copy(penColor = color) }
    }

    fun setPenWidth(fraction: Float) = _state.update { it.copy(penWidth = fraction) }

    fun setHighlighter(on: Boolean) = _state.update { it.copy(highlighter = on) }

    /** A finger-drawn line of [points] (fractions of the photo) is finished. */
    fun commitStroke(points: List<MarkupPoint>, photoWidth: Float, photoHeight: Float) {
        if (points.isEmpty()) return
        val current = _state.value
        val simplified = MarkupMath.simplify(points, STROKE_MIN_DISTANCE, photoWidth, photoHeight)
        pushMarkupHistory()
        val stroke = MarkupStroke(
            id = MarkupMath.nextId(current.edit.markup),
            points = simplified,
            colorArgb = current.penColor,
            widthFraction = current.penWidth,
            highlighter = current.highlighter,
        )
        setMarkup(current.edit.markup + stroke)
    }

    fun addText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val current = _state.value
        pushMarkupHistory()
        val item = MarkupText(
            id = MarkupMath.nextId(current.edit.markup),
            text = trimmed,
            center = MarkupPoint(0.5f, 0.5f),
            sizeFraction = MarkupMath.DEFAULT_TEXT_SIZE,
            colorArgb = current.penColor,
        )
        setMarkup(current.edit.markup + item)
        _state.update { it.copy(selectedMarkupId = item.id) }
    }

    /** Changes the words of the text [id]; an empty text removes it. */
    fun editText(id: Long, text: String) {
        val item = _state.value.edit.markup.firstOrNull { it.id == id } as? MarkupText ?: return
        pushMarkupHistory()
        val trimmed = text.trim()
        if (trimmed.isEmpty()) deleteMarkup(id, remember = false) else replaceMarkup(item.copy(text = trimmed))
    }

    fun addSticker(emoji: String) {
        val current = _state.value
        pushMarkupHistory()
        val item = MarkupSticker(
            id = MarkupMath.nextId(current.edit.markup),
            emoji = emoji,
            center = MarkupPoint(0.5f, 0.5f),
            sizeFraction = MarkupMath.DEFAULT_STICKER_SIZE,
        )
        setMarkup(current.edit.markup + item)
        _state.update { it.copy(selectedMarkupId = item.id) }
    }

    fun selectMarkup(id: Long?) = _state.update { it.copy(selectedMarkupId = id) }

    /** A finger goes down on the selected item: what follows is one change for Undo. */
    fun beginMarkupGesture() = pushMarkupHistory()

    /** Moves, scales and turns the item [id] (fractions of the photo, a factor, degrees). */
    fun transformItem(id: Long, dx: Float, dy: Float, zoom: Float, rotationDegrees: Float) {
        val item = _state.value.edit.markup.firstOrNull { it.id == id } ?: return
        replaceMarkup(MarkupMath.transform(item, dx, dy, zoom, rotationDegrees))
    }

    /** Sets the size of the selected text or sticker from the slider. */
    fun setSelectedSize(fraction: Float) {
        val current = _state.value
        val size = fraction.coerceIn(MarkupMath.MIN_SIZE, MarkupMath.MAX_SIZE)
        when (val item = current.edit.markup.firstOrNull { it.id == current.selectedMarkupId }) {
            is MarkupText -> replaceMarkup(item.copy(sizeFraction = size))
            is MarkupSticker -> replaceMarkup(item.copy(sizeFraction = size))
            else -> Unit
        }
    }

    fun deleteSelected() {
        _state.value.selectedMarkupId?.let { deleteMarkup(it, remember = true) }
    }

    private fun deleteMarkup(id: Long, remember: Boolean) {
        if (remember) pushMarkupHistory()
        setMarkup(_state.value.edit.markup.filterNot { it.id == id })
        _state.update { if (it.selectedMarkupId == id) it.copy(selectedMarkupId = null) else it }
    }

    fun undoMarkup() {
        val previous = markupHistory.removeLastOrNull() ?: return
        _state.update { it.copy(edit = it.edit.copy(markup = previous), canUndoMarkup = markupHistory.isNotEmpty()) }
        _state.update { current -> if (previous.none { it.id == current.selectedMarkupId }) current.copy(selectedMarkupId = null) else current }
    }

    private fun pushMarkupHistory() {
        markupHistory.addLast(_state.value.edit.markup)
        while (markupHistory.size > MARKUP_HISTORY) markupHistory.removeFirst()
        _state.update { it.copy(canUndoMarkup = true) }
    }

    private fun setMarkup(items: List<MarkupItem>) = _state.update { it.copy(edit = it.edit.copy(markup = items)) }

    private fun replaceMarkup(item: MarkupItem) =
        setMarkup(_state.value.edit.markup.map { if (it.id == item.id) item else it })

    // ---- saving ---------------------------------------------------------------------------

    fun saveAsCopy() {
        val current = _state.value
        if (current.saving || current.edit.isUnchanged) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val saved = saveCopy(uri, current.edit)
            _state.update { it.copy(saving = false) }
            _events.send(if (saved) EditorEvent.Saved else EditorEvent.SaveFailed)
        }
    }

    fun saveReplacingOriginal() {
        val current = _state.value
        if (current.saving || current.edit.isUnchanged) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val requests = requestWrite(uri)
                if (requests.isEmpty()) {
                    performReplace()
                } else {
                    _batches.send(
                        WriteBatch(
                            kind = WriteKind.Move, // Using Move just as a generic "requires permission" kind, or we could use a custom one if Edit existed, but Move shows no toast for success message.
                            itemCount = 1,
                            requests = requests,
                            onDeclined = { _state.update { it.copy(saving = false) } },
                            onApproved = { performReplace() }
                        )
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(saving = false) }
                _events.send(EditorEvent.SaveFailed)
            }
        }
    }

    private fun performReplace() {
        val current = _state.value
        viewModelScope.launch {
            val saved = replaceOriginal(uri, current.edit)
            _state.update { it.copy(saving = false) }
            _events.send(if (saved) EditorEvent.Saved else EditorEvent.SaveFailed)
        }
    }

    companion object {
        const val URI_ARG = "uri"

        /** Longest side of the on-screen preview; the saved photo is rendered from the original. */
        private const val PREVIEW_SIZE = 2048

        /** Points of a drawn line closer together than this (a fraction of the shorter side) are dropped. */
        private const val STROKE_MIN_DISTANCE = 0.004f
        private const val MARKUP_HISTORY = 50
    }
}
