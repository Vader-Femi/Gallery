package com.femi.gallery.domain.repository

import com.femi.gallery.domain.face.FaceScanState
import kotlinx.coroutines.flow.StateFlow

interface FaceIndexRepository {
    val scan: StateFlow<FaceScanState>
    val isRunning: Boolean
    fun startScan()
    fun stopScan()

    /** Stops the scan and deletes everything learned about faces: the people, their faces and what was scanned. */
    suspend fun eraseAll()

    /** Merges all faces from [absorbedId] into [survivorId], updates centroids and counts, and deletes [absorbedId]. */
    suspend fun mergePeople(survivorId: Long, absorbedId: Long, newName: String? = null)

    /** Finds an existing person with [name], case-insensitively, excluding [excludeId]. */
    suspend fun findExistingPerson(name: String, excludeId: Long): com.femi.gallery.data.db.PersonRow?
}
