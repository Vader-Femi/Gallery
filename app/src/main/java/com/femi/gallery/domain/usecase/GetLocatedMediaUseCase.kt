package com.femi.gallery.domain.usecase

import com.femi.gallery.domain.geo.LocatedMedia
import com.femi.gallery.domain.repository.LocationRepository
import javax.inject.Inject

/** The photos that have a place, for the map. */
class GetLocatedMediaUseCase @Inject constructor(
    private val repository: LocationRepository,
) {
    suspend operator fun invoke(): LocatedMedia = repository.locatedMedia()
}
