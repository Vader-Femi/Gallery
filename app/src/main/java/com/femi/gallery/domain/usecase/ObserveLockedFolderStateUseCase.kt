package com.femi.gallery.domain.usecase

import com.femi.gallery.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ObserveLockedFolderStateUseCase @Inject constructor(
    private val repository: MediaRepository,
) {
    operator fun invoke(): Flow<Boolean> = repository.observeAlbums().map { albums ->
        albums.any { it.bucketId == com.femi.gallery.domain.model.SpecialAlbums.LOCKED && it.itemCount > 0 }
    }
}
