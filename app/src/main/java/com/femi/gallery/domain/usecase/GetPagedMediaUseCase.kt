package com.femi.gallery.domain.usecase

import androidx.paging.PagingData
import com.femi.gallery.domain.model.MediaItem
import com.femi.gallery.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetPagedMediaUseCase @Inject constructor(
    private val repository: MediaRepository,
) {
    operator fun invoke(): Flow<PagingData<MediaItem>> = repository.getPagedMedia()
}
