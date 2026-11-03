package com.femi.gallery.domain.similar

import com.femi.gallery.domain.duplicates.DuplicateGroup
import com.femi.gallery.domain.model.MediaItem

/**
 * Groups photos whose [DifferenceHash]es differ in at most [maxDistance] bits.
 *
 * Comparing every photo with every other would take too long for a big library, so the 64 bits are cut into
 * `maxDistance + 1` pieces: two hashes that differ in at most `maxDistance` bits must be identical in at least one
 * piece, so only photos that share a piece with the same value are ever compared. Photos that were found alike are
 * then gathered so that every photo of a group is within [maxDistance] of the group's first photo; a long chain of
 * "A is like B, B is like C" does not pull unlike photos into one group.
 */
class SimilarFinder(maxDistance: Int = DEFAULT_MAX_DISTANCE) {
    private val maxDistance = maxDistance.coerceIn(1, MAX_ALLOWED_DISTANCE)

    /** Groups of two or more photos, those with the most to gain first. [hashes] maps a photo id to its hash. */
    fun find(items: List<MediaItem>, hashes: Map<Long, Long>): List<DuplicateGroup> {
        val known = items.filter { it.id in hashes }
        if (known.size < 2) return emptyList()
        val hash = LongArray(known.size) { hashes.getValue(known[it].id) }

        // Photos that share a piece of the hash are the only candidates for each other.
        val pieces = maxDistance + 1
        val buckets = HashMap<Long, MutableList<Int>>()
        for (i in known.indices) {
            for (k in 0 until pieces) buckets.getOrPut(bucketKey(hash[i], k, pieces)) { ArrayList(4) }.add(i)
        }

        val parent = IntArray(known.size) { it }
        fun root(x: Int): Int {
            var r = x
            while (parent[r] != r) r = parent[r]
            var c = x
            while (parent[c] != r) { val next = parent[c]; parent[c] = r; c = next }
            return r
        }
        for (members in buckets.values) {
            if (members.size < 2) continue
            for (a in 0 until members.size - 1) {
                for (b in a + 1 until members.size) {
                    val i = members[a]
                    val j = members[b]
                    if (DifferenceHash.distance(hash[i], hash[j]) <= maxDistance) {
                        val ri = root(i)
                        val rj = root(j)
                        if (ri != rj) parent[maxOf(ri, rj)] = minOf(ri, rj)
                    }
                }
            }
        }

        val components = HashMap<Int, MutableList<Int>>()
        for (i in known.indices) components.getOrPut(root(i)) { ArrayList() }.add(i)

        val groups = ArrayList<DuplicateGroup>()
        for (component in components.values) {
            if (component.size < 2) continue
            val ordered = component.sortedWith(compareBy({ known[it].dateTakenMillis }, { known[it].id }))
            val clusters = ArrayList<MutableList<Int>>()
            for (index in ordered) {
                val home = clusters.firstOrNull { DifferenceHash.distance(hash[it[0]], hash[index]) <= maxDistance }
                if (home != null) home.add(index) else clusters.add(arrayListOf(index))
            }
            for (cluster in clusters) {
                if (cluster.size < 2) continue
                val members = cluster.map { known[it] }
                groups.add(DuplicateGroup(items = members, keepId = SimilarKeepRule.choose(members).id))
            }
        }
        return groups.sortedWith(
            compareByDescending<DuplicateGroup> { it.reclaimableBytes }.thenByDescending { it.items.size }.thenBy { it.keepId },
        )
    }

    private fun bucketKey(hash: Long, piece: Int, pieces: Int): Long {
        val from = piece * 64 / pieces
        val to = (piece + 1) * 64 / pieces
        val width = to - from
        val value = (hash ushr from) and (if (width >= 64) -1L else (1L shl width) - 1)
        return (piece.toLong() shl 48) or value // a piece is at most 32 bits wide, so the two parts do not meet
    }

    companion object {
        /** Up to this many of the 64 bits may differ: a resized or re-saved copy, or the next shot of a burst. */
        const val DEFAULT_MAX_DISTANCE = 6
        private const val MAX_ALLOWED_DISTANCE = 16
    }
}
