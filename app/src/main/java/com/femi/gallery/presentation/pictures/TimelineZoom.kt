package com.femi.gallery.presentation.pictures

import android.content.Context
import com.femi.gallery.domain.model.TimelineGranularity
import com.femi.gallery.domain.model.TimelineGrouper
import com.femi.gallery.domain.model.TimelineSection
import com.femi.gallery.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * The pinch-to-zoom steps, from most to least detailed: larger day thumbnails, smaller day
 * thumbnails, then whole months, then whole years.
 */
enum class TimelineZoom(val granularity: TimelineGranularity, val columns: Int) {
    Day2(TimelineGranularity.Day, 2),
    Day3(TimelineGranularity.Day, 3),
    Day4(TimelineGranularity.Day, 4),
    Day5(TimelineGranularity.Day, 5),
    Month6(TimelineGranularity.Month, 6),
    Year7(TimelineGranularity.Year, 7);

    /** Pinch out: bigger thumbnails. */
    fun zoomedIn(): TimelineZoom = entries.getOrElse(ordinal - 1) { this }

    /** Pinch in: smaller thumbnails, coarser grouping. */
    fun zoomedOut(): TimelineZoom = entries.getOrElse(ordinal + 1) { this }

    companion object {
        val Default = Day3
    }
}

/** Current zoom level plus the sections regrouped to match it. Shared by Pictures and albums. */
class TimelineZoomState(
    private val scope: CoroutineScope,
    daySections: Flow<List<TimelineSection>>,
    private val settingsRepository: SettingsRepository,
) {
    private val _zoom = MutableStateFlow(settingsRepository.getTimelineZoomSync())
    val zoom: StateFlow<TimelineZoom> = _zoom.asStateFlow()

    init {
        scope.launch {
            settingsRepository.timelineZoom.collect {
                _zoom.value = it
            }
        }
    }

    val sections: StateFlow<List<TimelineSection>> =
        combine(daySections, _zoom) { days, zoom -> TimelineGrouper.regroup(days, zoom.granularity) }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun zoomIn() {
        _zoom.update { current ->
            val next = current.zoomedIn()
            scope.launch { settingsRepository.setTimelineZoom(next) }
            next
        }
    }

    fun zoomOut() {
        _zoom.update { current ->
            val next = current.zoomedOut()
            scope.launch { settingsRepository.setTimelineZoom(next) }
            next
        }
    }
}
