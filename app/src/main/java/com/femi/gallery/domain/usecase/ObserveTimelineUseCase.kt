package com.femi.gallery.domain.usecase

import com.femi.gallery.domain.model.TimelineSection
import com.femi.gallery.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveTimelineUseCase @Inject constructor(
    private val repository: MediaRepository,
) {
    operator fun invoke(): Flow<List<TimelineSection>> = repository.observeTimeline()
}
