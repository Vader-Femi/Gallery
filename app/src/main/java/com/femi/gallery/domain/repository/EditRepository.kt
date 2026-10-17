package com.femi.gallery.domain.repository

import android.graphics.Bitmap
import com.femi.gallery.domain.edit.EditState

/**
 * Photo editing. Bitmaps cross this boundary because decoding, transforming and saving pixels is
 * the whole job; everything else about an edit lives in the pure [EditState].
 */
interface EditRepository {
    /** The photo decoded with its orientation applied, scaled down so its longest side is at most [maxSize]. */
    suspend fun loadPreview(uri: String, maxSize: Int): Bitmap?

    /** Applies the rotation and flips of [state], and the crop too when [includeCrop] is set. */
    suspend fun applyGeometry(bitmap: Bitmap, state: EditState, includeCrop: Boolean): Bitmap

    /** Renders [state] onto the original photo and saves the result as a new picture next to it. */
    suspend fun saveCopy(uri: String, state: EditState): Boolean
}
