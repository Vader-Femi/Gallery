package com.femi.gallery.presentation.albums

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.femi.gallery.domain.mirror.AlbumSorting
import com.femi.gallery.domain.model.Album
import com.femi.gallery.domain.usecase.ObserveAlbumsUseCase
import com.femi.gallery.domain.usecase.RefreshMediaUseCase
import com.femi.gallery.presentation.selection.MediaSelectionControllerFactory
import com.femi.gallery.presentation.settings.ThemePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AlbumsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    observeAlbums: ObserveAlbumsUseCase,
    selectionFactory: MediaSelectionControllerFactory,
    private val refreshMedia: RefreshMediaUseCase,
) : ViewModel() {

    /** Runs album actions (rename) with their system confirmations; there is no selection on this screen. */
    val actions = selectionFactory.create(scope = viewModelScope, bucketId = null)

    val albums: StateFlow<List<Album>> = combine(
        observeAlbums(),
        ThemePreferences.getAlbumSortOptionFlow(context),
        ThemePreferences.getHiddenAlbumsFlow(context)
    ) { list, sortOption, hiddenAlbums ->
        val visible = list.filter { 
            (it.itemCount > 0 || it.bucketId < 0) && !hiddenAlbums.contains(it.bucketId)
        }
        AlbumSorting.orderAlbums(visible, sortOption)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onResumed() = refreshMedia()
}
