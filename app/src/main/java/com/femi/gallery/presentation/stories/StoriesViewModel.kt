package com.femi.gallery.presentation.stories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.femi.gallery.domain.model.Story
import com.femi.gallery.domain.usecase.ObserveStoriesUseCase
import com.femi.gallery.domain.usecase.ObserveStoriesUpdatingUseCase
import com.femi.gallery.domain.usecase.RefreshStoriesUseCase
import com.femi.gallery.domain.usecase.RefreshMediaUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StoriesViewModel @Inject constructor(
    observeStories: ObserveStoriesUseCase,
    observeStoriesUpdating: ObserveStoriesUpdatingUseCase,
    private val refreshStoriesUseCase: RefreshStoriesUseCase,
    private val refreshMedia: RefreshMediaUseCase,
) : ViewModel() {

    val stories: StateFlow<List<Story>?> = observeStories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val isUpdating: StateFlow<Boolean> = observeStoriesUpdating()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        viewModelScope.launch {
            refreshStoriesUseCase()
        }
    }

    fun onResumed() {
        refreshMedia()
        viewModelScope.launch {
            refreshStoriesUseCase()
        }
    }
}
