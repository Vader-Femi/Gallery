package com.femi.gallery.presentation.selection

import com.femi.gallery.domain.model.MediaRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionManagerTest {

    private fun ref(id: Long) = MediaRef(id, "content://media/external/images/media/$id", isFavorite = false)

    @Test
    fun beginEntersSelectionModeWithTheItem() {
        val manager = SelectionManager()
        manager.begin(ref(1))
        assertTrue(manager.state.value.active)
        assertEquals(setOf(1L), manager.state.value.ids)
    }

    @Test
    fun toggleAddsAndRemovesButStaysActive() {
        val manager = SelectionManager()
        manager.begin(ref(1))
        manager.toggle(ref(2))
        assertEquals(setOf(1L, 2L), manager.state.value.ids)
        manager.toggle(ref(1))
        manager.toggle(ref(2))
        assertEquals(0, manager.state.value.count)
        assertTrue(manager.state.value.active)
    }

    @Test
    fun selectAllReplacesSelection() {
        val manager = SelectionManager()
        manager.begin(ref(9))
        manager.selectAll(listOf(ref(1), ref(2), ref(3)))
        assertEquals(setOf(1L, 2L, 3L), manager.state.value.ids)
    }

    @Test
    fun clearLeavesSelectionMode() {
        val manager = SelectionManager()
        manager.begin(ref(1))
        manager.clear()
        assertFalse(manager.state.value.active)
        assertEquals(0, manager.state.value.count)
    }

    @Test
    fun addAllAddsWithoutReplacingExisting() {
        val manager = SelectionManager()
        manager.begin(ref(1))
        manager.addAll(listOf(ref(2), ref(3)))
        assertTrue(manager.state.value.active)
        assertEquals(setOf(1L, 2L, 3L), manager.state.value.ids)
    }

    @Test
    fun removeAllRemovesSpecifiedIdsAndStaysActive() {
        val manager = SelectionManager()
        manager.addAll(listOf(ref(1), ref(2), ref(3), ref(4)))
        manager.removeAll(setOf(2L, 4L))
        assertTrue(manager.state.value.active)
        assertEquals(setOf(1L, 3L), manager.state.value.ids)
    }
}
