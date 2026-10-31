package com.femi.gallery.di

import android.content.Context
import androidx.room.Room
import com.femi.gallery.data.db.ClipDao
import com.femi.gallery.data.db.ColorDao
import com.femi.gallery.data.db.GalleryDatabase
import com.femi.gallery.data.db.HashDao
import com.femi.gallery.data.db.LabelDao
import com.femi.gallery.data.db.LocationDao
import com.femi.gallery.data.db.MediaDao
import com.femi.gallery.data.db.TextDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): GalleryDatabase =
        Room.databaseBuilder(context, GalleryDatabase::class.java, GalleryDatabase.FILE_NAME).build()

    @Provides
    fun provideTextDao(database: GalleryDatabase): TextDao = database.textDao()

    @Provides
    fun provideLabelDao(database: GalleryDatabase): LabelDao = database.labelDao()

    @Provides
    fun provideColorDao(database: GalleryDatabase): ColorDao = database.colorDao()

    @Provides
    fun provideLocationDao(database: GalleryDatabase): LocationDao = database.locationDao()

    @Provides
    fun provideHashDao(database: GalleryDatabase): HashDao = database.hashDao()

    @Provides
    fun provideMediaDao(database: GalleryDatabase): MediaDao = database.mediaDao()

    @Provides
    fun provideClipDao(database: GalleryDatabase): ClipDao = database.clipDao()
}
