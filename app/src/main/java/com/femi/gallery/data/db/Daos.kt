package com.femi.gallery.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

/*
 * One DAO per table. The scan tables all work the same way: read everything, then write the changes
 * (new and changed rows, deleted ids) in one transaction so a crash never leaves half of them. Deleting
 * is done in chunks because SQLite limits how many values one statement may carry.
 */

private const val DELETE_CHUNK = 500

@Dao
abstract class TextDao {
    @Query("SELECT * FROM scan_text")
    abstract suspend fun all(): List<TextRow>

    @Upsert
    abstract suspend fun upsert(rows: List<TextRow>)

    @Query("DELETE FROM scan_text WHERE id IN (:ids)")
    abstract suspend fun delete(ids: List<Long>)

    @Transaction
    open suspend fun apply(changed: List<TextRow>, removedIds: List<Long>) {
        upsert(changed)
        removedIds.chunked(DELETE_CHUNK).forEach { delete(it) }
    }
}

@Dao
abstract class LabelDao {
    @Query("SELECT * FROM scan_label")
    abstract suspend fun all(): List<LabelRow>

    @Upsert
    abstract suspend fun upsert(rows: List<LabelRow>)

    @Query("DELETE FROM scan_label WHERE id IN (:ids)")
    abstract suspend fun delete(ids: List<Long>)

    @Transaction
    open suspend fun apply(changed: List<LabelRow>, removedIds: List<Long>) {
        upsert(changed)
        removedIds.chunked(DELETE_CHUNK).forEach { delete(it) }
    }
}

@Dao
abstract class ColorDao {
    @Query("SELECT * FROM scan_color")
    abstract suspend fun all(): List<ColorRow>

    @Upsert
    abstract suspend fun upsert(rows: List<ColorRow>)

    @Query("DELETE FROM scan_color WHERE id IN (:ids)")
    abstract suspend fun delete(ids: List<Long>)

    @Transaction
    open suspend fun apply(changed: List<ColorRow>, removedIds: List<Long>) {
        upsert(changed)
        removedIds.chunked(DELETE_CHUNK).forEach { delete(it) }
    }
}

@Dao
abstract class LocationDao {
    @Query("SELECT * FROM scan_location")
    abstract suspend fun all(): List<LocationRow>

    @Upsert
    abstract suspend fun upsert(rows: List<LocationRow>)

    @Query("DELETE FROM scan_location WHERE id IN (:ids)")
    abstract suspend fun delete(ids: List<Long>)

    @Transaction
    open suspend fun apply(changed: List<LocationRow>, removedIds: List<Long>) {
        upsert(changed)
        removedIds.chunked(DELETE_CHUNK).forEach { delete(it) }
    }
}

@Dao
abstract class ClipDao {
    @Query("SELECT * FROM scan_clip")
    abstract suspend fun all(): List<ClipRow>

    @Upsert
    abstract suspend fun upsert(rows: List<ClipRow>)

    @Query("DELETE FROM scan_clip WHERE id IN (:ids)")
    abstract suspend fun delete(ids: List<Long>)

    @Query("DELETE FROM scan_clip")
    abstract suspend fun deleteAll()

    @Transaction
    open suspend fun apply(changed: List<ClipRow>, removedIds: List<Long>) {
        // Embeddings are 512 bytes each; a smaller batch keeps one statement's data small.
        changed.chunked(100).forEach { upsert(it) }
        removedIds.chunked(DELETE_CHUNK).forEach { delete(it) }
    }
}

@Dao
abstract class HashDao {
    @Query("SELECT * FROM file_hash WHERE id = :id")
    abstract suspend fun get(id: Long): HashRow?

    @Upsert
    abstract suspend fun upsert(row: HashRow)

    @Query("SELECT id FROM file_hash")
    abstract suspend fun allIds(): List<Long>

    @Query("DELETE FROM file_hash WHERE id IN (:ids)")
    abstract suspend fun delete(ids: List<Long>)

    @Transaction
    open suspend fun deleteAll(ids: List<Long>) {
        ids.chunked(DELETE_CHUNK).forEach { delete(it) }
    }
}

/** The id and date of an item: all that is needed to lay out the timeline. */
data class OrderRow(
    val id: Long,
    val dateMillis: Long,
)

/** One album as the database summarises it; the cover is the newest item. */
data class AlbumRow(
    val bucketId: Long,
    val bucketName: String,
    val relativePath: String,
    val itemCount: Int,
    val newestDate: Long,
    val coverUri: String,
)

@Dao
abstract class MediaDao {

    /**
     * Ids and dates of the matching items, newest first. A null [bucketId] or [type] means any; [favoritesOnly]
     * is 1 to keep only favorites. The order is the same as the one the app has always used: date, then id.
     */
    @Query(
        "SELECT id, dateMillis FROM media " +
            "WHERE (:bucketId IS NULL OR bucketId = :bucketId) " +
            "AND (:type IS NULL OR type = :type) " +
            "AND (:favoritesOnly = 0 OR isFavorite = 1) " +
            "ORDER BY dateMillis DESC, id DESC",
    )
    abstract suspend fun order(bucketId: Long?, type: Int?, favoritesOnly: Int): List<OrderRow>

    /** Every album, with how many items it holds and its newest item as the cover (the bare columns come from that row). */
    @Query(
        "SELECT bucketId, bucketName, relativePath, COUNT(*) AS itemCount, MAX(dateMillis) AS newestDate, uri AS coverUri " +
            "FROM media GROUP BY bucketId",
    )
    abstract suspend fun albums(): List<AlbumRow>

    @Query("SELECT * FROM media WHERE id IN (:ids)")
    abstract suspend fun byIds(ids: List<Long>): List<MediaRow>

    @Query("SELECT * FROM media")
    abstract suspend fun all(): List<MediaRow>

    @Query("SELECT id FROM media")
    abstract suspend fun allIds(): List<Long>

    @Query("SELECT COUNT(*) FROM media")
    abstract suspend fun count(): Int

    @Upsert
    abstract suspend fun upsert(rows: List<MediaRow>)

    @Query("DELETE FROM media WHERE id IN (:ids)")
    abstract suspend fun delete(ids: List<Long>)

    @Query("DELETE FROM media")
    abstract suspend fun deleteAll()

    @Query("SELECT value FROM index_meta WHERE `key` = :key")
    abstract suspend fun meta(key: String): Long?

    @Upsert
    abstract suspend fun putMeta(row: MetaRow)

    /** Replaces the whole mirror and remembers the generation it matches, all or nothing. */
    @Transaction
    open suspend fun replaceAll(rows: List<MediaRow>, generation: Long) {
        deleteAll()
        rows.chunked(WRITE_CHUNK).forEach { upsert(it) }
        putMeta(MetaRow(GENERATION, generation))
    }

    /** Brings the mirror up to date: new and changed rows written, gone rows deleted, and the generation remembered. */
    @Transaction
    open suspend fun apply(changed: List<MediaRow>, removedIds: List<Long>, generation: Long) {
        changed.chunked(WRITE_CHUNK).forEach { upsert(it) }
        removedIds.chunked(DELETE_CHUNK).forEach { delete(it) }
        putMeta(MetaRow(GENERATION, generation))
    }

    companion object {
        const val GENERATION = "mediastore_generation"
        private const val WRITE_CHUNK = 500
    }
}
