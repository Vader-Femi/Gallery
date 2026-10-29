package com.femi.gallery.presentation.duplicates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.femi.gallery.domain.duplicates.DuplicateGroup
import com.femi.gallery.domain.duplicates.DuplicateProgress
import com.femi.gallery.domain.model.toRef
import com.femi.gallery.domain.repository.DuplicateScanState
import com.femi.gallery.domain.usecase.ScanDuplicatesUseCase
import com.femi.gallery.presentation.selection.MediaSelectionControllerFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the duplicates screen shows. */
data class DuplicatesUiState(
    val scanning: Boolean = true,
    /** Null while the list of files is still being read. */
    val progress: DuplicateProgress? = null,
    val groups: List<DuplicateGroup> = emptyList(),
) {
    /** How many extra copies would be removed. */
    val copiesToRemove: Int get() = groups.sumOf { it.extraCopies.size }

    /** How much space removing them frees. */
    val bytesToFree: Long get() = groups.sumOf { it.reclaimableBytes }
}

/**
 * Searches the library for identical files and lets the person choose which copy of each to keep. The
 * other copies go to the recycle bin, through the same system confirmation as any other trash action.
 */
@HiltViewModel
class DuplicatesViewModel @Inject constructor(
    private val scanDuplicates: ScanDuplicatesUseCase,
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
            scanDuplicates().collect { scanState ->
                when (scanState) {
                    DuplicateScanState.Listing -> Unit
                    is DuplicateScanState.Comparing -> _state.update { it.copy(progress = scanState.progress) }
                    is DuplicateScanState.Done -> _state.value = DuplicatesUiState(scanning = false, groups = scanState.groups)
                }
            }
        }
    }

    /** Keep the file [mediaId] of the group at [groupIndex] instead of the suggested one. */
    fun keep(groupIndex: Int, mediaId: Long) {
        _state.update { current ->
            current.copy(groups = current.groups.mapIndexed { index, group -> if (index == groupIndex) group.withKeep(mediaId) else group })
        }
    }

    /** Asks to move every extra copy to the recycle bin. */
    fun removeCopies() {
        val refs = _state.value.groups.flatMap { group -> group.extraCopies.map { it.toRef() } }
        if (refs.isNotEmpty()) selection.trash(refs)
    }

    /** The copies are gone: look again so the list is right. */
    fun onTrashed() = scan()
}
