package com.femi.gallery.presentation.videos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.femi.gallery.domain.model.MediaItem
import com.femi.gallery.domain.model.MediaType
import com.femi.gallery.domain.search.SearchCriteria
import com.femi.gallery.domain.usecase.GetPagedMediaUseCase
import com.femi.gallery.domain.usecase.ObserveTimelineUseCase
import com.femi.gallery.domain.usecase.RefreshMediaUseCase
import com.femi.gallery.presentation.pictures.TimelineZoomState
import com.femi.gallery.presentation.selection.MediaSelectionControllerFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class VideosViewModel @Inject constructor(
    getPagedMedia: GetPagedMediaUseCase,
    observeTimeline: ObserveTimelineUseCase,
    selectionFactory: MediaSelectionControllerFactory,
    private val refreshMedia: RefreshMediaUseCase,
) : ViewModel() {

    private val criteria = SearchCriteria(mediaType = MediaType.Video)

    val media: Flow<PagingData<MediaItem>> = getPagedMedia(search = criteria).cachedIn(viewModelScope)

    val timeline = TimelineZoomState(viewModelScope, observeTimeline(search = criteria))

    val selection = selectionFactory.create(
        scope = viewModelScope,
        bucketId = null,
        currentSearch = { criteria },
    )

    fun onResumed() = refreshMedia()
}
