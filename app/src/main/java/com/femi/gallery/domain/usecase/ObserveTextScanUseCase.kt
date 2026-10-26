package com.femi.gallery.domain.usecase

import com.femi.gallery.domain.repository.TextIndexRepository
import com.femi.gallery.domain.text.TextScanState
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class ObserveTextScanUseCase @Inject constructor(
    private val repository: TextIndexRepository,
) {
    operator fun invoke(): StateFlow<TextScanState> = repository.scan
}
