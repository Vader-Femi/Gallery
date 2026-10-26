package com.femi.gallery.domain.usecase

import com.femi.gallery.domain.label.LabelScanState
import com.femi.gallery.domain.repository.LabelIndexRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class ObserveLabelScanUseCase @Inject constructor(
    private val repository: LabelIndexRepository,
) {
    operator fun invoke(): StateFlow<LabelScanState> = repository.scan
}
