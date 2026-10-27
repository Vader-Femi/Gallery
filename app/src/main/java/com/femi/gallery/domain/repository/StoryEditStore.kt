package com.femi.gallery.domain.repository

import com.femi.gallery.domain.model.StoryEdit
import kotlinx.coroutines.flow.Flow

/** Where the titles and covers the person chose for stories are kept. */
interface StoryEditStore {
    /** The saved edits; emits again whenever they change. */
    val edits: Flow<List<StoryEdit>>

    /** Replaces the saved edits with [change] applied to them, one change at a time. */
    suspend fun update(change: (List<StoryEdit>) -> List<StoryEdit>)
}
