package com.femi.gallery.presentation.viewer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.femi.gallery.core.security.LockSession
import com.femi.gallery.domain.model.MediaDetails
import com.femi.gallery.domain.model.MediaItem
import com.femi.gallery.domain.model.MediaPositions
import com.femi.gallery.domain.model.SpecialAlbums
import com.femi.gallery.domain.search.SearchCriteria
import com.femi.gallery.domain.search.SearchQueryParser
import com.femi.gallery.domain.usecase.GetAllMediaRefsUseCase
import com.femi.gallery.domain.usecase.GetMediaDetailsUseCase
import com.femi.gallery.domain.usecase.GetPagedMediaUseCase
import com.femi.gallery.domain.usecase.RequestDeleteUseCase
import com.femi.gallery.domain.usecase.RequestFavoriteUseCase
import com.femi.gallery.domain.usecase.RequestTrashUseCase
import com.femi.gallery.presentation.selection.MediaSelectionControllerFactory
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
    selectionFactory: MediaSelectionControllerFactory,
    val lockSession: LockSession,
) : ViewModel() {

    /** Position of the tapped item in the full, newest-first media list, as the grid had it. */
    val startIndex: Int = savedStateHandle.get<Int>(INDEX_ARG) ?: 0

    /**
     * The tapped photo's id. This list is loaded separately from the grid's, so [startIndex] alone can
     * point at a different photo; the id lets the screen open on the right one.
     */
    val mediaId: Long = savedStateHandle.get<Long>(MEDIA_ID_ARG) ?: MediaPositions.NO_ID

    val isRecycleBin: Boolean get() = bucketId == SpecialAlbums.TRASH
    val isLocked: Boolean get() = bucketId == SpecialAlbums.LOCKED

    /** Set when opened from an album, so swiping stays inside that album. */
    private val bucketId: Long? =
        savedStateHandle.get<Long>(BUCKET_ARG)?.takeIf { it != NO_BUCKET }

    /** Set when opened from search results, so swiping stays inside those results. */
    private val search: SearchCriteria? = savedStateHandle.get<String>(QUERY_ARG)
        ?.let { SearchQueryParser.parse(it) }
        ?.takeUnless { it.isEmpty }

    // Start loading around the tapped item so the pager can open on it immediately.
    val media: Flow<PagingData<MediaItem>> =
        getPagedMedia(
            initialKey = (startIndex - PRELOAD_BEFORE).coerceAtLeast(0),
            bucketId = bucketId,
            search = search,
        ).cachedIn(viewModelScope)

    /** Favorite / trash for the photo on screen (the viewer has no multi-selection). */
    val actions = selectionFactory.create(
        scope = viewModelScope,
        bucketId = bucketId,
    )

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
        const val MEDIA_ID_ARG = "mediaId"
        const val BUCKET_ARG = "bucketId"
        const val QUERY_ARG = "query"
        private const val NO_BUCKET = Long.MIN_VALUE // keep in sync with GalleryRoutes.NO_BUCKET
        private const val PRELOAD_BEFORE = 120
    }
}
