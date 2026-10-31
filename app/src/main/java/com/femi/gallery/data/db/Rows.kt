package com.femi.gallery.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * The tables of the index. Every row is about one library item, named by its MediaStore id, and
 * carries the item's last-modified time ("modified") from when it was scanned, so an item that was
 * edited since is scanned again. Nothing here cannot be rebuilt by scanning the library again.
 */

/** Text read from a photo (OCR). */
@Entity(tableName = "scan_text")
data class TextRow(
    @PrimaryKey val id: Long,
    val modified: Long,
    val text: String,
)

/** Object and scene labels of a photo, separated by spaces. */
@Entity(tableName = "scan_label")
data class LabelRow(
    @PrimaryKey val id: Long,
    val modified: Long,
    val labels: String,
)

/** The colours of a photo, separated by spaces. */
@Entity(tableName = "scan_color")
data class ColorRow(
    @PrimaryKey val id: Long,
    val modified: Long,
    val colors: String,
)

/** Where a photo was taken; both numbers are null for a photo without a location (it was looked at, and has none). */
@Entity(tableName = "scan_location")
data class LocationRow(
    @PrimaryKey val id: Long,
    val modified: Long,
    val latitude: Double?,
    val longitude: Double?,
)

/**
 * Fingerprints of a file's content for the duplicate search. They are only valid while the file still
 * has the [size] and [dateAdded] it had when they were made.
 */
@Entity(tableName = "file_hash")
data class HashRow(
    @PrimaryKey val id: Long,
    val size: Long,
    val dateAdded: Long,
    val partialHash: String?,
    val fullHash: String?,
)

/**
 * One photo or video of the library, as MediaStore described it when it was last copied. Together the rows
 * are a local copy of the library (the "mirror"), which can be sorted, grouped and filtered by SQLite
 * instead of being read from MediaStore row by row every time the app starts.
 */
@Entity(
    tableName = "media",
    indices = [
        // The order everything is shown in: newest first, the higher id first among equals.
        Index(value = ["dateMillis", "id"]),
        Index(value = ["bucketId"]),
    ],
)
data class MediaRow(
    @PrimaryKey val id: Long,
    val uri: String,
    /** 0 for a photo, 1 for a video. */
    val type: Int,
    val displayName: String,
    val mimeType: String,
    /** The date it is shown under: when it was taken, else when it was added. */
    val dateMillis: Long,
    val dateAddedMillis: Long,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val durationMillis: Long,
    val bucketId: Long,
    val bucketName: String,
    val relativePath: String,
    val isFavorite: Boolean,
)

/** Small facts about the index itself, such as the MediaStore generation the mirror was last brought up to date at. */
@Entity(tableName = "index_meta")
data class MetaRow(
    @PrimaryKey val key: String,
    val value: Long,
)

/** What the image model made of a photo: one byte per number, see `ClipQuantizer`. Empty means the photo could not be read. */
@Entity(tableName = "scan_clip")
data class ClipRow(
    @PrimaryKey val id: Long,
    val modified: Long,
    val embedding: ByteArray,
)
