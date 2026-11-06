package com.femi.gallery.data.face

import com.femi.gallery.data.db.FaceDao
import com.femi.gallery.data.db.PersonRow
import com.femi.gallery.domain.face.PeopleVisibility
import com.femi.gallery.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Takes the photos of hidden albums out of what the People screens show: the photo counts, and the cover of a person
 * (a photo of a hidden album must not show up there). The faces are still known; they come back when the album is
 * shown again.
 */
@Singleton
class VisiblePeople @Inject constructor(
    private val faceDao: FaceDao,
    private val settings: SettingsRepository,
) {
    suspend fun filter(persons: List<PersonRow>): List<PersonRow> {
        if (persons.isEmpty()) return persons
        val hidden = settings.hiddenAlbums.first().mapNotNull { it.toLongOrNull() }
        if (hidden.isEmpty()) return persons

        val counts = faceDao.visibleCounts(hidden).associate { it.personId to it.photos }
        val hiddenCovers = persons.map { it.coverMediaId }.distinct().chunked(CHUNK).flatMap { faceDao.hiddenAmong(it, hidden) }.toSet()
        val hiddenCoverPersons = persons.filter { it.coverMediaId in hiddenCovers }.map { it.id }.toSet()
        val replacements = HashMap<Long, PeopleVisibility.Cover>()
        for (id in hiddenCoverPersons) {
            if ((counts[id] ?: 0) <= 0) continue
            faceDao.visibleCover(id, hidden)?.let { replacements[id] = PeopleVisibility.Cover(it.mediaId, it.bounds) }
        }
        return PeopleVisibility.apply(persons, counts, hiddenCoverPersons, replacements)
    }

    private companion object {
        const val CHUNK = 500
    }
}
