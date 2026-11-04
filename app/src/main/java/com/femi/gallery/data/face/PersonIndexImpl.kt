package com.femi.gallery.data.face

import com.femi.gallery.data.db.PersonDao
import com.femi.gallery.domain.face.PersonIndex
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersonIndexImpl @Inject constructor(
    private val personDao: PersonDao,
) : PersonIndex {
    override suspend fun idsMatching(term: String): Set<Long> {
        val clean = term.trim()
        if (clean.isEmpty()) return emptySet()
        return personDao.mediaIdsForName(clean).toSet()
    }
}
