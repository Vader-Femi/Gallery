package com.femi.gallery.presentation.common

/**
 * The photo the viewer ended on, reported back to the screen that opened it. The [id] is what
 * identifies the photo; the [index] is only where the viewer found it, which can differ from where
 * the grid has it when the two lists were loaded at different moments.
 */
data class ViewedMedia(val index: Int, val id: Long)
