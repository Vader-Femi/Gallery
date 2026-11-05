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
import com.femi.gallery.domain.model.StoryEdit
import com.femi.gallery.domain.model.StoryEdits
import com.femi.gallery.domain.model.StoryTitle
import com.femi.gallery.domain.model.StoryVideos
import com.femi.gallery.domain.model.MediaType
import com.femi.gallery.domain.repository.StoryEditStore
import com.femi.gallery.domain.search.SearchCriteria
import com.femi.gallery.domain.repository.SettingsRepository
import com.femi.gallery.domain.model.TimelineGrouper
import com.femi.gallery.domain.model.TimelineSection
import com.femi.gallery.domain.model.WriteRequest
import com.femi.gallery.domain.repository.MediaRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import com.femi.gallery.data.db.FaceDao
import com.femi.gallery.data.db.MediaDao
import com.femi.gallery.data.db.PersonDao
import com.femi.gallery.data.db.StoryDao
import com.femi.gallery.data.db.StoryRow
import com.femi.gallery.data.label.LabelIndexHolder
import com.femi.gallery.data.map.PhotoPlaceIndexHolder
import com.femi.gallery.domain.model.SmartStoryDetector
import java.time.LocalDate
import java.util.Collections
import java.time.Instant
import java.time.ZoneId
import java.util.WeakHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dataSource: MediaStoreDataSource,
    private val exifReader: ExifReader,
    private val lockedStore: LockedStore,
    private val storyEditStore: StoryEditStore,
    private val settingsRepository: SettingsRepository,
    private val mediaDao: MediaDao,
    private val faceDao: FaceDao,
    private val personDao: PersonDao,
    private val storyDao: StoryDao,
    private val placeIndexHolder: PhotoPlaceIndexHolder,
    private val labelIndexHolder: LabelIndexHolder,
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

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getPagedMedia(
        initialKey: Int?,
        bucketId: Long?,
        search: SearchCriteria?,
    ): Flow<PagingData<MediaItem>> = hiddenAlbumsChanges().flatMapLatest {
        Pager(
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
    }

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

    private val _isStoriesUpdating = MutableStateFlow(false)
    override fun isStoriesUpdating(): Flow<Boolean> = _isStoriesUpdating.asStateFlow()

    override fun observeStories(): Flow<List<Story>> =
        storyDao.flowAll()
            .map { rows -> rows.map { it.toStory() } }
            .distinctUntilChanged()

    override suspend fun refreshStories() {
        if (_isStoriesUpdating.value) return
        _isStoriesUpdating.value = true
        try {
            withContext(Dispatchers.IO) {
                computeAndCacheStories()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error refreshing stories", e)
        } finally {
            _isStoriesUpdating.value = false
        }
    }

    private suspend fun computeAndCacheStories() {
        val order = dataSource.loadOrder(search = SearchCriteria(excludeJunk = true))
        if (order.size == 0) {
            storyDao.replaceAll(emptyList())
            return
        }

        val days = TimelineGrouper.group(LongArray(order.size) { order.datesMillis[it] / 1000 })
        val candidates = StoryDetector.detect(days).take(MAX_STORIES)
        if (candidates.isEmpty()) {
            storyDao.replaceAll(emptyList())
            return
        }

        val edits = storyEditStore.edits.firstOrNull().orEmpty()
        val edited = StoryEdits.assign(edits, candidates.map { it.fromDay to it.toDay })

        // One batch of queries for all the covers
        val autoCoverIds = candidates.map { order.ids[it.coverIndex] }
        val chosenCoverIds = edited.mapNotNull { it?.coverId }
        val covers = (autoCoverIds + chosenCoverIds).distinct().chunked(COVER_BATCH_SIZE)
            .flatMap { dataSource.loadByIds(it.toLongArray()) }
            .associateBy { it.id }

        val videoDays = videoDays()
        val zone = ZoneId.systemDefault()
        val placeIndex = try { placeIndexHolder.current() } catch (e: Exception) { null }
        val labelIndex = try { labelIndexHolder.current() } catch (e: Exception) { null }
        val namedPeople = try { personDao.all().filter { it.isNamed }.associateBy { it.id } } catch (e: Exception) { emptyMap() }

        val stories = candidates.mapIndexedNotNull { index, candidate ->
            val edit = edited[index]
            val cover = edit?.coverId?.let { covers[it] } ?: covers[autoCoverIds[index]]
            if (cover == null) return@mapIndexedNotNull null

            // Collect the IDs of photos in this candidate's date range
            val storyIds = mutableListOf<Long>()
            for (i in 0 until order.size) {
                val epochDay = Instant.ofEpochMilli(order.datesMillis[i]).atZone(zone).toLocalDate().toEpochDay()
                if (epochDay in candidate.fromDay..candidate.toDay) {
                    storyIds.add(order.ids[i])
                }
            }

            // 1. Resolve dominant place
            val placeName = placeIndex?.let { pIndex ->
                val cityCounts = mutableMapOf<String, Int>()
                for (id in storyIds) {
                    val city = pIndex.cityNameFor(id)
                    if (city != null) {
                        cityCounts[city] = (cityCounts[city] ?: 0) + 1
                    }
                }
                cityCounts.maxByOrNull { it.value }?.takeIf { it.value >= 2 }?.key
            }

            // 2. Resolve prominent named people
            val prominentPeople = if (namedPeople.isNotEmpty() && storyIds.isNotEmpty()) {
                val personCounts = mutableMapOf<Long, Int>()
                for (id in storyIds) {
                    val faces = faceDao.getForMedia(id)
                    for (face in faces) {
                        if (face.personId in namedPeople) {
                            personCounts[face.personId] = (personCounts[face.personId] ?: 0) + 1
                        }
                    }
                }
                personCounts.entries
                    .sortedByDescending { it.value }
                    .filter { it.value >= 2 }
                    .mapNotNull { namedPeople[it.key]?.name }
                    .take(2)
            } else emptyList()

            // 3. Resolve top scene
            val topScene = labelIndex?.let { lIndex ->
                val sceneCounts = mutableMapOf<String, Int>()
                val keyScenes = listOf("beach", "nature", "mountain", "food", "pet", "dog", "cat", "sunset", "lake", "party")
                for (id in storyIds) {
                    val labels = lIndex.labelsFor(id)
                    for (s in keyScenes) {
                        if (labels.any { it.contains(s, ignoreCase = true) }) {
                            sceneCounts[s] = (sceneCounts[s] ?: 0) + 1
                        }
                    }
                }
                sceneCounts.maxByOrNull { it.value }?.takeIf { it.value >= 3 }?.key
            }

            val signals = SmartStoryDetector.ContextSignals(
                placeName = placeName,
                prominentPeople = prominentPeople,
                topScene = topScene,
            )
            val (smartTitle, theme) = SmartStoryDetector.determineTitleAndTheme(candidate.fromDay, candidate.toDay, signals)
            val subtitle = SmartStoryDetector.buildSubtitle(candidate.fromDay, candidate.toDay, signals, theme, candidate.count)

            Story(
                fromDay = candidate.fromDay,
                toDay = candidate.toDay,
                count = candidate.count,
                coverUri = cover.uri,
                title = edit?.title,
                videoCount = StoryVideos.countInRange(videoDays, candidate.fromDay, candidate.toDay),
                smartTitle = smartTitle,
                subtitle = subtitle,
                theme = theme,
            )
        }

        storyDao.replaceAll(stories.map { StoryRow.fromStory(it) })
    }

    /** The calendar day (as an epoch day) of every video, oldest first. */
    private suspend fun videoDays(): LongArray {
        val videos = dataSource.loadOrder(search = SearchCriteria(mediaType = MediaType.Video, excludeJunk = true))
        val zone = ZoneId.systemDefault()
        return LongArray(videos.size) {
            Instant.ofEpochMilli(videos.datesMillis[it]).atZone(zone).toLocalDate().toEpochDay()
        }.also { it.sort() }
    }

    override fun observeStoryEdit(fromDay: Long, toDay: Long): Flow<StoryEdit?> =
        storyEditStore.edits.map { StoryEdits.find(it, fromDay, toDay) }.distinctUntilChanged()

    override suspend fun renameStory(fromDay: Long, toDay: Long, title: String?) {
        val cleaned = title?.let(StoryTitle::clean)
        storyEditStore.update { StoryEdits.update(it, fromDay, toDay) { edit -> edit.copy(title = cleaned) } }
        refreshStories()
    }

    override suspend fun setStoryCover(fromDay: Long, toDay: Long, mediaId: Long?) {
        storyEditStore.update { StoryEdits.update(it, fromDay, toDay) { edit -> edit.copy(coverId = mediaId) } }
        refreshStories()
    }

    /** Emits once on start, then (debounced) whenever MediaStore changes or the app resumes after a change. */
    @OptIn(FlowPreview::class)
    private fun changeTriggers(): Flow<Unit> =
        merge(mediaChanges().debounce(CHANGE_DEBOUNCE_MS), refreshSignal, hiddenAlbumsChanges().map { })
            .onStart { emit(Unit) }
            .conflate()

    /** The albums hidden in Settings; every list built from the library starts again when they change. */
    private fun hiddenAlbumsChanges(): Flow<Set<Long>> =
        settingsRepository.hiddenAlbums
            .map { set -> set.mapNotNull { it.toLongOrNull() }.toSet() }
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
        const val TAG = "MediaRepository"
        const val CHANGE_DEBOUNCE_MS = 300L
        const val MAX_STORIES = 300
        const val COVER_BATCH_SIZE = 300
    }
}
