package com.femi.gallery.data.clip

import com.femi.gallery.domain.repository.ImageEmbedder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stands in until a real image/text model is added. Describing photos in free text needs a CLIP-style
 * model (about 100 MB) that cannot be bundled or checked yet; until then this reports that it is
 * unavailable, so no scan runs and no search is polluted with made-up matches.
 */
@Singleton
class UnavailableImageEmbedder @Inject constructor() : ImageEmbedder {
    override val isAvailable: Boolean = false
    override suspend fun embed(mediaId: Long): FloatArray? = null
    override suspend fun embedText(text: String): FloatArray? = null
}
