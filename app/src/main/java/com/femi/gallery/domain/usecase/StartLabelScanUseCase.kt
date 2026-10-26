package com.femi.gallery.domain.usecase

import com.femi.gallery.domain.repository.LabelIndexRepository
import javax.inject.Inject

class StartLabelScanUseCase @Inject constructor(
    private val repository: LabelIndexRepository,
) {
    operator fun invoke() = repository.startScan()
}
