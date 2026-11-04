package com.femi.gallery.domain.repository

import com.femi.gallery.domain.face.DetectedFace

interface FaceDetector {
    /** Detects faces in the given media item, returning their normalized bounding boxes and feature descriptors. */
    suspend fun detectFaces(mediaId: Long): List<DetectedFace>
}
