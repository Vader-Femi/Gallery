package com.femi.gallery.domain.repository

import androidx.paging.PagingData
import com.femi.gallery.domain.model.Album
import com.femi.gallery.domain.model.LockedCopy
import com.femi.gallery.domain.model.MediaDetails
import com.femi.gallery.domain.model.MediaItem
import com.femi.gallery.domain.model.MediaRef
import com.femi.gallery.domain.model.Story
import com.femi.gallery.domain.model.TimelineSection
import com.femi.gallery.domain.model.WriteRequest
import com.femi.gallery.domain.search.SearchCriteria
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    /** All photos and videos on the device, newest first. Refreshes itself when MediaStore changes. */
    fun getPagedMedia(
        initialKey: Int? = null,
        bucketId: Long? = null,
        search: SearchCriteria? = null,
    ): Flow<PagingData<MediaItem>>

    /** Device folders with cover and count, recomputed when MediaStore changes. */
    fun observeAlbums(): Flow<List<Album>>

    /** Automatically found events (busy days or runs of days), newest first. */
    fun observeStories(): Flow<List<Story>>

    /** Fetches the localized or user-defined name of a specific folder. */
    suspend fun getAlbumName(bucketId: Long): String

    /** EXIF and file details for the info panel. Never throws; missing data is simply null. */
    suspend fun getDetails(item: MediaItem): MediaDetails

    /** Per-day sections of the media list, recomputed when MediaStore changes. */
    fun observeTimeline(bucketId: Long? = null, search: SearchCriteria? = null): Flow<List<TimelineSection>>

    /** Ids, URIs and favorite flags of everything (optionally one album). */
    suspend fun getAllRefs(bucketId: Long? = null, search: SearchCriteria? = null): List<MediaRef>

    /** Confirmation dialogs to move [uris] to / out of the system trash (kept for 30 days). */
    suspend fun createTrashRequests(uris: List<String>, trash: Boolean): List<WriteRequest>

    suspend fun createFavoriteRequests(uris: List<String>, favorite: Boolean): List<WriteRequest>

    /** Confirmation dialogs to delete [uris] permanently (used from the recycle bin). */
    suspend fun createDeleteRequests(uris: List<String>): List<WriteRequest>

    /**
     * Confirmation dialogs that let the app change [uris] (needed to move files other apps
     * created). Approve them first, then call [moveToFolder].
     */
    suspend fun createWriteRequests(uris: List<String>): List<WriteRequest>

    /** Moves already-approved items into the album folder [relativePath]; returns how many moved. */
    suspend fun moveToFolder(uris: List<String>, relativePath: String): Int

    /** Copies items into the album folder [relativePath]; returns how many were copied. */
    suspend fun copyToFolder(uris: List<String>, relativePath: String): Int

    /** Copies photos/videos into the private Locked folder. The originals are left untouched. */
    suspend fun copyToLocked(uris: List<String>): List<LockedCopy>

    /** Moves items from the Locked folder back into the gallery; returns how many were restored. */
    suspend fun unlockItems(ids: List<Long>): Int

    /** Permanently deletes items from the Locked folder. */
    suspend fun deleteLockedItems(ids: List<Long>)

    /** Cheap check (MediaStore generation counter); invalidates the list only if something changed. */
    fun refreshIfChanged()
}
