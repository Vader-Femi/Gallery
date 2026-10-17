package com.femi.gallery.presentation.selection

import com.femi.gallery.domain.model.MediaRef
import com.femi.gallery.domain.model.SpecialAlbums
import com.femi.gallery.domain.model.WriteLimits
import com.femi.gallery.domain.model.WriteRequest
import com.femi.gallery.domain.search.SearchCriteria
import com.femi.gallery.domain.usecase.DeleteLockedUseCase
import com.femi.gallery.domain.usecase.GetAllMediaRefsUseCase
import com.femi.gallery.domain.usecase.LockMediaUseCase
import com.femi.gallery.domain.usecase.RequestDeleteUseCase
import com.femi.gallery.domain.usecase.RequestFavoriteUseCase
import com.femi.gallery.domain.usecase.RequestTrashUseCase
import com.femi.gallery.domain.usecase.UnlockMediaUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class WriteKind {
    Trash, Favorite, Unfavorite, Restore, DeleteForever,
    /** Moved into the Locked folder (originals deleted through the system dialogs). */
    Lock,
    /** Moved out of the Locked folder back into the gallery. */
    Unlock,
    /** Deleted from the Locked folder. */
    DeleteLocked,
    /** Something went wrong before any dialog could be shown. */
    Failed,
}

/**
 * One user action: the system confirmation dialogs to show in order (possibly none, for work
 * that needs no confirmation) and what to do if the user declines one of them.
 */
class WriteBatch(
    val kind: WriteKind,
    val itemCount: Int,
    val requests: List<WriteRequest>,
    /** Called with the index of the declined dialog, e.g. to undo a half-finished lock. */
    val onDeclined: ((requestIndex: Int) -> Unit)? = null,
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
    private val lockMedia: LockMediaUseCase,
    private val unlockMedia: UnlockMediaUseCase,
    private val deleteLockedMedia: DeleteLockedUseCase,
    /** The active search, so "Select all" only selects matching items. */
    private val currentSearch: () -> SearchCriteria? = { null },
) {
    private val selection = SelectionManager()
    val state = selection.state

    private val channel = Channel<WriteBatch>(Channel.BUFFERED)
    val batches: Flow<WriteBatch> = channel.receiveAsFlow()

    fun begin(ref: MediaRef) = selection.begin(ref)

    fun toggle(ref: MediaRef) = selection.toggle(ref)

    fun clear() = selection.clear()

    fun selectAll() {
        scope.launch { selection.selectAll(getAllRefs(bucketId, currentSearch())) }
    }

    fun trashSelected() = trash(state.value.selected.values)

    fun restoreSelected() = restore(state.value.selected.values)

    fun deleteForeverSelected() = deleteForever(state.value.selected.values)

    fun lockSelected() = lock(state.value.selected.values)

    fun unlockSelected() = unlock(state.value.selected.values)

    fun deleteLockedSelected() = deleteLocked(state.value.selected.values)

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

    /**
     * Moves items into the Locked folder: copies them to private storage first, then asks the
     * system to delete the originals. If the user declines, the copies that no longer have a
     * deleted original are removed again.
     */
    fun lock(refs: Collection<MediaRef>) {
        if (refs.isEmpty()) return
        scope.launch {
            val prepared = lockMedia(refs.map { it.uri })
            if (prepared.lockedIds.isEmpty()) {
                channel.send(WriteBatch(WriteKind.Failed, refs.size, emptyList()))
                return@launch
            }
            val batch = WriteBatch(
                kind = WriteKind.Lock,
                itemCount = prepared.lockedIds.size,
                requests = prepared.requests,
                onDeclined = { declinedIndex ->
                    // Earlier dialogs were approved, so those originals are gone and their copies must stay.
                    val orphanedCopies = prepared.lockedIds.drop(declinedIndex * WriteLimits.BATCH_SIZE)
                    scope.launch { deleteLockedMedia(orphanedCopies) }
                },
            )
            channel.send(batch)
        }
    }

    /** Moves items from the Locked folder back into the gallery. */
    fun unlock(refs: Collection<MediaRef>) {
        if (refs.isEmpty()) return
        scope.launch {
            val restored = unlockMedia(refs.map { it.id })
            val kind = if (restored > 0) WriteKind.Unlock else WriteKind.Failed
            channel.send(WriteBatch(kind, restored, emptyList()))
        }
    }

    /** Permanently deletes items from the Locked folder. */
    fun deleteLocked(refs: Collection<MediaRef>) {
        if (refs.isEmpty()) return
        scope.launch {
            deleteLockedMedia(refs.map { it.id })
            channel.send(WriteBatch(WriteKind.DeleteLocked, refs.size, emptyList()))
        }
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

/** Builds controllers with all their use cases, so ViewModels only inject this one class. */
class MediaSelectionControllerFactory @Inject constructor(
    private val getAllRefs: GetAllMediaRefsUseCase,
    private val requestTrash: RequestTrashUseCase,
    private val requestFavorite: RequestFavoriteUseCase,
    private val requestDelete: RequestDeleteUseCase,
    private val lockMedia: LockMediaUseCase,
    private val unlockMedia: UnlockMediaUseCase,
    private val deleteLocked: DeleteLockedUseCase,
) {
    fun create(
        scope: CoroutineScope,
        bucketId: Long?,
        currentSearch: () -> SearchCriteria? = { null },
    ) = MediaSelectionController(
        scope = scope,
        bucketId = bucketId,
        getAllRefs = getAllRefs,
        requestTrash = requestTrash,
        requestFavorite = requestFavorite,
        requestDelete = requestDelete,
        lockMedia = lockMedia,
        unlockMedia = unlockMedia,
        deleteLockedMedia = deleteLocked,
        currentSearch = currentSearch,
    )
}
