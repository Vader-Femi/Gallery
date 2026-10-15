package com.femi.gallery.domain.usecase

import com.femi.gallery.domain.repository.MediaRepository
import javax.inject.Inject

/** Reloads the media list if the device's media changed while we weren't looking. */
class RefreshMediaUseCase @Inject constructor(
    private val repository: MediaRepository,
) {
    operator fun invoke() = repository.refreshIfChanged()
}
