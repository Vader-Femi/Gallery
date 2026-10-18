package com.femi.gallery.domain.usecase

import com.femi.gallery.domain.model.Story
import com.femi.gallery.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveStoriesUseCase @Inject constructor(
    private val repository: MediaRepository,
) {
    operator fun invoke(): Flow<List<Story>> = repository.observeStories()
}
