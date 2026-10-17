package com.femi.gallery.data.locked

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.femi.gallery.domain.model.MediaItem

/** Pages through the Locked folder; it is small and in memory, so slicing the list is enough. */
class LockedPagingSource(
    private val store: LockedStore,
) : PagingSource<Int, MediaItem>() {

    private val listener: () -> Unit = { invalidate() }

    init {
        store.addListener(listener)
        registerInvalidatedCallback { store.removeListener(listener) }
    }

    override val jumpingSupported: Boolean = true

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaItem> {
        val all = store.list()
        val offset = (params.key ?: 0).coerceIn(0, maxOf(0, all.size - 1))
        val end = minOf(all.size, offset + params.loadSize)
        val page = if (all.isEmpty()) emptyList() else all.subList(offset, end)
        return LoadResult.Page(
            data = page,
            prevKey = if (offset == 0) null else maxOf(0, offset - params.loadSize),
            nextKey = if (end >= all.size) null else end,
            itemsBefore = offset,
            itemsAfter = maxOf(0, all.size - end),
        )
    }

    override fun getRefreshKey(state: PagingState<Int, MediaItem>): Int? =
        state.anchorPosition?.let { maxOf(0, it - state.config.initialLoadSize / 2) }
}
