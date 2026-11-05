@file:Suppress("unused", "UNUSED_PARAMETER")
package com.femi.gallery.domain.repository

import com.femi.gallery.domain.model.WriteRequest

/** Stub for tools/verify's unit tests: the part of MediaRepository that the edit use cases call. */
interface MediaRepository {
    suspend fun createWriteRequests(uris: List<String>): List<WriteRequest>
}
