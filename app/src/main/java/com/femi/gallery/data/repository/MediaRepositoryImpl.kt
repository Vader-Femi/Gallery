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
import com.femi.gallery.data.mediastore.MediaPagingSource
import com.femi.gallery.data.mediastore.MediaStoreDataSource
import com.femi.gallery.domain.model.MediaDetails
import com.femi.gallery.domain.model.MediaItem
import com.femi.gallery.domain.model.TimelineGrouper
import com.femi.gallery.domain.model.TimelineSection
import com.femi.gallery.domain.repository.MediaRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
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

    override fun getPagedMedia(initialKey: Int?): Flow<PagingData<MediaItem>> = Pager(
        config = PagingConfig(
            pageSize = 120,
            initialLoadSize = 240,
            prefetchDistance = 120,
            enablePlaceholders = true,
            jumpThreshold = 240,
        ),
        initialKey = initialKey,
        pagingSourceFactory = {
            MediaPagingSource(dataSource, context).also { sources.add(it) }
        },
    ).flow

    override suspend fun getDetails(item: MediaItem): MediaDetails = exifReader.read(item)

    @OptIn(FlowPreview::class)
    override fun observeTimeline(): Flow<List<TimelineSection>> =
        merge(mediaChanges().debounce(CHANGE_DEBOUNCE_MS), refreshSignal)
            .onStart { emit(Unit) }
            .conflate()
            .map {
                indexedGeneration = dataSource.generation()
                TimelineGrouper.group(dataSource.loadDatesAdded())
            }
            .distinctUntilChanged()

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
    }
}
