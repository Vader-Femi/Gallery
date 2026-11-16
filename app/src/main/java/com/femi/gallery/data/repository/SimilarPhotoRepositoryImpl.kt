package com.femi.gallery.data.repository

import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.femi.gallery.data.mediastore.MediaStoreDataSource
import com.femi.gallery.data.similar.ThumbnailPerceptualHasher
import com.femi.gallery.di.IoDispatcher
import com.femi.gallery.domain.duplicates.DuplicateGroup
import com.femi.gallery.domain.repository.SimilarPhotoRepository
import com.femi.gallery.domain.repository.SimilarScanState
import com.femi.gallery.domain.similar.SimilarFinder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SimilarPhotoRepositoryImpl @Inject constructor(
    private val dataSource: MediaStoreDataSource,
    private val hasher: ThumbnailPerceptualHasher,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : SimilarPhotoRepository {

    private val workers = Dispatchers.Default.limitedParallelism(3)

    @Volatile
    private var cachedGroups: List<DuplicateGroup>? = null

    @Volatile
    private var cachedGeneration: Long = -1L

    /** The hidden albums the cached groups were built for; hiding an album does not change the MediaStore generation. */
    @Volatile
    private var cachedHiddenKey: Long = 0L

    override fun scan(): Flow<SimilarScanState> = channelFlow {
        val currentGeneration = dataSource.generation()
        val hiddenKey = dataSource.hiddenAlbumsKey()
        // Groups made while other albums were hidden may hold photos that must not show now.
        val cached = cachedGroups?.takeIf { cachedHiddenKey == hiddenKey }
        if (cached != null && currentGeneration == cachedGeneration) {
            send(SimilarScanState.Done(cached))
            return@channelFlow
        }

        // If we have cached results, emit them first so the UI can display them immediately
        // while the background check for any new/modified photos completes.
        if (cached != null) {
            send(SimilarScanState.Done(cached))
        } else {
            send(SimilarScanState.Listing)
        }

        val startedAt = SystemClock.elapsedRealtime()
        // Videos are not compared; hidden albums are already left out by the data source.
        val items = dataSource.loadAllItems().filter { !it.isVideo }
        hasher.forgetExcept(items.mapTo(HashSet()) { it.id })

        val known = hasher.knownHashes(items)
        val hashes = known.hashes
        val unhashed = items.filter { it.id !in known.evaluatedIds }

        if (unhashed.isNotEmpty()) {
            val totalToHash = unhashed.size
            try {
                var next = 0
                while (next < totalToHash) {
                    val end = minOf(totalToHash, next + BATCH_SIZE)
                    val batch = coroutineScope {
                        (next until end).map { i ->
                            async(workers) {
                                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                                hasher.hash(unhashed[i])
                            }
                        }.awaitAll()
                    }
                    for (i in next until end) batch[i - next]?.let { hashes[unhashed[i].id] = it }
                    next = end
                    trySend(SimilarScanState.Hashing(next, totalToHash))
                }
            } finally {
                // What was learned is kept even when the search is cancelled half way.
                withContext(NonCancellable) { hasher.flush() }
            }
        }

        val groups = withContext(io) { SimilarFinder().find(items, hashes) }
        cachedGroups = groups
        cachedGeneration = currentGeneration
        cachedHiddenKey = hiddenKey
        Log.i(TAG, "${items.size} photos, ${hashes.size} with a hash (${unhashed.size} newly hashed), ${groups.size} groups in ${SystemClock.elapsedRealtime() - startedAt} ms")
        send(SimilarScanState.Done(groups))
    }.conflate() // a slow screen skips old progress, never the final result

    private companion object {
        const val TAG = "SimilarScan"
        const val BATCH_SIZE = 32
    }
}
