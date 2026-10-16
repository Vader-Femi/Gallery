package com.femi.gallery.domain.usecase

import com.femi.gallery.domain.model.Album
import com.femi.gallery.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveAlbumsUseCase @Inject constructor(
    private val repository: MediaRepository,
) {
    operator fun invoke(): Flow<List<Album>> = repository.observeAlbums()
}
