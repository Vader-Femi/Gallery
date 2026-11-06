package com.femi.gallery.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

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

    @Query("SELECT value FROM index_meta WHERE `key` = :key")
    abstract suspend fun meta(key: String): Long?

    @Upsert
    abstract suspend fun putMeta(row: MetaRow)

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

@Dao
abstract class PhotoHashDao {
    @Query("SELECT * FROM photo_hash")
    abstract suspend fun all(): List<PhotoHashRow>

    @Upsert
    abstract suspend fun upsertAll(rows: List<PhotoHashRow>)

    @Query("SELECT id FROM photo_hash")
    abstract suspend fun allIds(): List<Long>

    @Query("DELETE FROM photo_hash WHERE id IN (:ids)")
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
    val type: Int,
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
        "SELECT id, dateMillis, type FROM media " +
            "WHERE (:bucketId IS NULL OR bucketId = :bucketId) " +
            "AND (:type IS NULL OR type = :type) " +
            "AND (:favoritesOnly = 0 OR isFavorite = 1) " +
            "AND bucketId NOT IN (:excluded) " +
            "ORDER BY dateMillis DESC, id DESC",
    )
    abstract suspend fun order(bucketId: Long?, type: Int?, favoritesOnly: Int, excluded: List<Long>): List<OrderRow>

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

    @Query("""
        SELECT id FROM media 
        WHERE isFavorite = 0 AND (
            LOWER(relativePath) LIKE '%screenshot%' 
            OR LOWER(bucketName) LIKE '%screenshot%' 
            OR LOWER(displayName) LIKE 'screenshot%'
            OR LOWER(displayName) LIKE 'screen_%'
            OR LOWER(relativePath) LIKE '%whatsapp stickers%'
            OR LOWER(relativePath) LIKE '%screen recording%'
            OR LOWER(displayName) LIKE 'screenrecording%'
            OR LOWER(displayName) LIKE 'screen_recording%'
            OR LOWER(relativePath) LIKE '%receipt%'
            OR LOWER(displayName) LIKE 'receipt%'
        )
    """)
    abstract suspend fun junkIds(): List<Long>

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

@Dao
abstract class PersonDao {
    @Query("""
        SELECT * FROM person 
        ORDER BY 
            CASE WHEN name GLOB '[Pp]erson [0-9]*' THEN 1 ELSE 0 END ASC, 
            photoCount DESC, 
            name ASC
    """)
    abstract fun flowAll(): Flow<List<PersonRow>>

    @Query("""
        SELECT * FROM person 
        ORDER BY 
            CASE WHEN name GLOB '[Pp]erson [0-9]*' THEN 1 ELSE 0 END ASC, 
            photoCount DESC, 
            name ASC
    """)
    abstract suspend fun all(): List<PersonRow>

    @Query("SELECT * FROM person WHERE id = :id")
    abstract suspend fun get(id: Long): PersonRow?

    @Query("SELECT * FROM person WHERE id = :id")
    abstract fun flow(id: Long): Flow<PersonRow?>

    @Insert
    abstract suspend fun insert(person: PersonRow): Long

    @Update
    abstract suspend fun update(person: PersonRow)

    @Query("UPDATE person SET name = :name WHERE id = :id")
    abstract suspend fun updateName(id: Long, name: String)

    @Query("UPDATE person SET photoCount = :count WHERE id = :id")
    abstract suspend fun updatePhotoCount(id: Long, count: Int)

    @Query("DELETE FROM person WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("DELETE FROM person")
    abstract suspend fun deleteAll()

    @Query("SELECT * FROM person WHERE LOWER(name) LIKE '%' || LOWER(:query) || '%'")
    abstract suspend fun searchByName(query: String): List<PersonRow>

    @Query("""
        SELECT DISTINCT f.mediaId FROM face f 
        INNER JOIN person p ON f.personId = p.id 
        WHERE LOWER(p.name) LIKE '%' || LOWER(:query) || '%'
    """)
    abstract suspend fun mediaIdsForName(query: String): List<Long>
}

@Dao
abstract class FaceDao {
    @Query("SELECT * FROM face")
    abstract suspend fun all(): List<FaceRow>

    @Query("SELECT * FROM face WHERE mediaId = :mediaId")
    abstract suspend fun getForMedia(mediaId: Long): List<FaceRow>

    @Query("SELECT * FROM face WHERE personId = :personId")
    abstract suspend fun getForPerson(personId: Long): List<FaceRow>

    @Query("SELECT DISTINCT mediaId FROM face WHERE personId = :personId")
    abstract suspend fun mediaIdsForPerson(personId: Long): List<Long>

    @Query("SELECT DISTINCT mediaId FROM face WHERE personId = :personId")
    abstract fun flowMediaIdsForPerson(personId: Long): Flow<List<Long>>

    @Insert
    abstract suspend fun insert(faces: List<FaceRow>)

    @Query("DELETE FROM face WHERE mediaId IN (:mediaIds)")
    abstract suspend fun deleteForMedia(mediaIds: List<Long>)

    @Query("DELETE FROM face WHERE personId = :personId")
    abstract suspend fun deleteForPerson(personId: Long)

    @Query("SELECT COUNT(DISTINCT mediaId) FROM face WHERE personId = :personId")
    abstract suspend fun countPhotosForPerson(personId: Long): Int

    /** The photos of each person that are not in one of the [hidden] albums. */
    @Query(
        "SELECT personId, COUNT(DISTINCT mediaId) AS photos FROM face " +
            "WHERE mediaId NOT IN (SELECT id FROM media WHERE bucketId IN (:hidden)) GROUP BY personId",
    )
    abstract suspend fun visibleCounts(hidden: List<Long>): List<PersonPhotoCount>

    /** Which of the photos [mediaIds] are in one of the [hidden] albums. */
    @Query("SELECT id FROM media WHERE id IN (:mediaIds) AND bucketId IN (:hidden)")
    abstract suspend fun hiddenAmong(mediaIds: List<Long>, hidden: List<Long>): List<Long>

    /** A face of the person that is not in a hidden album, to show as the cover. */
    @Query(
        "SELECT mediaId, bounds FROM face WHERE personId = :personId " +
            "AND mediaId NOT IN (SELECT id FROM media WHERE bucketId IN (:hidden)) LIMIT 1",
    )
    abstract suspend fun visibleCover(personId: Long, hidden: List<Long>): FaceCover?

    @Query("DELETE FROM face")
    abstract suspend fun deleteAll()
}

@Dao
abstract class FaceScanDao {
    @Query("SELECT * FROM scan_face")
    abstract suspend fun all(): List<FaceScanRow>

    @Upsert
    abstract suspend fun upsert(rows: List<FaceScanRow>)

    @Query("DELETE FROM scan_face WHERE id IN (:ids)")
    abstract suspend fun delete(ids: List<Long>)

    @Query("DELETE FROM scan_face")
    abstract suspend fun deleteAll()

    @Transaction
    open suspend fun apply(changed: List<FaceScanRow>, removedIds: List<Long>) {
        upsert(changed)
        removedIds.chunked(DELETE_CHUNK).forEach { delete(it) }
    }
}

@Dao
abstract class StoryDao {
    @Query("SELECT * FROM stories ORDER BY toDay DESC, fromDay DESC")
    abstract fun flowAll(): kotlinx.coroutines.flow.Flow<List<StoryRow>>

    @Query("SELECT * FROM stories ORDER BY toDay DESC, fromDay DESC")
    abstract suspend fun all(): List<StoryRow>

    @Upsert
    abstract suspend fun upsertAll(stories: List<StoryRow>)

    @Query("DELETE FROM stories WHERE id NOT IN (:currentIds)")
    abstract suspend fun deleteOld(currentIds: List<String>)

    @Query("DELETE FROM stories")
    abstract suspend fun deleteAll()

    @Transaction
    open suspend fun replaceAll(stories: List<StoryRow>) {
        if (stories.isEmpty()) {
            deleteAll()
        } else {
            upsertAll(stories)
            deleteOld(stories.map { it.id })
        }
    }

    @Query("SELECT value FROM index_meta WHERE `key` = :key")
    abstract suspend fun meta(key: String): Long?

    @Upsert
    abstract suspend fun putMeta(row: MetaRow)

    companion object {
        const val STORIES_GENERATION = "stories_generation"
    }
}
