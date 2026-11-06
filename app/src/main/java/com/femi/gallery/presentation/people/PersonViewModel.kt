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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PersonViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val personDao: PersonDao,
    private val faceDao: FaceDao,
    private val faceRepo: com.femi.gallery.domain.repository.FaceIndexRepository,
    private val dataSource: MediaStoreDataSource,
    private val settingsRepository: com.femi.gallery.domain.repository.SettingsRepository,
    private val visiblePeople: com.femi.gallery.data.face.VisiblePeople,
) : ViewModel() {

    val personId: Long = checkNotNull(savedStateHandle["personId"])

    val person: StateFlow<PersonRow?> = combine(personDao.flow(personId), settingsRepository.hiddenAlbums) { row, _ -> row }
        .map { row -> row?.let { visiblePeople.filter(listOf(it)).firstOrNull() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val otherPeople: StateFlow<List<PersonRow>> = combine(personDao.flowAll(), settingsRepository.hiddenAlbums) { list, _ -> list }
        .map { visiblePeople.filter(it) }
        .map { list -> list.filter { it.id != personId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    suspend fun findExistingPerson(name: String): PersonRow? =
        faceRepo.findExistingPerson(name, personId)

    fun mergeWith(targetPersonId: Long, newName: String? = null) {
        viewModelScope.launch {
            faceRepo.mergePeople(survivorId = personId, absorbedId = targetPersonId, newName = newName)
        }
    }

    fun rename(name: String) {
        viewModelScope.launch {
            val clean = name.trim()
            if (clean.isNotEmpty()) {
                personDao.updateName(personId, clean)
            }
        }
    }
}
