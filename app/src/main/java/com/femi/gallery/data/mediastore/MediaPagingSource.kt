package com.femi.gallery.data.mediastore

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.femi.gallery.domain.model.MediaItem

/**
 * Offset-based paging straight from MediaStore. Placeholders are enabled by reporting
 * [LoadResult.Page.itemsBefore]/[LoadResult.Page.itemsAfter], which lets the UI jump to any
 * position (fast scroller, date scrubber) without loading everything in between.
 *
 * A source is invalidated (and replaced by Paging) whenever MediaStore changes, so the total
 * count is computed once per instance instead of on every page load.
 */
class MediaPagingSource(
    private val dataSource: MediaStoreDataSource,
    private val context: Context,
) : PagingSource<Int, MediaItem>() {

    private val generationAtCreation = dataSource.generation()
    private var cachedTotal: Int? = null

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = invalidate()
    }

    init {
        dataSource.observedUris.forEach { uri ->
            context.contentResolver.registerContentObserver(uri, true, observer)
        }
        registerInvalidatedCallback {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }

    override val jumpingSupported: Boolean = true

    /** Fallback for missed notifications, e.g. changes made while the app was in the background. */
    fun invalidateIfStale() {
        if (!invalid && dataSource.generation() != generationAtCreation) invalidate()
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaItem> = try {
        val total = cachedTotal ?: dataSource.count().also { cachedTotal = it }
        val offset = (params.key ?: 0).coerceIn(0, maxOf(0, total - 1))
        val items = dataSource.load(offset = offset, limit = params.loadSize)
        LoadResult.Page(
            data = items,
            prevKey = if (offset == 0) null else maxOf(0, offset - params.loadSize),
            nextKey = if (offset + items.size >= total) null else offset + items.size,
            itemsBefore = offset,
            itemsAfter = maxOf(0, total - offset - items.size),
        )
    } catch (e: SecurityException) {
        // Permission revoked while the app was running.
        LoadResult.Error(e)
    }

    override fun getRefreshKey(state: PagingState<Int, MediaItem>): Int? =
        state.anchorPosition?.let { maxOf(0, it - state.config.initialLoadSize / 2) }
}
