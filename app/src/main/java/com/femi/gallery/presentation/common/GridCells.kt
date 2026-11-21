package com.femi.gallery.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow

/**
 * Where the grid cells that are on screen are, so a photo that closes can fly into exactly its cell. Each cell
 * remembers its coordinates (not a rectangle: nothing is made while the grid scrolls) and forgets them when it
 * leaves the screen; the rectangle is only worked out when asked for. Used on the main thread only.
 */
object GridCells {
    private class Entry(val token: Any, val coordinates: LayoutCoordinates)

    private val cells = HashMap<Long, Entry>()

    internal fun place(mediaId: Long, token: Any, coordinates: LayoutCoordinates) {
        cells[mediaId] = Entry(token, coordinates)
    }

    internal fun forget(mediaId: Long, token: Any) {
        if (cells[mediaId]?.token === token) cells.remove(mediaId)
    }

    /**
     * The cell of the photo [mediaId] in the window, or null when it is not on screen (or not in a grid that reports
     * its cells). The whole cell, also the part that a scrolled grid has cut off.
     */
    fun boundsInWindow(mediaId: Long): Rect? {
        val entry = cells[mediaId] ?: return null
        val coordinates = entry.coordinates
        if (!coordinates.isAttached) return null
        val position: Offset = coordinates.positionInWindow()
        val size = coordinates.size
        if (size.width <= 0 || size.height <= 0) return null
        return Rect(position, Size(size.width.toFloat(), size.height.toFloat()))
    }
}

/** Makes this the grid cell of the photo [mediaId] for [GridCells]. */
@Composable
fun Modifier.gridCellBounds(mediaId: Long): Modifier {
    if (mediaId <= 0L) return this
    val token = remember { Any() }
    DisposableEffect(mediaId, token) { onDispose { GridCells.forget(mediaId, token) } }
    return this.onPlaced { GridCells.place(mediaId, token, it) }
}
