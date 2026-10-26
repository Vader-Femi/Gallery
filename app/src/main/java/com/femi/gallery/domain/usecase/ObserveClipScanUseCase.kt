package com.femi.gallery.domain.usecase

import com.femi.gallery.domain.clip.ClipScanState
import com.femi.gallery.domain.repository.ClipIndexRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class ObserveClipScanUseCase @Inject constructor(
    private val repository: ClipIndexRepository,
) {
    operator fun invoke(): StateFlow<ClipScanState> = repository.scan
}
