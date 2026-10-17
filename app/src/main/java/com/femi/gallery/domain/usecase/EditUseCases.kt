package com.femi.gallery.domain.usecase

import android.graphics.Bitmap
import com.femi.gallery.domain.edit.EditState
import com.femi.gallery.domain.repository.EditRepository
import javax.inject.Inject

class LoadEditPreviewUseCase @Inject constructor(
    private val repository: EditRepository,
) {
    suspend operator fun invoke(uri: String, maxSize: Int): Bitmap? = repository.loadPreview(uri, maxSize)
}

class ApplyEditGeometryUseCase @Inject constructor(
    private val repository: EditRepository,
) {
    suspend operator fun invoke(bitmap: Bitmap, state: EditState, includeCrop: Boolean): Bitmap =
        repository.applyGeometry(bitmap, state, includeCrop)
}

class SaveEditedCopyUseCase @Inject constructor(
    private val repository: EditRepository,
) {
    suspend operator fun invoke(uri: String, state: EditState): Boolean = repository.saveCopy(uri, state)
}
