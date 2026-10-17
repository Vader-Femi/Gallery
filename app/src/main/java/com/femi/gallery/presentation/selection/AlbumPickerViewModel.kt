package com.femi.gallery.presentation.selection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.femi.gallery.domain.model.Album
import com.femi.gallery.domain.model.AlbumPaths
import com.femi.gallery.domain.model.SpecialAlbums
import com.femi.gallery.domain.usecase.ObserveAlbumsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The albums a photo can be moved or copied into: real folders under DCIM, Pictures or Movies. */
@HiltViewModel
class AlbumPickerViewModel @Inject constructor(
    observeAlbums: ObserveAlbumsUseCase,
) : ViewModel() {

    val albums: StateFlow<List<Album>> = observeAlbums()
        .map { all ->
            all.filter { it.bucketId !in SPECIAL_IDS && AlbumPaths.isAlbumFolder(it.relativePath) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private companion object {
        val SPECIAL_IDS = setOf(SpecialAlbums.FAVORITES, SpecialAlbums.TRASH, SpecialAlbums.LOCKED)
    }
}
