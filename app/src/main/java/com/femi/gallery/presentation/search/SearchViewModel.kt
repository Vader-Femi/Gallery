package com.femi.gallery.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.femi.gallery.domain.model.MediaItem
import com.femi.gallery.domain.model.TimelineSection
import com.femi.gallery.domain.search.SearchCriteria
import com.femi.gallery.domain.search.SearchQueryParser
import com.femi.gallery.domain.usecase.GetAllMediaRefsUseCase
import com.femi.gallery.domain.usecase.GetPagedMediaUseCase
import com.femi.gallery.domain.usecase.ObserveTimelineUseCase
import com.femi.gallery.domain.usecase.RefreshMediaUseCase
import com.femi.gallery.domain.usecase.RequestDeleteUseCase
import com.femi.gallery.domain.usecase.RequestFavoriteUseCase
import com.femi.gallery.domain.usecase.RequestTrashUseCase
import com.femi.gallery.presentation.pictures.TimelineZoomState
import com.femi.gallery.presentation.selection.MediaSelectionControllerFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    getPagedMedia: GetPagedMediaUseCase,
    observeTimeline: ObserveTimelineUseCase,
    selectionFactory: MediaSelectionControllerFactory,
    private val refreshMedia: RefreshMediaUseCase,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** What the typed text means; recomputed shortly after the user stops typing. */
    val criteria: StateFlow<SearchCriteria> = _query
        .debounce(TYPING_DEBOUNCE_MS)
        .map { SearchQueryParser.parse(it) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, SearchCriteria.None)

    val media: Flow<PagingData<MediaItem>> = criteria
        .flatMapLatest { c ->
            if (c.isEmpty) flowOf(PagingData.empty()) else getPagedMedia(search = c)
        }
        .cachedIn(viewModelScope)

    val timeline = TimelineZoomState(
        scope = viewModelScope,
        daySections = criteria.flatMapLatest { c ->
            if (c.isEmpty) flowOf(emptyList<TimelineSection>()) else observeTimeline(search = c)
        },
    )

    val selection = selectionFactory.create(
        scope = viewModelScope,
        bucketId = null,
        currentSearch = { criteria.value.takeUnless { it.isEmpty } },
    )

    fun onQueryChange(text: String) {
        _query.value = text
    }

    fun onResumed() = refreshMedia()

    private companion object {
        const val TYPING_DEBOUNCE_MS = 250L
    }
}
