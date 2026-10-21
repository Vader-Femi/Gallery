package com.femi.gallery.domain.usecase

import com.femi.gallery.domain.geo.LocationScanState
import com.femi.gallery.domain.repository.LocationRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class ObserveLocationScanUseCase @Inject constructor(
    private val repository: LocationRepository,
) {
    operator fun invoke(): StateFlow<LocationScanState> = repository.scan
}
