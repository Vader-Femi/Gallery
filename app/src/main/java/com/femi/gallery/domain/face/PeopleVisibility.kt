package com.femi.gallery.domain.face

import com.femi.gallery.data.db.PersonRow

/** What shows of the people while some albums are hidden: their count, their cover, and whether they show at all. */
object PeopleVisibility {
    /** The face to use as a cover: its photo and where the face is in it (as stored in the person row). */
    data class Cover(val mediaId: Long, val crop: String)

    /**
     * [persons] as they should be shown: a person with no photo outside the hidden albums is left out; the others
     * count only those photos ([visibleCounts], by person id); and a person whose cover photo is in a hidden album
     * ([hiddenCoverPersons]) gets the cover from [replacements] instead.
     */
    fun apply(
        persons: List<PersonRow>,
        visibleCounts: Map<Long, Int>,
        hiddenCoverPersons: Set<Long>,
        replacements: Map<Long, Cover>,
    ): List<PersonRow> = persons.mapNotNull { person ->
        val count = visibleCounts[person.id] ?: 0
        if (count <= 0) return@mapNotNull null
        val replacement = replacements[person.id]
        when {
            person.id !in hiddenCoverPersons -> person.copy(photoCount = count)
            replacement != null -> person.copy(photoCount = count, coverMediaId = replacement.mediaId, coverCrop = replacement.crop)
            else -> null // its only cover is in a hidden album and no other face can stand in
        }
    }
}
