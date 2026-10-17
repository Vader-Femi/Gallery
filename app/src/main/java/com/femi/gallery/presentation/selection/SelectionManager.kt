package com.femi.gallery.presentation.selection

import androidx.compose.runtime.Stable
import com.femi.gallery.domain.model.MediaRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Which photos are selected, and whether the screen is in selection mode at all. */
class SelectionManager {

    @Stable
    data class State(
        val active: Boolean = false,
        val selected: Map<Long, MediaRef> = emptyMap(),
    ) {
        val count: Int get() = selected.size
        val ids: Set<Long> get() = selected.keys
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** Long-press: enters selection mode (if needed) with [ref] selected. */
    fun begin(ref: MediaRef) = _state.update { it.copy(active = true, selected = it.selected + (ref.id to ref)) }

    fun toggle(ref: MediaRef) = _state.update { current ->
        val next = if (ref.id in current.selected) current.selected - ref.id else current.selected + (ref.id to ref)
        current.copy(selected = next)
    }

    fun selectAll(refs: List<MediaRef>) {
        _state.update { it.copy(active = true, selected = refs.associateBy { ref -> ref.id }) }
    }

    fun clear() {
        _state.value = State()
    }
}
