package com.femi.gallery.domain.repository

import androidx.paging.PagingData
import com.femi.gallery.domain.model.MediaItem
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    /** All photos and videos on the device, newest first. Refreshes itself when MediaStore changes. */
    fun getPagedMedia(): Flow<PagingData<MediaItem>>
}
