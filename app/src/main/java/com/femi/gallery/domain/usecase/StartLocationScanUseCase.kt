package com.femi.gallery.domain.usecase

import com.femi.gallery.domain.repository.LocationRepository
import javax.inject.Inject

/** Reads where new photos were taken, in the background. Cheap to call whenever the app opens. */
class StartLocationScanUseCase @Inject constructor(
    private val repository: LocationRepository,
) {
    operator fun invoke() = repository.startScan()
}
