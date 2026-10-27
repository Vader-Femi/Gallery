package com.femi.gallery.domain.model

/**
 * Finds the edit that belongs to a story and keeps the list of edits tidy.
 *
 * Stories are found again from the photos each time, and a trip grows when more of its photos
 * appear, so the days of a story can change after it was renamed. An edit therefore matches:
 * - a single-day story only when it was made for exactly that day, or
 * - a longer story when it was made for exactly its days, or for days that mostly overlap
 *   ([MIN_OVERLAP] of the days they cover together, longer stories only).
 *
 * Single days never match loosely: an "On this day" memory sits inside a longer trip and must not
 * take over the trip's title.
 */
object StoryEdits {

    /** Share of the days covered by either range that both must cover for a loose match. */
    const val MIN_OVERLAP = 0.4

    /** The edit made for the story [fromDay]..[toDay], or null. */
    fun find(edits: List<StoryEdit>, fromDay: Long, toDay: Long): StoryEdit? =
        edits.firstOrNull { it.fromDay == fromDay && it.toDay == toDay } ?: bestLooseMatch(edits, fromDay, toDay)

    /**
     * The edit for each story in [ranges] (pairs of first and last day), in the same order; null
     * where there is none. An edit is given to at most one story, exact matches first.
     */
    fun assign(edits: List<StoryEdit>, ranges: List<Pair<Long, Long>>): List<StoryEdit?> {
        val result = arrayOfNulls<StoryEdit>(ranges.size)
        val taken = HashSet<StoryEdit>()
        ranges.forEachIndexed { index, (from, to) ->
            val exact = edits.firstOrNull { it.fromDay == from && it.toDay == to && it !in taken }
            if (exact != null) {
                result[index] = exact
                taken.add(exact)
            }
        }
        ranges.forEachIndexed { index, (from, to) ->
            if (result[index] == null) {
                val loose = bestLooseMatch(edits.filter { it !in taken }, from, to)
                if (loose != null) {
                    result[index] = loose
                    taken.add(loose)
                }
            }
        }
        return result.toList()
    }

    /**
     * [edits] with the edit of the story [fromDay]..[toDay] replaced by [change] applied to it (or to
     * a new empty edit). The result is written for the story's current days, and an edit that no
     * longer changes anything is dropped.
     */
    fun update(
        edits: List<StoryEdit>,
        fromDay: Long,
        toDay: Long,
        change: (StoryEdit) -> StoryEdit,
    ): List<StoryEdit> {
        val current = find(edits, fromDay, toDay)
        val changed = change(current ?: StoryEdit(fromDay, toDay)).copy(fromDay = fromDay, toDay = toDay)
        val rest = edits.filter { it != current }
        return if (changed.isEmpty) rest else rest + changed
    }

    private fun bestLooseMatch(edits: List<StoryEdit>, fromDay: Long, toDay: Long): StoryEdit? {
        if (fromDay == toDay) return null
        return edits
            .filter { it.fromDay != it.toDay }
            .map { it to overlap(it.fromDay, it.toDay, fromDay, toDay) }
            .filter { it.second >= MIN_OVERLAP }
            .maxByOrNull { it.second }
            ?.first
    }

    /** Days both ranges cover divided by days either covers (1 for the same range, 0 for none). */
    private fun overlap(aFrom: Long, aTo: Long, bFrom: Long, bTo: Long): Double {
        val shared = minOf(aTo, bTo) - maxOf(aFrom, bFrom) + 1
        if (shared <= 0) return 0.0
        val together = maxOf(aTo, bTo) - minOf(aFrom, bFrom) + 1
        return shared.toDouble() / together
    }
}
