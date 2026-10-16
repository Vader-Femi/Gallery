package com.femi.gallery.presentation.albums

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.femi.gallery.domain.model.MediaItem
import com.femi.gallery.domain.model.SpecialAlbums
import com.femi.gallery.domain.model.TimelineSection
import com.femi.gallery.domain.usecase.GetAllMediaRefsUseCase
import com.femi.gallery.domain.usecase.GetPagedMediaUseCase
import com.femi.gallery.domain.usecase.ObserveAlbumsUseCase
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** One album's photos, grouped by day just like the Pictures tab. */
@HiltViewModel
class AlbumViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getPagedMedia: GetPagedMediaUseCase,
    observeTimeline: ObserveTimelineUseCase,
    observeAlbums: ObserveAlbumsUseCase,
    getAllRefs: GetAllMediaRefsUseCase,
    requestTrash: RequestTrashUseCase,
    requestFavorite: RequestFavoriteUseCase,
    requestDelete: RequestDeleteUseCase,
    private val refreshMedia: RefreshMediaUseCase,
) : ViewModel() {

    val bucketId: Long = checkNotNull(savedStateHandle.get<Long>(BUCKET_ARG))
    val isRecycleBin: Boolean = bucketId == SpecialAlbums.TRASH
    val isFavorites: Boolean = bucketId == SpecialAlbums.FAVORITES

    val media: Flow<PagingData<MediaItem>> =
        getPagedMedia(bucketId = bucketId).cachedIn(viewModelScope)

    val sections: StateFlow<List<TimelineSection>> = observeTimeline(bucketId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Folder name, looked up from the album list so it never needs to travel through navigation. */
    val title: StateFlow<String> = observeAlbums()
        .map { albums -> albums.firstOrNull { it.bucketId == bucketId }?.name.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val selection = MediaSelectionController(
        scope = viewModelScope,
        bucketId = bucketId,
        getAllRefs = getAllRefs,
        requestTrash = requestTrash,
        requestFavorite = requestFavorite,
        requestDelete = requestDelete,
    )

    private val _columns = MutableStateFlow(DEFAULT_COLUMNS)
    val columns: StateFlow<Int> = _columns.asStateFlow()

    fun zoomIn() = _columns.update { (it - 1).coerceAtLeast(MIN_COLUMNS) }

    fun zoomOut() = _columns.update { (it + 1).coerceAtMost(MAX_COLUMNS) }

    fun onResumed() = refreshMedia()

    companion object {
        const val BUCKET_ARG = "bucketId"
        private const val MIN_COLUMNS = 2
        private const val DEFAULT_COLUMNS = 3
        private const val MAX_COLUMNS = 6
    }
}
