package com.femi.gallery.presentation.search

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.femi.gallery.domain.model.MediaItem
import com.femi.gallery.domain.model.TimelineSection
import com.femi.gallery.domain.search.SearchCriteria
import com.femi.gallery.domain.search.SearchQueryParser
import com.femi.gallery.domain.usecase.GetAllMediaRefsUseCase
import com.femi.gallery.domain.text.TextScanState
import com.femi.gallery.domain.usecase.GetPagedMediaUseCase
import com.femi.gallery.domain.usecase.ObserveTextScanUseCase
import com.femi.gallery.domain.usecase.ObserveTimelineUseCase
import com.femi.gallery.domain.usecase.RefreshMediaUseCase
import com.femi.gallery.domain.usecase.RequestDeleteUseCase
import com.femi.gallery.domain.usecase.RequestFavoriteUseCase
import com.femi.gallery.domain.usecase.RequestTrashUseCase
import com.femi.gallery.presentation.pictures.TimelineZoomState
import com.femi.gallery.presentation.selection.MediaSelectionControllerFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    getPagedMedia: GetPagedMediaUseCase,
    observeTimeline: ObserveTimelineUseCase,
    selectionFactory: MediaSelectionControllerFactory,
    private val refreshMedia: RefreshMediaUseCase,
    observeTextScan: ObserveTextScanUseCase,
    observeClipScan: com.femi.gallery.domain.usecase.ObserveClipScanUseCase,
    observeColorScan: com.femi.gallery.domain.usecase.ObserveColorScanUseCase,
    observeLabelScan: com.femi.gallery.domain.usecase.ObserveLabelScanUseCase,
    observeLocationScan: com.femi.gallery.domain.usecase.ObserveLocationScanUseCase,
) : ViewModel() {

    data class CombinedScanState(
        val isScanning: Boolean = false,
        val scanned: Int = 0,
        val total: Int = 0,
        val isUnavailable: Boolean = false,
    )

    /** How far the combined background indexing has got. */
    val combinedScan: StateFlow<CombinedScanState> = kotlinx.coroutines.flow.combine(
        observeTextScan(),
        observeClipScan(),
        observeColorScan(),
        observeLabelScan(),
        observeLocationScan()
    ) { text, clip, color, label, location ->
        val isScanning = text.status == com.femi.gallery.domain.text.TextScanStatus.Scanning ||
            clip.status == com.femi.gallery.domain.clip.ClipScanStatus.Scanning ||
            color.status == com.femi.gallery.domain.color.ColorScanStatus.Scanning ||
            label.status == com.femi.gallery.domain.label.LabelScanStatus.Scanning ||
            location.status == com.femi.gallery.domain.geo.LocationScanStatus.Scanning
            
        val isUnavailable = text.status == com.femi.gallery.domain.text.TextScanStatus.Unavailable

        val scanned = text.scanned + clip.scanned + color.scanned + label.scanned + location.scanned
        val total = text.total + clip.total + color.total + label.total + location.total

        CombinedScanState(isScanning, scanned, total, isUnavailable)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CombinedScanState())

    private val initialQuery: String = savedStateHandle.get<String>("query").orEmpty()
    private val _query = MutableStateFlow(initialQuery)
    val query: StateFlow<String> = _query.asStateFlow()

    /** What the typed text means; recomputed shortly after the user stops typing. */
    val criteria: StateFlow<SearchCriteria> = _query
        .map { it.trim() }
        .distinctUntilChanged()
        .flatMapLatest { text ->
            if (text.isEmpty()) {
                flowOf(SearchCriteria.None)
            } else {
                flowOf(text).debounce(TYPING_DEBOUNCE_MS.milliseconds).map { SearchQueryParser.parse(it) }
            }
        }
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
        context = context,
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
