package com.femi.gallery.domain.geo

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationScanPlannerTest {

    private val place = GeoPoint(6.5, 3.4)

    @Test
    fun aFirstScanReadsEverythingNewestChangeFirst() {
        val plan = LocationScanPlanner.plan(
            cached = emptyList(),
            ids = longArrayOf(1, 2, 3),
            modified = longArrayOf(100, 300, 200),
        )
        assertEquals(emptyList<LocationEntry>(), plan.keep)
        assertEquals(listOf(2L, 3L, 1L), plan.scanIds.toList())
        assertEquals(listOf(300L, 200L, 100L), plan.scanModified.toList())
        assertEquals(3, plan.toScan)
    }

    @Test
    fun unchangedPhotosAreKeptAndNotReadAgain() {
        val cached = listOf(LocationEntry(1, 100, place), LocationEntry(2, 200, null))
        val plan = LocationScanPlanner.plan(cached, longArrayOf(1, 2), longArrayOf(100, 200))
        assertEquals(cached.toSet(), plan.keep.toSet())
        assertEquals(0, plan.toScan)
    }

    @Test
    fun aModifiedPhotoIsReadAgain() {
        val cached = listOf(LocationEntry(1, 100, place))
        val plan = LocationScanPlanner.plan(cached, longArrayOf(1), longArrayOf(150))
        assertEquals(emptyList<LocationEntry>(), plan.keep)
        assertEquals(listOf(1L), plan.scanIds.toList())
        assertEquals(listOf(150L), plan.scanModified.toList())
    }

    @Test
    fun deletedPhotosAreDropped() {
        val cached = listOf(LocationEntry(1, 100, place), LocationEntry(2, 200, place))
        val plan = LocationScanPlanner.plan(cached, longArrayOf(2), longArrayOf(200))
        assertEquals(listOf(LocationEntry(2, 200, place)), plan.keep)
        assertEquals(0, plan.toScan)
    }

    @Test
    fun anInterruptedScanResumesWithTheRest() {
        val cached = listOf(LocationEntry(3, 30, place), LocationEntry(2, 20, null))
        val plan = LocationScanPlanner.plan(cached, longArrayOf(1, 2, 3, 4), longArrayOf(10, 20, 30, 40))
        assertEquals(setOf(2L, 3L), plan.keep.map { it.id }.toSet())
        assertEquals(listOf(4L, 1L), plan.scanIds.toList())
    }

    @Test
    fun everyPhotoEndsUpKeptOrScannedExactlyOnce() {
        val ids = LongArray(1_000) { it.toLong() }
        val modified = LongArray(1_000) { (it * 7 % 13).toLong() }
        val cached = (0 until 1_000 step 3).map { LocationEntry(it.toLong(), modified[it], null) } +
            LocationEntry(5_000, 1, null) // no longer exists
        val plan = LocationScanPlanner.plan(cached, ids, modified)
        val all = plan.keep.map { it.id } + plan.scanIds.toList()
        assertEquals(1_000, all.size)
        assertEquals(1_000, all.toSet().size)
    }
}
