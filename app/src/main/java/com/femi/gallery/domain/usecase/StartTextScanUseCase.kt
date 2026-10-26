package com.femi.gallery.domain.usecase

import com.femi.gallery.domain.repository.TextIndexRepository
import javax.inject.Inject

/** Reads the text in new photos, in the background. Cheap to call whenever the app opens. */
class StartTextScanUseCase @Inject constructor(
    private val repository: TextIndexRepository,
) {
    operator fun invoke() = repository.startScan()
}
