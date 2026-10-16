package com.femi.gallery.presentation.viewer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.femi.gallery.domain.model.MediaDetails
import com.femi.gallery.domain.model.MediaItem
import com.femi.gallery.domain.usecase.GetMediaDetailsUseCase
import com.femi.gallery.domain.usecase.GetPagedMediaUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getPagedMedia: GetPagedMediaUseCase,
    private val getMediaDetails: GetMediaDetailsUseCase,
) : ViewModel() {

    /** Position of the tapped item in the full, newest-first media list. */
    val startIndex: Int = savedStateHandle.get<Int>(INDEX_ARG) ?: 0

    // Start loading around the tapped item so the pager can open on it immediately.
    val media: Flow<PagingData<MediaItem>> =
        getPagedMedia(initialKey = (startIndex - PRELOAD_BEFORE).coerceAtLeast(0))
            .cachedIn(viewModelScope)

    private val _details = MutableStateFlow<MediaDetails?>(null)
    val details: StateFlow<MediaDetails?> = _details.asStateFlow()
    private var detailsJob: Job? = null

    fun showDetails(item: MediaItem) {
        detailsJob?.cancel()
        _details.value = MediaDetails(item) // file info is available instantly, EXIF follows
        detailsJob = viewModelScope.launch { _details.value = getMediaDetails(item) }
    }

    fun hideDetails() {
        detailsJob?.cancel()
        _details.value = null
    }

    companion object {
        const val INDEX_ARG = "index"
        private const val PRELOAD_BEFORE = 120
    }
}
