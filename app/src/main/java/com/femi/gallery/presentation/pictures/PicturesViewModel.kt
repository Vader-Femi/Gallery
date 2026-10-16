package com.femi.gallery.presentation.pictures

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.femi.gallery.domain.model.MediaItem
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

    /** Pinch-to-zoom level and the day/month/year sections that go with it. */
    val timeline = TimelineZoomState(viewModelScope, observeTimeline())

    val selection = MediaSelectionController(
        scope = viewModelScope,
        bucketId = null,
        getAllRefs = getAllRefs,
        requestTrash = requestTrash,
        requestFavorite = requestFavorite,
        requestDelete = requestDelete,
    )

    fun onResumed() = refreshMedia()
}
