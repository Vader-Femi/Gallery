package com.femi.gallery.data.mediastore

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.femi.gallery.domain.model.MediaItem
import com.femi.gallery.domain.model.MediaOrder
import com.femi.gallery.domain.model.PageWindow
import com.femi.gallery.domain.search.SearchCriteria

/**
 * Pages through the library in "date taken, else date added" order. The order of every matching
 * item is read once per source (two numbers per item); each page then loads just its own rows.
 *
 * Placeholders are enabled by reporting [LoadResult.Page.itemsBefore]/[LoadResult.Page.itemsAfter],
 * which lets the UI jump to any position (fast scroller, date scrubber) without loading
 * everything in between.
 *
 * A source is invalidated (and replaced by Paging) whenever MediaStore changes, so the order is
 * not recomputed on every page load.
 */
class MediaPagingSource(
    private val dataSource: MediaStoreDataSource,
    private val context: Context,
    private val bucketId: Long? = null,
    private val search: SearchCriteria? = null,
) : PagingSource<Int, MediaItem>() {

    private val generationAtCreation = dataSource.generation()
    private var cachedOrder: MediaOrder? = null

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
        val order = cachedOrder ?: dataSource.loadOrder(bucketId, search).also { cachedOrder = it }
        val total = order.size
        val window = when (params) {
            is LoadParams.Prepend -> PageWindow.backward(params.key, params.loadSize, total)
            else -> PageWindow.forward(params.key, params.loadSize, total)
        }
        val offset = window.start
        val pageIds = order.ids.copyOfRange(offset, window.end)
        val items = dataSource.loadByIds(pageIds, bucketId, search)

        // Something was deleted or changed since the order was read: show what is left and reload.
        if (items.size != pageIds.size) invalidate()

        LoadResult.Page(
            data = items,
            prevKey = window.prevKey,
            nextKey = window.nextKey(total),
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
