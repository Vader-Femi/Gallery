package com.femi.gallery.domain.usecase

import com.femi.gallery.domain.geo.MapData
import com.femi.gallery.domain.repository.MapDataRepository
import javax.inject.Inject

class GetMapDataUseCase @Inject constructor(
    private val repository: MapDataRepository,
) {
    suspend operator fun invoke(): MapData = repository.load()
}
