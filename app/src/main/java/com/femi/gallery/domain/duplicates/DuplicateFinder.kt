package com.femi.gallery.domain.duplicates

import com.femi.gallery.domain.model.MediaItem
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Finds files that are byte-for-byte the same, reading as little as possible:
 *
 * 1. Two files can only be equal if they have the same size (and are both photos or both videos), which
 *    the library already knows, so most files are ruled out without being opened.
 * 2. Files that share a size are compared by a few small pieces of each.
 * 3. Only files whose pieces also match are read completely.
 *
 * Files that cannot be read are left out rather than guessed at.
 */
class DuplicateFinder(private val hasher: FileHasher) {

    suspend fun find(
        items: List<MediaItem>,
        onProgress: (DuplicateProgress) -> Unit = {},
    ): List<DuplicateGroup> {
        val sameSize = items
            .filter { it.sizeBytes > 0 }
            .groupBy { it.sizeBytes to it.type }
            .values
            .filter { it.size > 1 }

        val toSample = sameSize.sumOf { it.size }
        var sampled = 0
        val samePieces = ArrayList<List<MediaItem>>()
        for (group in sameSize) {
            val byHash = LinkedHashMap<String, MutableList<MediaItem>>()
            for (item in group) {
                currentCoroutineContext().ensureActive()
                hasher.partialHash(item)?.let { byHash.getOrPut(it) { ArrayList() }.add(item) }
                sampled++
                onProgress(DuplicateProgress(DuplicatePhase.Sampling, sampled, toSample))
            }
            byHash.values.filter { it.size > 1 }.forEach { samePieces.add(it) }
        }

        val toVerify = samePieces.sumOf { it.size }
        var verified = 0
        val result = ArrayList<DuplicateGroup>()
        for (group in samePieces) {
            val byHash = LinkedHashMap<String, MutableList<MediaItem>>()
            for (item in group) {
                currentCoroutineContext().ensureActive()
                hasher.fullHash(item)?.let { byHash.getOrPut(it) { ArrayList() }.add(item) }
                verified++
                onProgress(DuplicateProgress(DuplicatePhase.Verifying, verified, toVerify))
            }
            for (same in byHash.values) {
                if (same.size > 1) result.add(DuplicateGroup(items = same, keepId = DuplicateKeepRule.choose(same).id))
            }
        }
        return result.sortedByDescending { it.reclaimableBytes }
    }
}
