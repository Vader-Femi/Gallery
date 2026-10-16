package com.femi.gallery.domain.usecase

import androidx.paging.PagingData
import com.femi.gallery.domain.model.MediaItem
import com.femi.gallery.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetPagedMediaUseCase @Inject constructor(
    private val repository: MediaRepository,
) {
    /**
     * [initialKey] is the media index to start loading around (used by the viewer).
     * [bucketId] limits the list to one album.
     */
    operator fun invoke(initialKey: Int? = null, bucketId: Long? = null): Flow<PagingData<MediaItem>> =
        repository.getPagedMedia(initialKey, bucketId)
}
