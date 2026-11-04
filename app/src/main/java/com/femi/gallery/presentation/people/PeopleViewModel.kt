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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PeopleViewModel @Inject constructor(
    private val personDao: PersonDao,
    private val faceDao: FaceDao,
    private val faceScanDao: com.femi.gallery.data.db.FaceScanDao,
    private val faceRepo: FaceIndexRepository,
) : ViewModel() {

    val people: StateFlow<List<PersonRow>> = personDao.flowAll()
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

    fun rescan() {
        viewModelScope.launch {
            personDao.deleteAll()
            faceDao.deleteAll()
            faceScanDao.deleteAll()
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

    fun removePerson(personId: Long) {
        viewModelScope.launch {
            personDao.delete(personId)
            faceDao.deleteForPerson(personId)
        }
    }
}
