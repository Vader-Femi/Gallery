package com.femi.gallery.data.locked

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.femi.gallery.domain.model.MediaItem
import com.femi.gallery.domain.model.PageWindow

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
        val window = when (params) {
            is LoadParams.Prepend -> PageWindow.backward(params.key, params.loadSize, all.size)
            else -> PageWindow.forward(params.key, params.loadSize, all.size)
        }
        val offset = window.start
        val end = window.end
        val page = if (all.isEmpty()) emptyList() else all.subList(offset, end)
        return LoadResult.Page(
            data = page,
            prevKey = window.prevKey,
            nextKey = window.nextKey(all.size),
            itemsBefore = offset,
            itemsAfter = maxOf(0, all.size - end),
        )
    }

    override fun getRefreshKey(state: PagingState<Int, MediaItem>): Int? =
        state.anchorPosition?.let { maxOf(0, it - state.config.initialLoadSize / 2) }
}
