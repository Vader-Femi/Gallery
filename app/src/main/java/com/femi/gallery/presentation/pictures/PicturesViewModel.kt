package com.femi.gallery.presentation.pictures

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.femi.gallery.domain.model.MediaItem
import com.femi.gallery.domain.model.TimelineSection
import com.femi.gallery.domain.usecase.GetAllMediaRefsUseCase
import com.femi.gallery.domain.usecase.GetPagedMediaUseCase
import com.femi.gallery.domain.usecase.ObserveTimelineUseCase
import com.femi.gallery.domain.usecase.RefreshMediaUseCase
import com.femi.gallery.domain.usecase.RequestDeleteUseCase
import com.femi.gallery.domain.usecase.RequestFavoriteUseCase
import com.femi.gallery.domain.usecase.RequestTrashUseCase
import com.femi.gallery.presentation.selection.MediaSelectionController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class PicturesViewModel @Inject constructor(
    getPagedMedia: GetPagedMediaUseCase,
    observeTimeline: ObserveTimelineUseCase,
    getAllRefs: GetAllMediaRefsUseCase,
    requestTrash: RequestTrashUseCase,
    requestFavorite: RequestFavoriteUseCase,
    requestDelete: RequestDeleteUseCase,
    private val refreshMedia: RefreshMediaUseCase,
) : ViewModel() {

    val media: Flow<PagingData<MediaItem>> = getPagedMedia().cachedIn(viewModelScope)

    val sections: StateFlow<List<TimelineSection>> = observeTimeline()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val selection = MediaSelectionController(
        scope = viewModelScope,
        bucketId = null,
        getAllRefs = getAllRefs,
        requestTrash = requestTrash,
        requestFavorite = requestFavorite,
        requestDelete = requestDelete,
    )

    private val _columns = MutableStateFlow(DEFAULT_COLUMNS)
    val columns: StateFlow<Int> = _columns.asStateFlow()

    /** Pinch out: bigger thumbnails, fewer columns. */
    fun zoomIn() = _columns.update { (it - 1).coerceAtLeast(MIN_COLUMNS) }

    /** Pinch in: smaller thumbnails, more columns. */
    fun zoomOut() = _columns.update { (it + 1).coerceAtMost(MAX_COLUMNS) }

    fun onResumed() = refreshMedia()

    private companion object {
        const val MIN_COLUMNS = 2
        const val DEFAULT_COLUMNS = 3
        const val MAX_COLUMNS = 6
    }
}
