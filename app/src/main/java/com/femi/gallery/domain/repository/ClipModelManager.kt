package com.femi.gallery.domain.repository

import com.femi.gallery.domain.clip.ClipModelKind
import com.femi.gallery.domain.clip.ClipModelStatus
import kotlinx.coroutines.flow.StateFlow

/** What happened when the person chose a model file. */
enum class ClipInstallResult {
    /** The file was copied in and works. */
    Installed,

    /** The file could not be read, or is not a CLIP model of the kind asked for. It was not kept. */
    Unusable,

    /** The file works but does not belong with the other model (their embeddings are not the same length). It was not kept. */
    Mismatch,
}

/** Installing and removing the model files that describe-a-photo needs. */
interface ClipModelManager {
    val status: StateFlow<ClipModelStatus>
    val isInitializing: StateFlow<Boolean>

    /** Copies the file at [source] (a content address chosen by the person) in as the [kind] model, and checks it. */
    suspend fun install(kind: ClipModelKind, source: String): ClipInstallResult

    /** Removes both models and the embeddings made with them. */
    suspend fun remove()
}
