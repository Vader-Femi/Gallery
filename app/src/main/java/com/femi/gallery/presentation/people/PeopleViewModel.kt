package com.femi.gallery.presentation.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.femi.gallery.data.db.FaceDao
import com.femi.gallery.data.db.PersonDao
import com.femi.gallery.data.db.PersonRow
import com.femi.gallery.domain.face.FaceScanState
import com.femi.gallery.domain.repository.FaceIndexRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PeopleViewModel @Inject constructor(
    private val personDao: PersonDao,
    private val faceDao: FaceDao,
    private val faceRepo: FaceIndexRepository,
    private val settingsRepository: com.femi.gallery.domain.repository.SettingsRepository,
    private val visiblePeople: com.femi.gallery.data.face.VisiblePeople,
) : ViewModel() {

    val people: StateFlow<List<PersonRow>> = combine(personDao.flowAll(), settingsRepository.hiddenAlbums) { list, _ -> list }
        .map { visiblePeople.filter(it) }
        .map { list ->
            list.sortedWith(
                compareByDescending<PersonRow> { it.isNamed }
                    .thenByDescending { it.photoCount }
                    .thenBy { it.name }
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val scanState: StateFlow<FaceScanState> = faceRepo.scan

    fun startScan() = faceRepo.startScan()

    /** Whether face recognition is on (Settings); when it is off the screen says so instead of offering a scan. */
    val enabled: StateFlow<Boolean> = settingsRepository.faceRecognition
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun turnOn() {
        viewModelScope.launch {
            settingsRepository.setFaceRecognition(true)
            faceRepo.startScan()
        }
    }

    fun rescan() {
        viewModelScope.launch {
            faceRepo.eraseAll()
            faceRepo.startScan()
        }
    }

    fun renamePerson(personId: Long, newName: String) {
        viewModelScope.launch {
            val trimmed = newName.trim()
            if (trimmed.isNotEmpty()) {
                personDao.updateName(personId, trimmed)
            }
        }
    }

    suspend fun findExistingPerson(name: String, excludeId: Long): PersonRow? =
        faceRepo.findExistingPerson(name, excludeId)

    fun mergePeople(survivorId: Long, absorbedId: Long, newName: String? = null) {
        viewModelScope.launch {
            faceRepo.mergePeople(survivorId = survivorId, absorbedId = absorbedId, newName = newName)
        }
    }

    fun removePerson(personId: Long) {
        viewModelScope.launch {
            personDao.delete(personId)
            faceDao.deleteForPerson(personId)
        }
    }
}
