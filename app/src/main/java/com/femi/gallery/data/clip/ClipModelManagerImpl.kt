package com.femi.gallery.data.clip

import android.net.Uri
import com.femi.gallery.domain.clip.ClipModelKind
import com.femi.gallery.domain.clip.ClipModelStatus
import com.femi.gallery.domain.repository.ClipInstallResult
import com.femi.gallery.domain.repository.ClipModelManager
import com.femi.gallery.domain.repository.ClipStore
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Installs a model file and checks it before keeping it: it is opened and run once, so a wrong file is
 * refused when it is chosen, not when the first search fails; and when both models are there, their
 * embeddings must have the same length, or they could not be compared.
 */
@Singleton
class ClipModelManagerImpl @Inject constructor(
    private val files: ClipModelFiles,
    private val embedder: OnnxClipEmbedder,
    private val store: ClipStore,
    private val index: ClipIndexHolder,
) : ClipModelManager {

    override val status: StateFlow<ClipModelStatus> = files.status

    override suspend fun install(kind: ClipModelKind, source: String): ClipInstallResult {
        if (!files.import(kind, Uri.parse(source))) return ClipInstallResult.Unusable

        val own = embedder.check(kind)
        if (own !is OnnxClipEmbedder.Check.Ok) {
            files.delete(kind)
            return ClipInstallResult.Unusable
        }

        val otherKind = if (kind == ClipModelKind.Image) ClipModelKind.Text else ClipModelKind.Image
        if (files.file(otherKind).length() > 0) {
            val other = embedder.check(otherKind)
            if (other is OnnxClipEmbedder.Check.Ok && other.dimension != own.dimension) {
                files.delete(kind)
                return ClipInstallResult.Mismatch
            }
        }
        return ClipInstallResult.Installed
    }

    override suspend fun remove() {
        files.deleteAll()
        embedder.release()
        // Embeddings made with the removed models are of no use to anything else.
        store.save(emptyList())
        index.publish(emptyList())
        store.setModelSignature(0L)
    }
}
