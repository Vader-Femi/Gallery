package com.femi.gallery.domain.face

import com.femi.gallery.data.db.FaceCover
import com.femi.gallery.data.db.FaceDao
import com.femi.gallery.data.db.FaceRow
import com.femi.gallery.data.db.PersonDao
import com.femi.gallery.data.db.PersonPhotoCount
import com.femi.gallery.data.db.PersonRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import kotlin.math.sqrt

class FaceClustererTest {

    private class FakePersonDao : PersonDao() {
        val people = mutableMapOf<Long, PersonRow>()
        private var nextId = 1L

        override fun flowAll(): Flow<List<PersonRow>> = emptyFlow()
        override suspend fun all(): List<PersonRow> = people.values.toList()
        override suspend fun get(id: Long): PersonRow? = people[id]
        override fun flow(id: Long): Flow<PersonRow?> = emptyFlow()

        override suspend fun insert(person: PersonRow): Long {
            val id = nextId++
            people[id] = person.copy(id = id)
            return id
        }

        override suspend fun update(person: PersonRow) {
            people[person.id] = person
        }

        override suspend fun updateName(id: Long, name: String) {
            people[id]?.let { people[id] = it.copy(name = name) }
        }

        override suspend fun updatePhotoCount(id: Long, count: Int) {
            people[id]?.let { people[id] = it.copy(photoCount = count) }
        }

        override suspend fun delete(id: Long) {
            people.remove(id)
        }

        override suspend fun deleteAll() {
            people.clear()
        }

        override suspend fun searchByName(query: String): List<PersonRow> = emptyList()
        override suspend fun mediaIdsForName(query: String): List<Long> = emptyList()
    }

    private class FakeFaceDao : FaceDao() {
        val faces = mutableListOf<FaceRow>()

        override suspend fun all(): List<FaceRow> = faces
        override suspend fun getForMedia(mediaId: Long): List<FaceRow> = faces.filter { it.mediaId == mediaId }
        override suspend fun getForPerson(personId: Long): List<FaceRow> = faces.filter { it.personId == personId }
        override suspend fun mediaIdsForPerson(personId: Long): List<Long> = faces.filter { it.personId == personId }.map { it.mediaId }.distinct()
        override fun flowMediaIdsForPerson(personId: Long): Flow<List<Long>> = emptyFlow()
        override suspend fun insert(faces: List<FaceRow>) {
            this.faces.addAll(faces)
        }
        override suspend fun deleteForMedia(mediaIds: List<Long>) {
            faces.removeAll { it.mediaId in mediaIds }
        }
        override suspend fun deleteForPerson(personId: Long) {
            faces.removeAll { it.personId == personId }
        }
        override suspend fun countPhotosForPerson(personId: Long): Int = mediaIdsForPerson(personId).size
        override suspend fun deleteAll() {
            faces.clear()
        }
        override suspend fun visibleCounts(hidden: List<Long>): List<PersonPhotoCount> = emptyList()
        override suspend fun hiddenAmong(mediaIds: List<Long>, hidden: List<Long>): List<Long> = emptyList()
        override suspend fun visibleCover(personId: Long, hidden: List<Long>): FaceCover? = null
    }

    private fun normalize(v: FloatArray): FloatArray {
        var sum = 0f
        for (x in v) sum += x * x
        val mag = sqrt(sum)
        return FloatArray(v.size) { v[it] / mag }
    }

    @Test
    fun clustersSimilar512dFacesTogether() = runBlocking {
        val personDao = FakePersonDao()
        val faceDao = FakeFaceDao()
        val clusterer = FaceClusterer(personDao, faceDao)

        // 512-d base vector for Person 1
        val baseVector = FloatArray(512) { 0.1f }
        baseVector[0] = 2.0f
        baseVector[1] = 1.5f
        val normBase = normalize(baseVector)

        // Slightly perturbed vector for Person 1 (same identity, e.g. similarity ~0.85)
        val similarVector = FloatArray(512) { 0.1f }
        similarVector[0] = 1.9f
        similarVector[1] = 1.4f
        val normSimilar = normalize(similarVector)

        val person1Id = clusterer.assignFace(101L, "0,0,1,1", normBase)
        val person2Id = clusterer.assignFace(102L, "0,0,1,1", normSimilar)

        assertEquals("Faces of the same person should be clustered together", person1Id, person2Id)
        assertEquals(1, personDao.people.size)
        assertEquals(2, personDao.people[person1Id]?.photoCount)
    }

    @Test
    fun separatesDissimilar512dFaces() = runBlocking {
        val personDao = FakePersonDao()
        val faceDao = FakeFaceDao()
        val clusterer = FaceClusterer(personDao, faceDao)

        // Vector for Person A
        val vecA = FloatArray(512) { 0f }
        vecA[0] = 1f
        vecA[1] = 1f
        val normA = normalize(vecA)

        // Orthogonal/dissimilar vector for Person B
        val vecB = FloatArray(512) { 0f }
        vecB[2] = 1f
        vecB[3] = 1f
        val normB = normalize(vecB)

        val idA = clusterer.assignFace(101L, "0,0,1,1", normA)
        val idB = clusterer.assignFace(102L, "0,0,1,1", normB)

        assertNotEquals("Different people should not be merged", idA, idB)
        assertEquals(2, personDao.people.size)
    }

    @Test
    fun neverClustersTwoFacesFromSamePhotoIntoSamePerson() = runBlocking {
        val personDao = FakePersonDao()
        val faceDao = FakeFaceDao()
        val clusterer = FaceClusterer(personDao, faceDao)

        val vec = normalize(FloatArray(512) { 0.5f })

        // Photo 101 has two faces with similar vectors (e.g. twins or similar lighting)
        val id1 = clusterer.assignFace(101L, "0,0,0.5,0.5", vec, excludedPersonIds = emptySet())
        val id2 = clusterer.assignFace(101L, "0.5,0.5,1,1", vec, excludedPersonIds = setOf(id1))

        assertNotEquals("Faces in the same photo must never be assigned to the same person", id1, id2)
        assertEquals(2, personDao.people.size)
    }

    @Test
    fun personRowIsNamedIdentifiesCustomVsDefaultNames() {
        val default1 = PersonRow(id = 1, name = "Person 1", coverMediaId = 1)
        val default99 = PersonRow(id = 2, name = "Person 99", coverMediaId = 1)
        val customName = PersonRow(id = 3, name = "Sarah", coverMediaId = 1)
        val customNameWithPerson = PersonRow(id = 4, name = "Person of Interest", coverMediaId = 1)

        org.junit.Assert.assertFalse(default1.isNamed)
        org.junit.Assert.assertFalse(default99.isNamed)
        org.junit.Assert.assertTrue(customName.isNamed)
        org.junit.Assert.assertTrue(customNameWithPerson.isNamed)
    }

    @Test
    fun sortingOrdersNamedPeopleFirstThenByPhotoCount() {
        val p1 = PersonRow(id = 1, name = "Person 1", coverMediaId = 1, photoCount = 100)
        val p2 = PersonRow(id = 2, name = "Person 2", coverMediaId = 1, photoCount = 50)
        val p3 = PersonRow(id = 3, name = "Alice", coverMediaId = 1, photoCount = 5)
        val p4 = PersonRow(id = 4, name = "Bob", coverMediaId = 1, photoCount = 20)

        val sorted = listOf(p1, p2, p3, p4).sortedWith(
            compareByDescending<PersonRow> { it.isNamed }
                .thenByDescending { it.photoCount }
                .thenBy { it.name },
        )

        assertEquals(listOf("Bob", "Alice", "Person 1", "Person 2"), sorted.map { it.name })
    }
}
