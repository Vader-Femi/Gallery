package com.femi.gallery.domain.usecase

import com.femi.gallery.domain.color.ColorScanState
import com.femi.gallery.domain.repository.ColorIndexRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class ObserveColorScanUseCase @Inject constructor(
    private val repository: ColorIndexRepository,
) {
    operator fun invoke(): StateFlow<ColorScanState> = repository.scan
}
