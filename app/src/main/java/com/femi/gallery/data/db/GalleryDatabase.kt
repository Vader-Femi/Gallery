package com.femi.gallery.data.db

import androidx.room.AutoMigration
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
    entities = [
        TextRow::class,
        LabelRow::class,
        ColorRow::class,
        LocationRow::class,
        HashRow::class,
        MediaRow::class,
        MetaRow::class,
        ClipRow::class,
        PhotoHashRow::class,
        PersonRow::class,
        FaceRow::class,
        FaceScanRow::class,
    ],
    version = 5,
    exportSchema = true,
    // Versions 2, 3, 4 and 5 only added tables (the library mirror; the photos' embeddings; the hashes for similar
    // photos; the face clusters and people), which Room can migrate by itself from the exported schemas, which is
    // why the schemas belong in version control.
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
    ],
)
abstract class GalleryDatabase : RoomDatabase() {
    abstract fun textDao(): TextDao
    abstract fun labelDao(): LabelDao
    abstract fun colorDao(): ColorDao
    abstract fun locationDao(): LocationDao
    abstract fun hashDao(): HashDao
    abstract fun mediaDao(): MediaDao
    abstract fun clipDao(): ClipDao
    abstract fun photoHashDao(): PhotoHashDao
    abstract fun personDao(): PersonDao
    abstract fun faceDao(): FaceDao
    abstract fun faceScanDao(): FaceScanDao

    companion object {
        const val FILE_NAME = "gallery.db"
    }
}
