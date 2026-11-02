package com.femi.gallery.domain.usecase

import com.femi.gallery.domain.video.TrimEvent
import com.femi.gallery.domain.video.TrimRange
import com.femi.gallery.domain.video.VideoTrimRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class TrimVideoUseCase @Inject constructor(private val repository: VideoTrimRepository) {
    operator fun invoke(uri: String, range: TrimRange): Flow<TrimEvent> = repository.trim(uri, range)
}

class VideoKeyFramesUseCase @Inject constructor(private val repository: VideoTrimRepository) {
    suspend operator fun invoke(uri: String): LongArray = repository.keyFrames(uri)
}
