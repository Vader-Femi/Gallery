package com.femi.gallery.domain.usecase

import com.femi.gallery.domain.model.StoryEdit
import com.femi.gallery.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveStoryEditUseCase @Inject constructor(
    private val repository: MediaRepository,
) {
    operator fun invoke(fromDay: Long, toDay: Long): Flow<StoryEdit?> = repository.observeStoryEdit(fromDay, toDay)
}

class RenameStoryUseCase @Inject constructor(
    private val repository: MediaRepository,
) {
    suspend operator fun invoke(fromDay: Long, toDay: Long, title: String?) = repository.renameStory(fromDay, toDay, title)
}

class SetStoryCoverUseCase @Inject constructor(
    private val repository: MediaRepository,
) {
    suspend operator fun invoke(fromDay: Long, toDay: Long, mediaId: Long?) = repository.setStoryCover(fromDay, toDay, mediaId)
}
