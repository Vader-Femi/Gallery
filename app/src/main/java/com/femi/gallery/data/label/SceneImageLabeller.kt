package com.femi.gallery.data.label

import android.content.Context
import com.femi.gallery.di.IoDispatcher
import com.femi.gallery.domain.label.SceneTagger
import com.femi.gallery.domain.repository.ImageLabeller
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SceneImageLabeller @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : ImageLabeller {

    override suspend fun label(mediaId: Long): List<String> = withContext(io) {
        emptyList()
    }
}
