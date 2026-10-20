package com.femi.gallery.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageWindowTest {

    private val total = 1_000

    @Test
    fun forwardStartsAtTheKeyOrAtTheBeginning() {
        assertEquals(PageWindow(300, 540), PageWindow.forward(key = 300, loadSize = 240, total = total))
        assertEquals(PageWindow(0, 240), PageWindow.forward(key = null, loadSize = 240, total = total))
    }

    @Test
    fun forwardIsClampedToTheList() {
        assertEquals(PageWindow(900, 1_000), PageWindow.forward(key = 900, loadSize = 240, total = total))
        assertEquals(PageWindow(999, 1_000), PageWindow.forward(key = 5_000, loadSize = 120, total = total))
        assertEquals(PageWindow(0, 0), PageWindow.forward(key = 0, loadSize = 120, total = 0))
    }

    @Test
    fun backwardEndsExactlyWhereTheLoadedPageStarts() {
        // After a jump the first page is loaded with 240 items, but later pages with 120.
        val first = PageWindow.forward(key = 500, loadSize = 240, total = total)
        val before = PageWindow.backward(endKey = first.prevKey!!, loadSize = 120, total = total)
        assertEquals(PageWindow(380, 500), before)
        assertEquals(first.start, before.end)
    }

    @Test
    fun backwardNeverOverlapsAtTheStartOfTheList() {
        val first = PageWindow.forward(key = 50, loadSize = 240, total = total)
        val before = PageWindow.backward(endKey = first.prevKey!!, loadSize = 120, total = total)
        assertEquals(PageWindow(0, 50), before)
        assertNull(before.prevKey)
    }

    @Test
    fun chainingPagesCoversTheListWithoutGapsOrOverlaps() {
        // Jump into the middle, then keep loading both ways with the usual page size.
        val covered = mutableListOf<Int>()
        var window = PageWindow.forward(key = 410, loadSize = 240, total = total)
        covered += (window.start until window.end)
        var back: Int? = window.prevKey
        while (back != null) {
            window = PageWindow.backward(back, loadSize = 120, total = total)
            covered += (window.start until window.end)
            back = window.prevKey
        }
        var forward = PageWindow.forward(key = 410, loadSize = 240, total = total).nextKey(total)
        while (forward != null) {
            window = PageWindow.forward(forward, loadSize = 120, total = total)
            covered += (window.start until window.end)
            forward = window.nextKey(total)
        }
        assertEquals((0 until total).toList(), covered.sorted())
    }

    @Test
    fun nextKeyIsNullAtTheEnd() {
        assertNull(PageWindow(880, 1_000).nextKey(total))
        assertEquals(880, PageWindow(760, 880).nextKey(total))
    }
}
