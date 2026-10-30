package com.femi.gallery.presentation.albums

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.femi.gallery.core.security.LockSession
import com.femi.gallery.domain.model.MediaItem
import com.femi.gallery.domain.model.SpecialAlbums
import com.femi.gallery.domain.usecase.GetAllMediaRefsUseCase
import com.femi.gallery.domain.usecase.GetAlbumNameUseCase
import com.femi.gallery.domain.usecase.GetPagedMediaUseCase
import com.femi.gallery.domain.usecase.ObserveAlbumsUseCase
import com.femi.gallery.domain.usecase.ObserveTimelineUseCase
import com.femi.gallery.domain.usecase.RefreshMediaUseCase
import com.femi.gallery.domain.usecase.RequestDeleteUseCase
import com.femi.gallery.domain.usecase.RequestFavoriteUseCase
import com.femi.gallery.domain.usecase.RequestTrashUseCase
import com.femi.gallery.presentation.pictures.TimelineZoomState
import com.femi.gallery.presentation.selection.MediaSelectionControllerFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One album's photos, grouped and zoomable just like the Pictures tab. */
@HiltViewModel
class AlbumViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    getPagedMedia: GetPagedMediaUseCase,
    observeTimeline: ObserveTimelineUseCase,
    getAlbumName: GetAlbumNameUseCase,
    selectionFactory: MediaSelectionControllerFactory,
    val lockSession: LockSession,
    private val refreshMedia: RefreshMediaUseCase,
) : ViewModel() {

    val bucketId: Long = checkNotNull(savedStateHandle.get<Long>(BUCKET_ARG))
    val isRecycleBin: Boolean = bucketId == SpecialAlbums.TRASH
    val isFavorites: Boolean = bucketId == SpecialAlbums.FAVORITES
    val isLocked: Boolean = bucketId == SpecialAlbums.LOCKED

    val media: Flow<PagingData<MediaItem>> =
        getPagedMedia(bucketId = bucketId).cachedIn(viewModelScope)

    val timeline = TimelineZoomState(viewModelScope, observeTimeline(bucketId), context)

    /** Folder name, looked up once when the album is opened. */
    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    init {
        viewModelScope.launch {
            _title.value = getAlbumName(bucketId)
        }
    }

    val selection = selectionFactory.create(
        scope = viewModelScope,
        bucketId = bucketId,
    )

    fun onResumed() = refreshMedia()

    companion object {
        const val BUCKET_ARG = "bucketId"
    }
}
