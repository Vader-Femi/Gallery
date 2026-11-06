package com.femi.gallery.domain.usecase

import com.femi.gallery.domain.repository.FaceIndexRepository
import javax.inject.Inject

/** Starts looking for faces in photos that were not looked at yet (nothing happens when face recognition is off). */
class StartFaceScanUseCase @Inject constructor(private val repository: FaceIndexRepository) {
    operator fun invoke() = repository.startScan()
}
