package com.femi.gallery.presentation.people

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.femi.gallery.data.db.FaceDao
import com.femi.gallery.data.db.PersonDao
import com.femi.gallery.data.db.PersonRow
import com.femi.gallery.data.mediastore.MediaStoreDataSource
import com.femi.gallery.domain.model.MediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PersonViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val personDao: PersonDao,
    private val faceDao: FaceDao,
    private val dataSource: MediaStoreDataSource,
) : ViewModel() {

    val personId: Long = checkNotNull(savedStateHandle["personId"])

    val person: StateFlow<PersonRow?> = personDao.flow(personId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val photos: StateFlow<List<MediaItem>> = faceDao.flowMediaIdsForPerson(personId)
        .flatMapLatest { ids ->
            flow {
                if (ids.isEmpty()) {
                    emit(emptyList())
                } else {
                    emit(dataSource.loadVisibleByIds(ids.toLongArray()))
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun rename(name: String) {
        viewModelScope.launch {
            val clean = name.trim()
            if (clean.isNotEmpty()) {
                personDao.updateName(personId, clean)
            }
        }
    }
}
