package com.femi.gallery.di

import com.femi.gallery.data.repository.MediaRepositoryImpl
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
}
