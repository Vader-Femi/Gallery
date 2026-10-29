package com.femi.gallery.domain.usecase

import com.femi.gallery.domain.repository.DuplicateRepository
import com.femi.gallery.domain.repository.DuplicateScanState
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ScanDuplicatesUseCase @Inject constructor(
    private val repository: DuplicateRepository,
) {
    operator fun invoke(): Flow<DuplicateScanState> = repository.scan()
}
