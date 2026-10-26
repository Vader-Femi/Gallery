package com.femi.gallery.domain.usecase

import com.femi.gallery.domain.repository.ClipIndexRepository
import javax.inject.Inject

class StartClipScanUseCase @Inject constructor(
    private val repository: ClipIndexRepository,
) {
    operator fun invoke() = repository.startScan()
}
