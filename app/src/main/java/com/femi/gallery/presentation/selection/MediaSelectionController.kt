package com.femi.gallery.presentation.selection

import com.femi.gallery.domain.model.MediaRef
import com.femi.gallery.domain.model.WriteRequest
import com.femi.gallery.domain.usecase.GetAllMediaRefsUseCase
import com.femi.gallery.domain.model.SpecialAlbums
import com.femi.gallery.domain.usecase.RequestDeleteUseCase
import com.femi.gallery.domain.usecase.RequestFavoriteUseCase
import com.femi.gallery.domain.usecase.RequestTrashUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

enum class WriteKind { Trash, Favorite, Unfavorite, Restore, DeleteForever }

/** One user action that needs one or more system confirmation dialogs, in order. */
class WriteBatch(
    val kind: WriteKind,
    val itemCount: Int,
    val requests: List<WriteRequest>,
)

/**
 * Selection state plus the actions that can be applied to it (or to any list of items, which
 * is how the viewer uses it). Screens collect [batches] and launch the system dialogs.
 */
class MediaSelectionController(
    private val scope: CoroutineScope,
    private val bucketId: Long?,
    private val getAllRefs: GetAllMediaRefsUseCase,
    private val requestTrash: RequestTrashUseCase,
    private val requestFavorite: RequestFavoriteUseCase,
    private val requestDelete: RequestDeleteUseCase,
) {
    private val selection = SelectionManager()
    val state = selection.state

    private val channel = Channel<WriteBatch>(Channel.BUFFERED)
    val batches: Flow<WriteBatch> = channel.receiveAsFlow()

    fun begin(ref: MediaRef) = selection.begin(ref)

    fun toggle(ref: MediaRef) = selection.toggle(ref)

    fun clear() = selection.clear()

    fun selectAll() {
        scope.launch { selection.selectAll(getAllRefs(bucketId)) }
    }

    fun trashSelected() = trash(state.value.selected.values)

    fun restoreSelected() = restore(state.value.selected.values)

    fun deleteForeverSelected() = deleteForever(state.value.selected.values)

    fun toggleFavoriteSelected() = toggleFavorite(state.value.selected.values)

    fun trash(refs: Collection<MediaRef>) {
        if (refs.isEmpty()) return
        scope.launch {
            val requests = requestTrash(refs.map { it.uri }, true)
            channel.send(WriteBatch(WriteKind.Trash, refs.size, requests))
        }
    }

    /** Takes items out of the trash. */
    fun restore(refs: Collection<MediaRef>) {
        if (refs.isEmpty()) return
        scope.launch {
            val requests = requestTrash(refs.map { it.uri }, false)
            channel.send(WriteBatch(WriteKind.Restore, refs.size, requests))
        }
    }

    /** Permanently deletes items; cannot be undone. */
    fun deleteForever(refs: Collection<MediaRef>) {
        if (refs.isEmpty()) return
        scope.launch {
            val requests = requestDelete(refs.map { it.uri })
            channel.send(WriteBatch(WriteKind.DeleteForever, refs.size, requests))
        }
    }

    /** Permanently deletes everything currently in the recycle bin. */
    fun emptyBin() {
        scope.launch { deleteForever(getAllRefs(SpecialAlbums.TRASH)) }
    }

    /** Favorites everything unless it's all favorites already, in which case it unfavorites. */
    fun toggleFavorite(refs: Collection<MediaRef>) {
        if (refs.isEmpty()) return
        val makeFavorite = refs.any { !it.isFavorite }
        scope.launch {
            val requests = requestFavorite(refs.map { it.uri }, makeFavorite)
            val kind = if (makeFavorite) WriteKind.Favorite else WriteKind.Unfavorite
            channel.send(WriteBatch(kind, refs.size, requests))
        }
    }
}
