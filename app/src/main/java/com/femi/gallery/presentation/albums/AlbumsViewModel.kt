package com.femi.gallery.presentation.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.femi.gallery.domain.model.Album
import com.femi.gallery.domain.usecase.ObserveAlbumsUseCase
import com.femi.gallery.domain.usecase.RefreshMediaUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AlbumsViewModel @Inject constructor(
    observeAlbums: ObserveAlbumsUseCase,
    private val refreshMedia: RefreshMediaUseCase,
) : ViewModel() {

    val albums: StateFlow<List<Album>> = observeAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onResumed() = refreshMedia()
}
