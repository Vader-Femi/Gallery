package com.femi.gallery.presentation.common

import android.util.LruCache
import coil3.memory.MemoryCache

/**
 * Remembers which in-memory image the grid used for each photo, so the viewer can show exactly that
 * image in the very first frame while it loads its own, sharper one. Without it, opening a photo
 * whose thumbnail was not cached at the viewer's size starts from an empty (black) frame.
 */
object GridThumbnails {
    private const val MAX_ENTRIES = 600
    private val keys = LruCache<Long, MemoryCache.Key>(MAX_ENTRIES)

    fun remember(mediaId: Long, key: MemoryCache.Key?) {
        if (key != null) keys.put(mediaId, key)
    }

    fun keyFor(mediaId: Long): MemoryCache.Key? = keys.get(mediaId)
}
