package com.femi.gallery.di

import com.femi.gallery.data.edit.BitmapEditor
import com.femi.gallery.data.location.FileLocationStore
import com.femi.gallery.data.map.AssetMapDataRepository
import com.femi.gallery.data.repository.LocationRepositoryImpl
import com.femi.gallery.data.repository.MediaRepositoryImpl
import com.femi.gallery.domain.repository.EditRepository
import com.femi.gallery.domain.repository.LocationRepository
import com.femi.gallery.domain.repository.LocationStore
import com.femi.gallery.domain.repository.MapDataRepository
import com.femi.gallery.domain.repository.MediaRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindMediaRepository(impl: MediaRepositoryImpl): MediaRepository

    @Binds
    abstract fun bindEditRepository(impl: BitmapEditor): EditRepository

    @Binds
    abstract fun bindLocationRepository(impl: LocationRepositoryImpl): LocationRepository

    @Binds
    abstract fun bindLocationStore(impl: FileLocationStore): LocationStore

    @Binds
    abstract fun bindMapDataRepository(impl: AssetMapDataRepository): MapDataRepository
}
