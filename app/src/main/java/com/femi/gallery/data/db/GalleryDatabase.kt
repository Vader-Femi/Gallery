package com.femi.gallery.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The index: everything the app works out about the library that is slow to work out again (text in
 * photos, labels, colours, locations, file fingerprints). It is a cache of facts about the photos, never
 * the only copy of anything the person made, and it stays on the phone (see the backup rules).
 *
 * Changing a table later means raising [version] and adding a migration; the schema of each version is
 * written to app/schemas and belongs in version control.
 */
@Database(
    entities = [TextRow::class, LabelRow::class, ColorRow::class, LocationRow::class, HashRow::class],
    version = 1,
    exportSchema = true,
)
abstract class GalleryDatabase : RoomDatabase() {
    abstract fun textDao(): TextDao
    abstract fun labelDao(): LabelDao
    abstract fun colorDao(): ColorDao
    abstract fun locationDao(): LocationDao
    abstract fun hashDao(): HashDao

    companion object {
        const val FILE_NAME = "gallery.db"
    }
}
