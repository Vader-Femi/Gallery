package com.femi.gallery.data.repository

import com.femi.gallery.data.mediastore.MediaStoreDataSource
import com.femi.gallery.di.IoDispatcher
import com.femi.gallery.domain.duplicates.DuplicateFinder
import com.femi.gallery.domain.duplicates.FileHasher
import com.femi.gallery.domain.repository.DuplicateRepository
import com.femi.gallery.domain.repository.DuplicateScanState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DuplicateRepositoryImpl @Inject constructor(
    private val dataSource: MediaStoreDataSource,
    private val hasher: FileHasher,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : DuplicateRepository {

    override fun scan(): Flow<DuplicateScanState> = channelFlow {
        send(DuplicateScanState.Listing)
        val items = dataSource.loadAllItems()
        val groups = withContext(io) {
            DuplicateFinder(hasher).find(items) { trySend(DuplicateScanState.Comparing(it)) }
        }
        send(DuplicateScanState.Done(groups))
    }.conflate() // a slow screen skips old progress, never the final result
}
