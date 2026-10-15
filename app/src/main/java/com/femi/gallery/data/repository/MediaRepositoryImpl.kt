package com.femi.gallery.data.repository

import android.content.Context
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.femi.gallery.data.mediastore.MediaPagingSource
import com.femi.gallery.data.mediastore.MediaStoreDataSource
import com.femi.gallery.domain.model.MediaItem
import com.femi.gallery.domain.repository.MediaRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dataSource: MediaStoreDataSource,
) : MediaRepository {

    @Volatile
    private var currentSource: MediaPagingSource? = null

    override fun refreshIfChanged() {
        currentSource?.invalidateIfStale()
    }

    override fun getPagedMedia(): Flow<PagingData<MediaItem>> = Pager(
        config = PagingConfig(
            pageSize = 120,
            initialLoadSize = 240,
            prefetchDistance = 120,
            enablePlaceholders = true,
            jumpThreshold = 240,
        ),
        pagingSourceFactory = {
            MediaPagingSource(dataSource, context).also { currentSource = it }
        },
    ).flow
}
