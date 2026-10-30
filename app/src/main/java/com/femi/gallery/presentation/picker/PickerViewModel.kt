package com.femi.gallery.presentation.picker

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.femi.gallery.domain.model.MediaItem
import com.femi.gallery.domain.model.PickerFilter
import com.femi.gallery.domain.model.TimelineSection
import com.femi.gallery.domain.usecase.GetPagedMediaUseCase
import com.femi.gallery.domain.usecase.ObserveTimelineUseCase
import com.femi.gallery.presentation.pictures.TimelineZoomState
import com.femi.gallery.presentation.selection.SelectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

/** The library as seen by an app that asked the user to pick photos or videos. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PickerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    getPagedMedia: GetPagedMediaUseCase,
    observeTimeline: ObserveTimelineUseCase,
) : ViewModel() {

    private val filter = MutableStateFlow(PickerFilter.All)

    val media: Flow<PagingData<MediaItem>> = filter
        .flatMapLatest { getPagedMedia(search = it.toCriteria()) }
        .cachedIn(viewModelScope)

    val timeline = TimelineZoomState(
        scope = viewModelScope,
        daySections = filter.flatMapLatest { observeTimeline(search = it.toCriteria()) },
        context = context,
    )

    /** What the user has ticked when the other app allows several results. */
    val selection = SelectionManager()

    fun setFilter(value: PickerFilter) {
        filter.value = value
    }
}
