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

import android.content.ContentUris
import android.provider.MediaStore
import com.femi.gallery.data.db.PersonDao
import com.femi.gallery.domain.model.SpecialAlbums

@HiltViewModel
class AlbumsViewModel @Inject constructor(
    observeAlbums: ObserveAlbumsUseCase,
    selectionFactory: MediaSelectionControllerFactory,
    private val refreshMedia: RefreshMediaUseCase,
    private val settingsRepository: SettingsRepository,
    private val personDao: PersonDao,
) : ViewModel() {

    /** Runs album actions (rename) with their system confirmations; there is no selection on this screen. */
    val actions = selectionFactory.create(scope = viewModelScope, bucketId = null)

    val albums: StateFlow<List<Album>> = combine(
        observeAlbums(),
        settingsRepository.albumSortOption,
        settingsRepository.hiddenAlbums.map { set -> set.mapNotNull { it.toLongOrNull() }.toSet() },
        personDao.flowAll(),
    ) { list, sortOption, hiddenAlbums, people ->
        val visible = list.filter { 
            (it.itemCount > 0 || it.bucketId < 0) && !hiddenAlbums.contains(it.bucketId)
        }.toMutableList()

        if (people.isNotEmpty() && !hiddenAlbums.contains(SpecialAlbums.PEOPLE)) {
            val topPerson = people.first()
            val coverUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, topPerson.coverMediaId).toString()
            visible.add(
                0,
                Album(
                    bucketId = SpecialAlbums.PEOPLE,
                    name = "People",
                    relativePath = "",
                    coverUri = coverUri,
                    itemCount = people.size,
                    newestDate = Long.MAX_VALUE,
                ),
            )
        }
        AlbumSorting.orderAlbums(visible, sortOption)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onResumed() = refreshMedia()
}
