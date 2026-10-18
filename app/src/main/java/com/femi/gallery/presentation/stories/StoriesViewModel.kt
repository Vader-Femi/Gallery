package com.femi.gallery.presentation.stories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.femi.gallery.domain.model.Story
import com.femi.gallery.domain.usecase.ObserveStoriesUseCase
import com.femi.gallery.domain.usecase.RefreshMediaUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class StoriesViewModel @Inject constructor(
    observeStories: ObserveStoriesUseCase,
    private val refreshMedia: RefreshMediaUseCase,
) : ViewModel() {

    /** Null until the first search for stories has finished. */
    val stories: StateFlow<List<Story>?> = observeStories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onResumed() = refreshMedia()
}
