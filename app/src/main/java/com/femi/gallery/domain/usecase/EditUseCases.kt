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

class ReplaceOriginalUseCase @Inject constructor(
    private val repository: EditRepository,
) {
    suspend operator fun invoke(uri: String, state: EditState): Boolean = repository.replaceOriginal(uri, state)
}

class RequestWriteOriginalUseCase @Inject constructor(
    private val repository: com.femi.gallery.domain.repository.MediaRepository,
) {
    suspend operator fun invoke(uri: String): List<com.femi.gallery.domain.model.WriteRequest> = repository.createWriteRequests(listOf(uri))
}

class ApplyEditEffectsUseCase @Inject constructor(
    private val repository: EditRepository,
) {
    suspend operator fun invoke(bitmap: Bitmap, state: EditState): Bitmap = repository.applyEffects(bitmap, state)
}
