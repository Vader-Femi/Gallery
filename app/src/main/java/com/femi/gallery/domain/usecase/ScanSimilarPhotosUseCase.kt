package com.femi.gallery.domain.usecase

import com.femi.gallery.domain.repository.SimilarPhotoRepository
import com.femi.gallery.domain.repository.SimilarScanState
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ScanSimilarPhotosUseCase @Inject constructor(
    private val repository: SimilarPhotoRepository,
) {
    operator fun invoke(): Flow<SimilarScanState> = repository.scan()
}
