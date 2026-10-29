package com.femi.gallery.data.db

import androidx.room.Entity
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
