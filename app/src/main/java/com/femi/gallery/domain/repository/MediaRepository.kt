package com.femi.gallery.domain.repository

import androidx.paging.PagingData
import com.femi.gallery.domain.model.Album
import com.femi.gallery.domain.model.MediaDetails
import com.femi.gallery.domain.model.MediaItem
import com.femi.gallery.domain.model.MediaRef
import com.femi.gallery.domain.model.TimelineSection
import com.femi.gallery.domain.model.WriteRequest
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    /** All photos and videos on the device, newest first. Refreshes itself when MediaStore changes. */
    fun getPagedMedia(initialKey: Int? = null, bucketId: Long? = null): Flow<PagingData<MediaItem>>

    /** Device folders with cover and count, recomputed when MediaStore changes. */
    fun observeAlbums(): Flow<List<Album>>

    /** EXIF and file details for the info panel. Never throws; missing data is simply null. */
    suspend fun getDetails(item: MediaItem): MediaDetails

    /** Per-day sections of the media list, recomputed when MediaStore changes. */
    fun observeTimeline(bucketId: Long? = null): Flow<List<TimelineSection>>

    /** Ids, URIs and favorite flags of everything (optionally one album). */
    suspend fun getAllRefs(bucketId: Long? = null): List<MediaRef>

    /** Confirmation dialogs to move [uris] to / out of the system trash (kept for 30 days). */
    suspend fun createTrashRequests(uris: List<String>, trash: Boolean): List<WriteRequest>

    suspend fun createFavoriteRequests(uris: List<String>, favorite: Boolean): List<WriteRequest>

    /** Cheap check (MediaStore generation counter); invalidates the list only if something changed. */
    fun refreshIfChanged()
}
