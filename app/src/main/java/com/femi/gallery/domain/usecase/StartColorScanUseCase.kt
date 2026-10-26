package com.femi.gallery.domain.usecase

import com.femi.gallery.domain.repository.ColorIndexRepository
import javax.inject.Inject

class StartColorScanUseCase @Inject constructor(
    private val repository: ColorIndexRepository,
) {
    operator fun invoke() = repository.startScan()
}
