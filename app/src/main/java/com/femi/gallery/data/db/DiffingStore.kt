package com.femi.gallery.data.db

import android.content.Context
import android.util.Log
import com.femi.gallery.domain.store.RowDiff
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The behaviour all scan stores share. A scan hands over its whole list at every checkpoint; this keeps
 * a copy of what the database holds and writes only the difference, in one transaction (see [RowDiff]).
 *
 * The first time the database is empty and the file the app used before Room is still there, the file is
 * imported and deleted, so nobody has to scan their library again.
 */
abstract class DiffingStore<T>(
    private val context: Context,
    private val io: CoroutineDispatcher,
    private val legacyFileName: String,
    private val decodeLegacy: (ByteArray) -> List<T>?,
) {
    private val lock = Mutex()
    private var saved: Map<Long, T>? = null

    protected abstract fun idOf(entry: T): Long
    protected abstract suspend fun readAll(): List<T>
    protected abstract suspend fun write(changed: List<T>, removedIds: List<Long>)

    protected suspend fun loadAll(): List<T> = lock.withLock {
        withContext(io) {
            var rows = readAll()
            if (rows.isEmpty()) rows = importLegacy() ?: rows
            saved = rows.associateBy { idOf(it) }
            rows
        }
    }

    protected suspend fun saveAll(entries: List<T>) {
        lock.withLock {
            withContext(io) {
                val before = saved ?: readAll().associateBy { idOf(it) }
                val changes = RowDiff.between(before, entries) { idOf(it) }
                if (!changes.isEmpty) write(changes.changed, changes.removedIds)
                saved = entries.associateBy { idOf(it) }
            }
        }
    }

    /** The entries of the old file, written to the database; the file is deleted once they are safe there. */
    private suspend fun importLegacy(): List<T>? {
        val file = File(context.filesDir, legacyFileName)
        if (!file.exists()) return null
        val entries = try {
            decodeLegacy(file.readBytes())
        } catch (e: java.io.IOException) {
            Log.w(TAG, "Could not read $legacyFileName", e)
            null
        }
        if (entries == null) {
            file.delete() // damaged: the scan will make it again
            return null
        }
        write(entries, emptyList())
        file.delete()
        Log.i(TAG, "Moved ${entries.size} entries from $legacyFileName into the database")
        return entries
    }

    private companion object {
        const val TAG = "IndexDb"
    }
}
