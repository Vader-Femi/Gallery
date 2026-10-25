package com.femi.gallery.data.repository

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.femi.gallery.data.exif.ExifReader
import com.femi.gallery.data.locked.LockedPagingSource
import com.femi.gallery.data.locked.LockedStore
import com.femi.gallery.data.mediastore.MediaPagingSource
import com.femi.gallery.data.mediastore.MediaStoreDataSource
import com.femi.gallery.domain.model.Album
import com.femi.gallery.domain.model.LockedCopy
import com.femi.gallery.domain.model.MediaDetails
import com.femi.gallery.domain.model.MediaItem
import com.femi.gallery.domain.model.MediaRef
import com.femi.gallery.domain.model.SpecialAlbums
import com.femi.gallery.domain.model.Story
import com.femi.gallery.domain.model.StoryDetector
import com.femi.gallery.domain.model.TimelineGrouper
import com.femi.gallery.domain.model.TimelineSection
import com.femi.gallery.domain.model.WriteRequest
import com.femi.gallery.domain.repository.MediaRepository
import com.femi.gallery.domain.search.SearchCriteria
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import java.util.Collections
import java.util.WeakHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dataSource: MediaStoreDataSource,
    private val exifReader: ExifReader,
    private val lockedStore: LockedStore,
) : MediaRepository {

    /** Live paging sources (grid and viewer each have one); weakly held so they can be collected. */
    private val sources: MutableSet<MediaPagingSource> =
        Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<MediaPagingSource, Boolean>()))

    @Volatile
    private var indexedGeneration = -1L

    private val refreshSignal = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override fun refreshIfChanged() {
        synchronized(sources) { sources.toList() }.forEach { it.invalidateIfStale() }
        if (dataSource.generation() != indexedGeneration) refreshSignal.tryEmit(Unit)
    }

    override fun getPagedMedia(
        initialKey: Int?,
        bucketId: Long?,
        search: SearchCriteria?,
    ): Flow<PagingData<MediaItem>> = Pager(
        config = PagingConfig(
            pageSize = 120,
            initialLoadSize = 240,
            prefetchDistance = 120,
            enablePlaceholders = true,
            jumpThreshold = 240,
        ),
        initialKey = initialKey,
        pagingSourceFactory = {
            if (bucketId == SpecialAlbums.LOCKED) {
                LockedPagingSource(lockedStore)
            } else {
                MediaPagingSource(dataSource, context, bucketId, search).also { sources.add(it) }
            }
        },
    ).flow

    override suspend fun getDetails(item: MediaItem): MediaDetails = exifReader.read(item)

    override suspend fun getAlbumName(bucketId: Long): String = dataSource.loadAlbumName(bucketId)

    override suspend fun getAllRefs(bucketId: Long?, search: SearchCriteria?): List<MediaRef> =
        if (bucketId == SpecialAlbums.LOCKED) {
            lockedStore.list().map { MediaRef(it.id, it.uri, isFavorite = false) }
        } else {
            dataSource.loadRefs(bucketId, search)
        }

    override suspend fun createWriteRequests(uris: List<String>): List<WriteRequest> =
        dataSource.createWriteRequests(uris).map(::WriteRequest)

    override suspend fun moveToFolder(uris: List<String>, relativePath: String): Int =
        dataSource.moveItems(uris, relativePath)

    override suspend fun copyToFolder(uris: List<String>, relativePath: String): Int =
        dataSource.copyItems(uris, relativePath)

    override suspend fun copyToLocked(uris: List<String>): List<LockedCopy> = lockedStore.copyIn(uris)

    override suspend fun unlockItems(ids: List<Long>): Int = lockedStore.restore(ids)

    override suspend fun deleteLockedItems(ids: List<Long>) = lockedStore.delete(ids)

    override suspend fun createTrashRequests(uris: List<String>, trash: Boolean): List<WriteRequest> =
        dataSource.createTrashRequests(uris, trash).map(::WriteRequest)

    override suspend fun createDeleteRequests(uris: List<String>): List<WriteRequest> =
        dataSource.createDeleteRequests(uris).map(::WriteRequest)

    override suspend fun createFavoriteRequests(uris: List<String>, favorite: Boolean): List<WriteRequest> =
        dataSource.createFavoriteRequests(uris, favorite).map(::WriteRequest)

    override fun observeTimeline(bucketId: Long?, search: SearchCriteria?): Flow<List<TimelineSection>> =
        if (bucketId == SpecialAlbums.LOCKED) {
            lockedStore.version
                .map { TimelineGrouper.group(lockedStore.datesSeconds()) }
                .distinctUntilChanged()
        } else {
            changeTriggers()
                .map {
                    indexedGeneration = dataSource.generation()
                    TimelineGrouper.group(dataSource.loadDatesSeconds(bucketId, search))
                }
                .distinctUntilChanged()
        }

    override fun observeAlbums(): Flow<List<Album>> =
        changeTriggers().map { dataSource.loadAlbums() }
            .distinctUntilChanged()

    override fun observeStories(): Flow<List<Story>> =
        changeTriggers()
            .map {
                val order = dataSource.loadOrder()
                val days = TimelineGrouper.group(LongArray(order.size) { order.datesMillis[it] / 1000 })
                val candidates = StoryDetector.detect(days).take(MAX_STORIES)

                // One batch of queries for all the covers (a story's cover is a photo from its middle).
                val coverIds = candidates.map { order.ids[it.coverIndex] }
                val covers = coverIds.chunked(COVER_BATCH_SIZE)
                    .flatMap { dataSource.loadByIds(it.toLongArray()) }
                    .associateBy { it.id }
                candidates.mapNotNull { candidate ->
                    covers[order.ids[candidate.coverIndex]]?.let { cover ->
                        Story(candidate.fromDay, candidate.toDay, candidate.count, cover.uri)
                    }
                }
            }
            .distinctUntilChanged()

    /** Emits once on start, then (debounced) whenever MediaStore changes or the app resumes after a change. */
    @OptIn(FlowPreview::class)
    private fun changeTriggers(): Flow<Unit> =
        merge(mediaChanges().debounce(CHANGE_DEBOUNCE_MS), refreshSignal)
            .onStart { emit(Unit) }
            .conflate()

    private fun mediaChanges(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        context.contentResolver.registerContentObserver(MediaStore.AUTHORITY_URI, true, observer)
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }

    private companion object {
        const val CHANGE_DEBOUNCE_MS = 300L
        const val MAX_STORIES = 300
        const val COVER_BATCH_SIZE = 300
    }
}
