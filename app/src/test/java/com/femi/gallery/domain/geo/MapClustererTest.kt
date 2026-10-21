package com.femi.gallery.domain.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapClustererTest {

    /** Three photos in Lagos, two in Abuja and one in London. */
    private val media = LocatedMedia(
        ids = longArrayOf(1, 2, 3, 4, 5, 6),
        latitudes = doubleArrayOf(6.5244, 6.5250, 6.4500, 9.0765, 9.0800, 51.5072),
        longitudes = doubleArrayOf(3.3792, 3.3800, 3.4000, 7.3986, 7.4000, -0.1276),
        dates = longArrayOf(100, 300, 200, 50, 60, 10),
    )

    @Test
    fun farApartPlacesAreSeparateClustersWhenZoomedOutFar() {
        val clusters = MapClusterer.cluster(media, zoom = 3.0)
        // At zoom 3 Lagos and Abuja are still different cells from London.
        assertTrue(clusters.size in 2..3)
        assertEquals(6, clusters.sumOf { it.count })
    }

    @Test
    fun theWholeWorldInOneCellMergesEverything() {
        val clusters = MapClusterer.cluster(media, zoom = 0.0, cellPixels = 1_000.0)
        assertEquals(1, clusters.size)
        assertEquals(6, clusters.single().count)
    }

    @Test
    fun zoomingInSplitsClustersAndNeverLosesPhotos() {
        var previous = 0
        for (zoom in listOf(2.0, 6.0, 10.0, 14.0, 18.0)) {
            val clusters = MapClusterer.cluster(media, zoom)
            assertEquals(6, clusters.sumOf { it.count })
            assertTrue(clusters.size >= previous)
            previous = clusters.size
        }
        assertEquals(6, previous) // every photo alone at street level
    }

    @Test
    fun theCoverIsTheNewestPhotoAndTheLargestClusterComesFirst() {
        val clusters = MapClusterer.cluster(media, zoom = 9.0)
        val first = clusters.first()
        assertEquals(3, first.count)
        assertEquals(2L, first.coverId) // dated 300, the newest of 1, 2 and 3
        assertTrue(clusters.zipWithNext().all { (a, b) -> a.count >= b.count })
    }

    @Test
    fun searchingAClusterBoxFindsExactlyItsPhotos() {
        for (zoom in listOf(1.0, 5.0, 9.0, 12.0)) {
            for (cluster in MapClusterer.cluster(media, zoom)) {
                assertEquals(cluster.count, media.idsIn(cluster.box).size)
                assertTrue(media.idsIn(cluster.box).contains(cluster.coverId))
            }
        }
    }

    @Test
    fun theCentreIsTheAveragePlace() {
        val cluster = MapClusterer.cluster(media, zoom = 9.0).first()
        assertEquals((6.5244 + 6.5250 + 6.4500) / 3, cluster.latitude, 1e-9)
        assertEquals((3.3792 + 3.3800 + 3.4000) / 3, cluster.longitude, 1e-9)
    }

    @Test
    fun onlyPhotosInTheGivenAreaAreClustered() {
        val nigeria = GeoBox(south = 4.0, west = 2.0, north = 14.0, east = 15.0)
        val clusters = MapClusterer.cluster(media, zoom = 5.0, within = nigeria)
        assertEquals(5, clusters.sumOf { it.count })
    }

    @Test
    fun noPhotosMeansNoClusters() {
        assertTrue(MapClusterer.cluster(LocatedMedia.Empty, zoom = 5.0).isEmpty())
    }

    @Test
    fun idsInOnlyReturnsPhotosInsideTheBox() {
        val lagos = GeoBox(south = 6.0, west = 3.0, north = 7.0, east = 4.0)
        assertEquals(setOf(1L, 2L, 3L), media.idsIn(lagos))
    }

    @Test
    fun thousandsOfPhotosClusterQuickly() {
        val n = 50_000
        val big = LocatedMedia(
            ids = LongArray(n) { it.toLong() },
            latitudes = DoubleArray(n) { (it % 180) - 89.0 },
            longitudes = DoubleArray(n) { ((it * 7) % 360) - 179.0 },
            dates = LongArray(n) { it.toLong() },
        )
        val clusters = MapClusterer.cluster(big, zoom = 4.0)
        assertEquals(n, clusters.sumOf { it.count })
    }
}
