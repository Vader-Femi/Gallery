package com.femi.gallery.domain.usecase

import com.femi.gallery.domain.repository.TextIndexRepository
import javax.inject.Inject

class GetPhotoTextUseCase @Inject constructor(
    private val repository: TextIndexRepository,
) {
    suspend operator fun invoke(mediaId: Long): String? = repository.getText(mediaId)
}
