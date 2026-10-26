package com.femi.gallery.di

import com.femi.gallery.data.edit.BitmapEditor
import com.femi.gallery.data.location.FileLocationStore
import com.femi.gallery.data.map.AssetMapDataRepository
import com.femi.gallery.data.repository.LocationRepositoryImpl
import com.femi.gallery.data.repository.MediaRepositoryImpl
import com.femi.gallery.data.repository.TextIndexRepositoryImpl
import com.femi.gallery.data.text.FileTextStore
import com.femi.gallery.data.text.MlKitTextRecognizer
import com.femi.gallery.domain.repository.EditRepository
import com.femi.gallery.domain.repository.LocationRepository
import com.femi.gallery.domain.repository.LocationStore
import com.femi.gallery.domain.repository.MapDataRepository
import com.femi.gallery.domain.repository.MediaRepository
import com.femi.gallery.domain.repository.TextIndexRepository
import com.femi.gallery.domain.repository.TextRecognizer
import com.femi.gallery.domain.repository.TextStore
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

    @Binds
    abstract fun bindTextStore(impl: FileTextStore): TextStore

    @Binds
    abstract fun bindTextRecognizer(impl: MlKitTextRecognizer): TextRecognizer

    @Binds
    abstract fun bindTextIndexRepository(impl: TextIndexRepositoryImpl): TextIndexRepository
}
