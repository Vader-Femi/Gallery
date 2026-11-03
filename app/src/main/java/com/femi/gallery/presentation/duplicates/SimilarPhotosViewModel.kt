package com.femi.gallery.presentation.duplicates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.femi.gallery.domain.duplicates.DuplicatePhase
import com.femi.gallery.domain.duplicates.DuplicateProgress
import com.femi.gallery.domain.model.toRef
import com.femi.gallery.domain.repository.SimilarScanState
import com.femi.gallery.domain.usecase.ScanSimilarPhotosUseCase
import com.femi.gallery.presentation.selection.MediaSelectionControllerFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Searches the whole library for photos that look alike (not only identical files) and lets the person choose which
 * photo of each group to keep. Unlike identical copies, similar photos can be different pictures worth keeping, so a
 * group can be left alone ("Keep all"), and the others go to the recycle bin only after the system confirmation.
 */
@HiltViewModel
class SimilarPhotosViewModel @Inject constructor(
    private val scanSimilar: ScanSimilarPhotosUseCase,
    selectionFactory: MediaSelectionControllerFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(DuplicatesUiState())
    val state: StateFlow<DuplicatesUiState> = _state.asStateFlow()

    /** Only its trash action and the confirmation dialogs are used here. */
    val selection = selectionFactory.create(viewModelScope, bucketId = null)

    private var scanJob: Job? = null

    init {
        scan()
    }

    fun scan() {
        scanJob?.cancel()
        _state.value = DuplicatesUiState()
        scanJob = viewModelScope.launch {
            scanSimilar().collect { scanState ->
                when (scanState) {
                    SimilarScanState.Listing -> Unit
                    is SimilarScanState.Hashing ->
                        _state.update { it.copy(progress = DuplicateProgress(DuplicatePhase.Hashing, scanState.done, scanState.total)) }
                    is SimilarScanState.Done -> _state.value = DuplicatesUiState(scanning = false, groups = scanState.groups)
                }
            }
        }
    }

    /** Keep the photo [mediaId] of the group at [groupIndex] instead of the suggested one. */
    fun keep(groupIndex: Int, mediaId: Long) {
        _state.update { current ->
            current.copy(groups = current.groups.mapIndexed { index, group -> if (index == groupIndex) group.withKeep(mediaId) else group })
        }
    }

    /** Leaves the group at [groupIndex] alone: none of its photos will be removed. */
    fun ignore(groupIndex: Int) {
        _state.update { current -> current.copy(groups = current.groups.filterIndexed { index, _ -> index != groupIndex }) }
    }

    /** Asks to move every photo that is not kept to the recycle bin. */
    fun removeExtras() {
        val refs = _state.value.groups.flatMap { group -> group.extraCopies.map { it.toRef() } }
        if (refs.isNotEmpty()) selection.trash(refs)
    }

    /** The photos are gone: look again so the list is right. */
    fun onTrashed() = scan()
}
