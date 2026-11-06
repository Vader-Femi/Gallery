package com.femi.gallery.domain.face

import com.femi.gallery.data.db.FaceDao
import com.femi.gallery.data.db.FaceRow
import com.femi.gallery.data.db.PersonDao
import com.femi.gallery.data.db.PersonRow
import com.femi.gallery.domain.face.FaceFeatureHelper.toByteArray
import com.femi.gallery.domain.face.FaceFeatureHelper.toFloatArray
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class FaceClusterer @Inject constructor(
    private val personDao: PersonDao,
    private val faceDao: FaceDao,
) {
    suspend fun assignFace(
        mediaId: Long,
        boundsString: String,
        feature: FloatArray,
        excludedPersonIds: Set<Long> = emptySet(),
    ): Long {
        val people = personDao.all()
        var bestPerson: PersonRow? = null
        var bestScore = -1f
        val threshold = when {
            feature.size == 512 -> MOBILE_FACENET_THRESHOLD
            feature.size > 100 -> CLIP_CLUSTER_THRESHOLD
            else -> ZERO_MEAN_CLUSTER_THRESHOLD
        }

        for (person in people) {
            if (person.id in excludedPersonIds) continue
            val centroid = person.centroid.toFloatArray()
            if (centroid.isNotEmpty() && centroid.size == feature.size) {
                val score = FaceFeatureHelper.cosineSimilarity(feature, centroid)
                if (score > bestScore) {
                    bestScore = score
                    bestPerson = person
                }
            }
        }

        return if (bestPerson != null && bestScore >= threshold) {
            val person = bestPerson
            val oldCentroid = person.centroid.toFloatArray()
            val newCentroid = updateCentroid(oldCentroid, person.photoCount, feature)
            val updated = person.copy(
                photoCount = person.photoCount + 1,
                centroid = newCentroid.toByteArray(),
            )
            personDao.update(updated)
            val faceRow = FaceRow(
                mediaId = mediaId,
                personId = person.id,
                bounds = boundsString,
                feature = feature.toByteArray(),
            )
            faceDao.insert(listOf(faceRow))
            person.id
        } else {
            val newPersonName = "Person ${people.size + 1}"
            val newPerson = PersonRow(
                name = newPersonName,
                coverMediaId = mediaId,
                coverCrop = boundsString,
                photoCount = 1,
                centroid = feature.toByteArray(),
            )
            val personId = personDao.insert(newPerson)
            val faceRow = FaceRow(
                mediaId = mediaId,
                personId = personId,
                bounds = boundsString,
                feature = feature.toByteArray(),
            )
            faceDao.insert(listOf(faceRow))
            personId
        }
    }

    private fun updateCentroid(oldCentroid: FloatArray, count: Int, newFeature: FloatArray): FloatArray {
        if (oldCentroid.isEmpty() || oldCentroid.size != newFeature.size) return newFeature
        val updated = FloatArray(oldCentroid.size)
        var normSq = 0f
        val wOld = count.coerceAtMost(20).toFloat()
        for (i in updated.indices) {
            val v = (oldCentroid[i] * wOld + newFeature[i]) / (wOld + 1f)
            updated[i] = v
            normSq += v * v
        }
        val norm = sqrt(normSq)
        if (norm > 1e-6f) {
            for (i in updated.indices) updated[i] /= norm
        }
        return updated
    }

    fun mergeCentroids(c1: FloatArray, count1: Int, c2: FloatArray, count2: Int): FloatArray {
        if (c1.isEmpty() || c1.size != c2.size) return if (c2.isNotEmpty()) c2 else c1
        if (c2.isEmpty()) return c1
        val w1 = count1.coerceAtLeast(1).coerceAtMost(50).toFloat()
        val w2 = count2.coerceAtLeast(1).coerceAtMost(50).toFloat()
        val total = w1 + w2
        val updated = FloatArray(c1.size)
        var normSq = 0f
        for (i in updated.indices) {
            val v = (c1[i] * w1 + c2[i] * w2) / total
            updated[i] = v
            normSq += v * v
        }
        val norm = sqrt(normSq)
        if (norm > 1e-6f) {
            for (i in updated.indices) updated[i] /= norm
        }
        return updated
    }

    companion object {
        const val MOBILE_FACENET_THRESHOLD = 0.62f
        const val CLIP_CLUSTER_THRESHOLD = 0.76f
        const val ZERO_MEAN_CLUSTER_THRESHOLD = 0.70f
    }
}
