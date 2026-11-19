package com.femi.gallery.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalViewSetTest {

    private val pkg = "com.femi.gallery"
    private fun c(name: String) = "content://com.other.app/photos/$name"

    @Test
    fun theSelectedItemMustBeInTheClipToSwipe() {
        val clip = listOf(c("1"), c("2"), c("3"))
        assertEquals(clip, ExternalViewSets.candidates(c("2"), clip, pkg))
        assertEquals(emptyList<String>(), ExternalViewSets.candidates(c("9"), clip, pkg))
        assertEquals(emptyList<String>(), ExternalViewSets.candidates(c("1"), emptyList(), pkg))
    }

    @Test
    fun keepsTheSendersOrderAndDropsDuplicates() {
        val clip = listOf(c("3"), c("1"), c("3"), c("2"), c("1"))
        assertEquals(listOf(c("3"), c("1"), c("2")), ExternalViewSets.candidates(c("1"), clip, pkg))
    }

    @Test
    fun onlyContentAddressesAreAcceptedBesideTheSelectedOne() {
        val clip = listOf(c("1"), "file:///sdcard/secret.jpg", "http://example.com/a.jpg", c("2"))
        assertEquals(listOf(c("1"), c("2")), ExternalViewSets.candidates(c("1"), clip, pkg))
    }

    @Test
    fun theAppsOwnProvidersAreNeverAccepted() {
        val locked = "content://com.femi.gallery.locked/123456"
        val files = "content://com.femi.gallery.fileprovider/shared/a.jpg"
        val escaped = "content://com%2Efemi%2Egallery%2Elocked/123456"
        val withUser = "content://someone@com.femi.gallery.locked/123456"
        val upper = "content://COM.FEMI.GALLERY.LOCKED/123456"
        val clip = listOf(c("1"), locked, files, escaped, withUser, upper, c("2"))
        assertEquals(listOf(c("1"), c("2")), ExternalViewSets.candidates(c("1"), clip, pkg))
        for (own in listOf(locked, files, escaped, withUser, upper)) {
            assertFalse(own, ExternalViewSets.isAllowedPrimary(own, pkg))
        }
    }

    @Test
    fun anotherAppWithASimilarNameIsNotTreatedAsOwn() {
        val other = "content://com.femi.gallery2.provider/1"
        assertTrue(ExternalViewSets.isAllowedPrimary(other, pkg))
        assertTrue(ExternalViewSets.isAllowedPrimary("content://com.femi.galleryx/1", pkg))
    }

    @Test
    fun theSelectedItemMayBeAContentOrFileAddress() {
        assertTrue(ExternalViewSets.isAllowedPrimary(c("1"), pkg))
        assertTrue(ExternalViewSets.isAllowedPrimary("file:///sdcard/DCIM/a.jpg", pkg))
        assertFalse(ExternalViewSets.isAllowedPrimary("http://example.com/a.jpg", pkg))
        assertFalse(ExternalViewSets.isAllowedPrimary("no-scheme", pkg))
    }

    @Test
    fun aLongClipIsCutAroundTheSelectedItem() {
        val clip = (0 until 200).map { c("$it") }
        val middle = ExternalViewSets.candidates(c("100"), clip, pkg)
        assertEquals(ExternalViewSets.MAX_ITEMS, middle.size)
        assertTrue(c("100") in middle)
        val first = ExternalViewSets.candidates(c("0"), clip, pkg)
        assertEquals(clip.take(ExternalViewSets.MAX_ITEMS), first)
        val last = ExternalViewSets.candidates(c("199"), clip, pkg)
        assertEquals(clip.takeLast(ExternalViewSets.MAX_ITEMS), last)
    }

    @Test
    fun assembleKeepsImagesAndVideosAndFindsTheStart() {
        val candidates = listOf(c("1"), c("2"), c("3"), c("4"))
        val types = mapOf(c("1") to "image/jpeg", c("2") to "video/mp4", c("3") to "application/pdf", c("4") to null)
        val set = ExternalViewSets.assemble(c("2"), selectedIsVideo = true, candidates = candidates) { types[it] }
        assertEquals(
            listOf(ExternalMediaRef(c("1"), false), ExternalMediaRef(c("2"), true)),
            set.items,
        )
        assertEquals(1, set.startIndex)
    }

    @Test
    fun theSelectedItemStaysEvenIfItsTypeCannotBeRead() {
        val set = ExternalViewSets.assemble(c("2"), false, listOf(c("1"), c("2"))) { null }
        assertEquals(listOf(ExternalMediaRef(c("2"), false)), set.items)
        assertEquals(0, set.startIndex)
    }

    @Test
    fun withoutCandidatesTheSetIsTheSelectedItemAlone() {
        val set = ExternalViewSets.assemble(c("7"), true, emptyList()) { "image/png" }
        assertEquals(ExternalViewSet(listOf(ExternalMediaRef(c("7"), true)), 0), set)
    }
}
