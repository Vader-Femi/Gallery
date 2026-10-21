package com.femi.gallery.domain.geo

/** Decides which map labels fit on screen without overlapping each other. */
object LabelPlacer {

    /** A label's rectangle in screen pixels. */
    class Box(val left: Float, val top: Float, val right: Float, val bottom: Float)

    /**
     * For [boxes] listed from most to least important, which ones to show: a label is hidden when it
     * would come within [padding] pixels of a more important label that is shown.
     */
    fun place(boxes: List<Box>, padding: Float = 4f): BooleanArray {
        val shown = BooleanArray(boxes.size)
        val accepted = ArrayList<Box>()
        for (i in boxes.indices) {
            val box = boxes[i]
            val overlaps = accepted.any {
                box.left < it.right + padding && box.right > it.left - padding &&
                    box.top < it.bottom + padding && box.bottom > it.top - padding
            }
            if (!overlaps) {
                shown[i] = true
                accepted.add(box)
            }
        }
        return shown
    }
}
