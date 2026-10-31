package com.femi.gallery.data.db

import android.util.Log
import com.femi.gallery.data.mediastore.MediaStoreDataSource
import com.femi.gallery.di.ApplicationScope
import com.femi.gallery.domain.mirror.AlbumSummary
import com.femi.gallery.domain.mirror.AlbumSorting
import com.femi.gallery.domain.mirror.MirrorQuery
import com.femi.gallery.domain.mirror.MirrorSync
import com.femi.gallery.domain.model.Album
import com.femi.gallery.domain.model.MediaItem
import com.femi.gallery.domain.model.MediaOrder
import com.femi.gallery.domain.model.MediaOrdering
import com.femi.gallery.domain.model.MediaType
import dagger.Lazy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A local copy of the library: one row per photo or video in the index database, kept up to date from
 * MediaStore. Asking SQLite to sort, group and filter that copy is much faster than reading every row
 * from MediaStore (which is another process) each time the app starts or the library changes.
 *
 * Keeping it right:
 * - MediaStore has a generation number that rises with every change. The mirror remembers the number it
 *   was last brought up to date at. If it is still the same, nothing is done; if it is higher, only the rows
 *   changed since then are read, and the number of items is compared so that deleted and trashed items (which
 *   MediaStore cannot list as "changed") are found and dropped.
 * - The first time, everything is copied in the background. Until that is done [fresh] says no, and the
 *   caller reads MediaStore as it always did.
 * - Anything the mirror cannot answer (see [MirrorQuery]) is never asked of it.
 */
@Singleton
class MediaMirror @Inject constructor(
    private val dao: MediaDao,
    private val source: Lazy<MediaStoreDataSource>,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val lock = Mutex()
    private var importJob: Job? = null

    /**
     * Brings the mirror up to date and says whether it can answer now. False while the first copy is being
     * made, or when MediaStore cannot be read (for instance the media permission was taken away); the caller
     * then asks MediaStore directly.
     */
    suspend fun fresh(): Boolean = try {
        val generation = source.get().generation()
        when (MirrorSync.decide(dao.meta(MediaDao.GENERATION), generation)) {
            MirrorSync.Action.Nothing -> true
            MirrorSync.Action.Update -> lock.withLock { update() }
            MirrorSync.Action.Import -> {
                startImport()
                false
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // No permission, or the database is unhappy: the mirror is a speed-up, so MediaStore answers instead.
        Log.w(TAG, "The mirror cannot be used right now", e)
        false
    }

    /** Whether a copy exists that page loads can read from without checking MediaStore first. */
    suspend fun hasCopy(): Boolean = dao.meta(MediaDao.GENERATION) != null

    suspend fun order(query: MirrorQuery): MediaOrder {
        val rows = dao.order(
            bucketId = query.bucketId,
            type = query.mediaType?.let(::typeCode),
            favoritesOnly = if (query.favoritesOnly) 1 else 0,
        )
        val order = MediaOrder(
            ids = LongArray(rows.size) { rows[it].id },
            datesMillis = LongArray(rows.size) { rows[it].dateMillis },
            isVideos = BooleanArray(rows.size) { rows[it].type == 1 },
        )
        return MediaOrdering.withinDateRanges(order, query.dateRanges)
    }

    /** The albums, newest activity first with the Camera folder on top. */
    suspend fun albums(): List<Album> =
        AlbumSorting.order(
            dao.albums().map { AlbumSummary(it.bucketId, it.bucketName, it.relativePath, it.coverUri, it.itemCount, it.newestDate) },
        ).map { Album(bucketId = it.bucketId, name = it.name, relativePath = it.relativePath, coverUri = it.coverUri, itemCount = it.itemCount) }

    /** The items with these ids, in the order of [ids]; ids that are not in the mirror are skipped. */
    suspend fun items(ids: LongArray): List<MediaItem> {
        if (ids.isEmpty()) return emptyList()
        val byId = HashMap<Long, MediaItem>(ids.size * 2)
        ids.asList().chunked(ID_CHUNK).forEach { chunk -> dao.byIds(chunk).forEach { byId[it.id] = it.toItem() } }
        return ids.asList().mapNotNull { byId[it] }
    }

    suspend fun allItems(): List<MediaItem> = dao.all().map { it.toItem() }

    private fun startImport() {
        if (importJob?.isActive == true) return
        importJob = scope.launch {
            lock.withLock {
                try {
                    val generation = source.get().generation()
                    val items = source.get().loadAllItemsFromMediaStore()
                    dao.replaceAll(items.map { it.toRow() }, generation)
                    Log.i(TAG, "Copied ${items.size} items into the mirror")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Cannot copy the library yet", e)
                }
            }
        }
    }

    /** Called with [lock] held. True when the mirror is right afterwards. */
    private suspend fun update(): Boolean {
        val store = source.get()
        // The number is read first: anything that changes while this runs is picked up the next time.
        val generation = store.generation()
        val last = dao.meta(MediaDao.GENERATION) ?: return false
        if (MirrorSync.decide(last, generation) == MirrorSync.Action.Nothing) return true // another caller did it
        if (MirrorSync.decide(last, generation) == MirrorSync.Action.Import) {
            startImportLocked()
            return false
        }

        val changed = store.loadItemsChangedSince(last).map { it.toRow() }.toMutableList()
        var removed = LongArray(0)

        // Deleted and trashed items do not show up as "changed". If the counts differ, find out which.
        val alreadyThere = knownIds(changed)
        val countAfter = dao.count() + changed.count { it.id !in alreadyThere }
        if (store.countItems() != countAfter) {
            val mirrorIds = dao.allIds().toLongArray() + changed.map { it.id }.toLongArray()
            val reconciliation = MirrorSync.reconcile(store.loadAllIds(), mirrorIds)
            removed = reconciliation.toDelete
            if (reconciliation.missing.isNotEmpty()) {
                changed += store.loadByIdsFromMediaStore(reconciliation.missing).map { it.toRow() }
            }
        }
        dao.apply(changed, removed.toList(), generation)
        return true
    }

    /** The ids in [changed] that the mirror already has (they are replaced, not added). */
    private suspend fun knownIds(changed: List<MediaRow>): Set<Long> {
        if (changed.isEmpty()) return emptySet()
        val known = HashSet<Long>()
        changed.map { it.id }.chunked(ID_CHUNK).forEach { chunk -> dao.byIds(chunk).forEach { known.add(it.id) } }
        return known
    }

    private fun startImportLocked() {
        // The lock is held by the caller, so the import has to wait for it in its own coroutine.
        startImport()
    }

    private fun typeCode(type: MediaType): Int = if (type == MediaType.Video) VIDEO else IMAGE

    private fun MediaItem.toRow() = MediaRow(
        id = id,
        uri = uri,
        type = typeCode(type),
        displayName = displayName,
        mimeType = mimeType,
        dateMillis = dateTakenMillis,
        dateAddedMillis = dateAddedMillis,
        width = width,
        height = height,
        sizeBytes = sizeBytes,
        durationMillis = durationMillis,
        bucketId = bucketId,
        bucketName = bucketName,
        relativePath = relativePath,
        isFavorite = isFavorite,
    )

    private fun MediaRow.toItem() = MediaItem(
        id = id,
        uri = uri,
        displayName = displayName,
        mimeType = mimeType,
        type = if (type == VIDEO) MediaType.Video else MediaType.Image,
        dateTakenMillis = dateMillis,
        dateAddedMillis = dateAddedMillis,
        width = width,
        height = height,
        sizeBytes = sizeBytes,
        durationMillis = durationMillis,
        bucketId = bucketId,
        bucketName = bucketName,
        relativePath = relativePath,
        isFavorite = isFavorite,
    )

    private companion object {
        const val TAG = "MediaMirror"
        const val IMAGE = 0
        const val VIDEO = 1
        const val ID_CHUNK = 500
    }
}
