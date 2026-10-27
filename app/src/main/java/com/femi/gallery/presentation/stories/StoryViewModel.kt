package com.femi.gallery.presentation.stories

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.femi.gallery.domain.model.MediaItem
import com.femi.gallery.domain.model.StoryFilter
import com.femi.gallery.domain.search.SearchQueryParser
import com.femi.gallery.domain.usecase.GetPagedMediaUseCase
import com.femi.gallery.domain.usecase.ObserveStoryEditUseCase
import com.femi.gallery.domain.usecase.ObserveTimelineUseCase
import com.femi.gallery.domain.usecase.RefreshMediaUseCase
import com.femi.gallery.domain.usecase.RenameStoryUseCase
import com.femi.gallery.domain.usecase.SetStoryCoverUseCase
import com.femi.gallery.presentation.pictures.TimelineZoomState
import com.femi.gallery.presentation.selection.MediaSelectionControllerFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * The photos and videos of one story: everything taken between its first and last day, optionally
 * only the photos or only the videos. Also holds the story's own title and cover.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getPagedMedia: GetPagedMediaUseCase,
    observeTimeline: ObserveTimelineUseCase,
    observeStoryEdit: ObserveStoryEditUseCase,
    selectionFactory: MediaSelectionControllerFactory,
    private val renameStory: RenameStoryUseCase,
    private val setStoryCover: SetStoryCoverUseCase,
    private val refreshMedia: RefreshMediaUseCase,
) : ViewModel() {

    val fromDay: Long = checkNotNull(savedStateHandle.get<Long>(FROM_ARG))
    val toDay: Long = checkNotNull(savedStateHandle.get<Long>(TO_ARG))

    private val from = LocalDate.ofEpochDay(fromDay)
    private val to = LocalDate.ofEpochDay(toDay)

    private val _filter = MutableStateFlow(StoryFilter.All)
    val filter: StateFlow<StoryFilter> = _filter.asStateFlow()

    /** Lets the viewer reopen exactly what the grid shows (the days and the chosen filter). */
    val query: String get() = _filter.value.query(from, to)

    /** Lets the slideshow play the whole story; it only ever shows photos. */
    val slideshowQuery: String = StoryFilter.All.query(from, to)

    val media: Flow<PagingData<MediaItem>> =
        _filter.flatMapLatest { getPagedMedia(search = it.criteria(from, to)) }.cachedIn(viewModelScope)

    val timeline = TimelineZoomState(viewModelScope, _filter.flatMapLatest { observeTimeline(search = it.criteria(from, to)) })

    /** Whether the story has any video; the filter is only offered then. */
    val hasVideos: StateFlow<Boolean> = observeTimeline(search = StoryFilter.Videos.criteria(from, to))
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    /** The title the person gave the story; null shows the dates. */
    val customTitle: StateFlow<String?> = observeStoryEdit(fromDay, toDay)
        .map { it?.title }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** Whether the person chose the cover (so it can be put back to the automatic one). */
    val hasCustomCover: StateFlow<Boolean> = observeStoryEdit(fromDay, toDay)
        .map { it?.coverId != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    val selection = selectionFactory.create(
        scope = viewModelScope,
        bucketId = null,
        currentSearch = { _filter.value.criteria(from, to) },
    )

    fun setFilter(filter: StoryFilter) {
        if (filter == _filter.value) return
        selection.clear()
        _filter.value = filter
    }

    /** Blank goes back to the dates. */
    fun rename(title: String?) {
        viewModelScope.launch { renameStory(fromDay, toDay, title) }
    }

    fun setCover(mediaId: Long) {
        viewModelScope.launch { setStoryCover(fromDay, toDay, mediaId) }
    }

    fun resetCover() {
        viewModelScope.launch { setStoryCover(fromDay, toDay, null) }
    }

    fun onResumed() = refreshMedia()

    companion object {
        const val FROM_ARG = "fromDay"
        const val TO_ARG = "toDay"
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
