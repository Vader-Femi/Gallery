package com.femi.gallery.di

import com.femi.gallery.data.locked.TinkContentCipher
import com.femi.gallery.domain.locked.ContentCipher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class LockedModule {
    @Binds
    abstract fun bindContentCipher(impl: TinkContentCipher): ContentCipher
}
