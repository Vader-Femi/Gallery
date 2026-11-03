package com.femi.gallery.presentation.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.femi.gallery.domain.mirror.AlbumSorting
import com.femi.gallery.domain.model.Album
import com.femi.gallery.domain.repository.SettingsRepository
import com.femi.gallery.domain.usecase.ObserveAlbumsUseCase
import com.femi.gallery.domain.usecase.RefreshMediaUseCase
import com.femi.gallery.presentation.selection.MediaSelectionControllerFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AlbumsViewModel @Inject constructor(
    observeAlbums: ObserveAlbumsUseCase,
    selectionFactory: MediaSelectionControllerFactory,
    private val refreshMedia: RefreshMediaUseCase,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    /** Runs album actions (rename) with their system confirmations; there is no selection on this screen. */
    val actions = selectionFactory.create(scope = viewModelScope, bucketId = null)

    val albums: StateFlow<List<Album>> = combine(
        observeAlbums(),
        settingsRepository.albumSortOption,
        settingsRepository.hiddenAlbums.map { set -> set.mapNotNull { it.toLongOrNull() }.toSet() },
    ) { list, sortOption, hiddenAlbums ->
        val visible = list.filter { 
            (it.itemCount > 0 || it.bucketId < 0) && !hiddenAlbums.contains(it.bucketId)
        }
        AlbumSorting.orderAlbums(visible, sortOption)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onResumed() = refreshMedia()
}
