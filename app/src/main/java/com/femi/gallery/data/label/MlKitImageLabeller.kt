package com.femi.gallery.data.label

import android.content.Context
import com.femi.gallery.di.IoDispatcher
import com.femi.gallery.domain.repository.ImageLabeller
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MlKitImageLabellerStub @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : ImageLabeller {
    override suspend fun label(mediaId: Long): List<String> = emptyList()
}
