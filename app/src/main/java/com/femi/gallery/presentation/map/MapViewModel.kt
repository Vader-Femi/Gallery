package com.femi.gallery.presentation.map

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.femi.gallery.domain.geo.LocatedMedia
import com.femi.gallery.domain.geo.LocationScanState
import com.femi.gallery.domain.geo.MapData
import com.femi.gallery.domain.geo.MapViewport
import com.femi.gallery.domain.usecase.GetLocatedMediaUseCase
import com.femi.gallery.domain.usecase.GetMapDataUseCase
import com.femi.gallery.domain.usecase.ObserveLocationScanUseCase
import com.femi.gallery.domain.usecase.StartLocationScanUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State of the map: the photos that have a place, the land and cities to draw, how far the location
 * scan has got, and where the map is looking. The viewport lives here so that it is still where the
 * user left it after they come back from looking at photos.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class MapViewModel @Inject constructor(
    private val getLocatedMedia: GetLocatedMediaUseCase,
    observeScan: ObserveLocationScanUseCase,
    private val startScan: StartLocationScanUseCase,
    private val getMapData: GetMapDataUseCase,
) : ViewModel() {

    val scan: StateFlow<LocationScanState> = observeScan()

    private val _media = MutableStateFlow(LocatedMedia.Empty)
    val media: StateFlow<LocatedMedia> = _media.asStateFlow()

    /** True once the photos have been read at least once, so "no photos" is not shown too early. */
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _mapData = MutableStateFlow(MapData.Empty)
    val mapData: StateFlow<MapData> = _mapData.asStateFlow()

    /** Null until the first layout, when it is set to show all the photos. */
    private val _viewport = MutableStateFlow<MapViewport?>(null)
    val viewport: StateFlow<MapViewport?> = _viewport.asStateFlow()

    init {
        viewModelScope.launch { _mapData.value = getMapData() }
        viewModelScope.launch {
            reload()
            // While the scan runs, pick up the photos it has placed so far, without reloading on every batch.
            scan.map { it.placed }
                .distinctUntilChanged()
                .drop(1)
                .debounce(RELOAD_DELAY_MS)
                .collect { reload() }
        }
    }

    fun setViewport(viewport: MapViewport) {
        _viewport.value = viewport
    }

    /** Changes the viewport from its latest value, so quick gestures never work from a stale one. */
    fun updateViewport(transform: (MapViewport) -> MapViewport) {
        _viewport.update { current -> current?.let(transform) }
    }

    /** Reads where new photos were taken; call when the screen is shown and after a permission change. */
    fun scanLocations() = startScan()

    private suspend fun reload() {
        try {
            _media.value = getLocatedMedia()
        } catch (e: Exception) {
            Log.w(TAG, "Could not read the photos with a place", e)
        }
        _loaded.value = true
    }

    private companion object {
        const val TAG = "Map"
        const val RELOAD_DELAY_MS = 2_000L
    }
}
