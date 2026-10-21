package com.femi.gallery.presentation.place

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.femi.gallery.domain.model.MediaItem
import com.femi.gallery.domain.search.SearchQueryParser
import com.femi.gallery.domain.usecase.GetPagedMediaUseCase
import com.femi.gallery.domain.usecase.ObserveTimelineUseCase
import com.femi.gallery.domain.usecase.RefreshMediaUseCase
import com.femi.gallery.presentation.pictures.TimelineZoomState
import com.femi.gallery.presentation.selection.MediaSelectionControllerFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** The photos of one place on the map: everything taken inside the area the route names. */
@HiltViewModel
class PlaceViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getPagedMedia: GetPagedMediaUseCase,
    observeTimeline: ObserveTimelineUseCase,
    selectionFactory: MediaSelectionControllerFactory,
    private val refreshMedia: RefreshMediaUseCase,
) : ViewModel() {

    /** The area as a search query (`in:south,west,north,east`); lets the viewer and slideshow reopen exactly these photos. */
    val query: String = savedStateHandle.get<String>(QUERY_ARG).orEmpty()

    private val criteria = SearchQueryParser.parse(query)

    val media: Flow<PagingData<MediaItem>> = getPagedMedia(search = criteria).cachedIn(viewModelScope)

    val timeline = TimelineZoomState(viewModelScope, observeTimeline(search = criteria))

    val selection = selectionFactory.create(
        scope = viewModelScope,
        bucketId = null,
        currentSearch = { criteria },
    )

    fun onResumed() = refreshMedia()

    companion object {
        const val QUERY_ARG = "query"
    }
}
