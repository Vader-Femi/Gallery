package com.femi.gallery.presentation.common

import android.util.LruCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import coil3.memory.MemoryCache

/**
 * Remembers which in-memory image the grid used for each photo, so the viewer can show exactly that
 * image in the very first frame while it loads its own, sharper one. Without it, opening a photo
 * whose thumbnail was not cached at the viewer's size starts from an empty (black) frame.
 */
object GridThumbnails {
    private const val MAX_ENTRIES = 600
    private val keys = LruCache<Long, MemoryCache.Key>(MAX_ENTRIES)
    private val sizes = LruCache<Long, Size>(MAX_ENTRIES)

    fun remember(mediaId: Long, key: MemoryCache.Key?, size: Size = Size.Unspecified) {
        if (key != null) keys.put(mediaId, key)
        if (size.isSpecified && size.width > 0f && size.height > 0f) sizes.put(mediaId, size)
    }

    fun keyFor(mediaId: Long): MemoryCache.Key? = keys.get(mediaId)

    /**
     * The size of the grid's thumbnail, so the viewer knows the photo's shape from its very first
     * frame (and with the rotation already applied, unlike the width and height in the database).
     */
    fun sizeFor(mediaId: Long): Size? = sizes.get(mediaId)
}
