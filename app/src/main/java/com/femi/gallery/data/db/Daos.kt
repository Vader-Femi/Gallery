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
