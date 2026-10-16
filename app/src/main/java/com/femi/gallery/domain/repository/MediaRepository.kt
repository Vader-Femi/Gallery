package com.femi.gallery.domain.repository

import androidx.paging.PagingData
import com.femi.gallery.domain.model.MediaDetails
import com.femi.gallery.domain.model.MediaItem
import com.femi.gallery.domain.model.TimelineSection
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    /** All photos and videos on the device, newest first. Refreshes itself when MediaStore changes. */
    fun getPagedMedia(initialKey: Int? = null): Flow<PagingData<MediaItem>>

    /** EXIF and file details for the info panel. Never throws; missing data is simply null. */
    suspend fun getDetails(item: MediaItem): MediaDetails

    /** Per-day sections of the media list, recomputed when MediaStore changes. */
    fun observeTimeline(): Flow<List<TimelineSection>>

    /** Cheap check (MediaStore generation counter); invalidates the list only if something changed. */
    fun refreshIfChanged()
}
