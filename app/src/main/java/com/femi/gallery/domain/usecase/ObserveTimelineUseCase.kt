package com.femi.gallery.domain.usecase

import com.femi.gallery.domain.model.TimelineSection
import com.femi.gallery.domain.repository.MediaRepository
import com.femi.gallery.domain.search.SearchCriteria
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveTimelineUseCase @Inject constructor(
    private val repository: MediaRepository,
) {
    operator fun invoke(bucketId: Long? = null, search: SearchCriteria? = null): Flow<List<TimelineSection>> =
        repository.observeTimeline(bucketId, search)
}
