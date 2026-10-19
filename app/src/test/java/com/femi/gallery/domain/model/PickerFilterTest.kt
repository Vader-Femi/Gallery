package com.femi.gallery.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PickerFilterTest {

    @Test
    fun noTypesMeansEverything() {
        assertEquals(PickerFilter.All, PickerFilter.fromMimeTypes(emptyList()))
        assertEquals(PickerFilter.All, PickerFilter.fromMimeTypes(listOf("", "  ")))
    }

    @Test
    fun imageTypesNarrowToPhotos() {
        assertEquals(PickerFilter.ImagesOnly, PickerFilter.fromMimeTypes(listOf("image/*")))
        assertEquals(PickerFilter.ImagesOnly, PickerFilter.fromMimeTypes(listOf("image/jpeg", "IMAGE/PNG")))
    }

    @Test
    fun videoTypesNarrowToVideos() {
        assertEquals(PickerFilter.VideosOnly, PickerFilter.fromMimeTypes(listOf("video/*")))
    }

    @Test
    fun mixedOrWildcardTypesKeepEverything() {
        assertEquals(PickerFilter.All, PickerFilter.fromMimeTypes(listOf("image/*", "video/*")))
        assertEquals(PickerFilter.All, PickerFilter.fromMimeTypes(listOf("*/*")))
        assertEquals(PickerFilter.All, PickerFilter.fromMimeTypes(listOf("image/*", "application/pdf")))
    }

    @Test
    fun filterBecomesASearch() {
        assertNull(PickerFilter.All.toCriteria())
        assertEquals(MediaType.Image, PickerFilter.ImagesOnly.toCriteria()?.mediaType)
        assertEquals(MediaType.Video, PickerFilter.VideosOnly.toCriteria()?.mediaType)
    }
}
