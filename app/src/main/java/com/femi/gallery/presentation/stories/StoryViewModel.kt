package com.femi.gallery.presentation.stories

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
import java.time.LocalDate
import javax.inject.Inject

/** The photos of one story: everything taken between its first and last day. */
@HiltViewModel
class StoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getPagedMedia: GetPagedMediaUseCase,
    observeTimeline: ObserveTimelineUseCase,
    selectionFactory: MediaSelectionControllerFactory,
    private val refreshMedia: RefreshMediaUseCase,
) : ViewModel() {

    val fromDay: Long = checkNotNull(savedStateHandle.get<Long>(FROM_ARG))
    val toDay: Long = checkNotNull(savedStateHandle.get<Long>(TO_ARG))

    private val from = LocalDate.ofEpochDay(fromDay)
    private val to = LocalDate.ofEpochDay(toDay)
    private val criteria = SearchQueryParser.betweenDays(from, to)

    /** Lets the viewer and slideshow reopen exactly this story from a route argument. */
    val query: String = SearchQueryParser.betweenDaysQuery(from, to)

    val media: Flow<PagingData<MediaItem>> = getPagedMedia(search = criteria).cachedIn(viewModelScope)

    val timeline = TimelineZoomState(viewModelScope, observeTimeline(search = criteria))

    val selection = selectionFactory.create(
        scope = viewModelScope,
        bucketId = null,
        currentSearch = { criteria },
    )

    fun onResumed() = refreshMedia()

    companion object {
        const val FROM_ARG = "fromDay"
        const val TO_ARG = "toDay"
    }
}
