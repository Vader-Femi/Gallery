package com.femi.gallery.presentation.settings

import android.content.Context
import android.os.Environment
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.femi.gallery.data.work.BackgroundScans
import com.femi.gallery.domain.mirror.AlbumSortOption
import com.femi.gallery.domain.model.Album
import com.femi.gallery.domain.repository.SettingsRepository
import com.femi.gallery.domain.usecase.ObserveAlbumsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    observeAlbums: ObserveAlbumsUseCase,
    private val settingsRepository: SettingsRepository,
    private val faces: com.femi.gallery.domain.repository.FaceIndexRepository,
) : ViewModel() {

    val themeMode: StateFlow<Int> = settingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

    val autoplayVideos: StateFlow<Boolean> = settingsRepository.autoplayVideos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val muteVideos: StateFlow<Boolean> = settingsRepository.muteVideos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val playAudioInBackground: StateFlow<Boolean> = settingsRepository.playAudioInBackground
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val backgroundScan: StateFlow<Boolean> = settingsRepository.backgroundScan
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val scanOnlyWhileCharging: StateFlow<Boolean> = settingsRepository.scanOnlyWhileCharging
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val faceRecognition: StateFlow<Boolean> = settingsRepository.faceRecognition
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** Turning it off also erases everything learned about faces; turning it on starts looking again. */
    fun setFaceRecognition(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setFaceRecognition(enabled)
            if (enabled) faces.startScan() else faces.eraseAll()
        }
    }

    val semanticSearch: StateFlow<Boolean> = settingsRepository.semanticSearch
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val semanticSearchStrictness: StateFlow<com.femi.gallery.domain.repository.SemanticSearchStrictness> = settingsRepository.semanticSearchStrictness
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.femi.gallery.domain.repository.SemanticSearchStrictness.Normal)

    val albumSortOption: StateFlow<AlbumSortOption> = settingsRepository.albumSortOption
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlbumSortOption.Name)

    val hiddenAlbums: StateFlow<Set<Long>> = settingsRepository.hiddenAlbums
        .map { set -> set.mapNotNull { it.toLongOrNull() }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val albums: StateFlow<List<Album>> = observeAlbums()
        .map { list -> list.filter { it.bucketId >= 0 } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setThemeMode(mode: Int) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun setAutoplayVideos(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoplayVideos(enabled)
        }
    }

    fun setMuteVideos(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setMuteVideos(enabled)
        }
    }

    fun setPlayAudioInBackground(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setPlayAudioInBackground(enabled)
        }
    }

    fun setBackgroundScan(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setBackgroundScan(enabled)
            BackgroundScans.update(context, settingsChanged = true)
        }
    }

    fun setScanOnlyWhileCharging(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setScanOnlyWhileCharging(enabled)
            BackgroundScans.update(context, settingsChanged = true)
        }
    }

    fun setSemanticSearch(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSemanticSearch(enabled)
        }
    }

    fun setSemanticSearchStrictness(strictness: com.femi.gallery.domain.repository.SemanticSearchStrictness) {
        viewModelScope.launch {
            settingsRepository.setSemanticSearchStrictness(strictness)
        }
    }

    fun setAlbumSortOption(option: AlbumSortOption) {
        viewModelScope.launch {
            settingsRepository.setAlbumSortOption(option)
        }
    }

    fun setHiddenAlbums(hiddenIds: Set<Long>) {
        viewModelScope.launch {
            settingsRepository.setHiddenAlbums(hiddenIds.map { it.toString() }.toSet())
        }
    }

    private val _deletedEmptyAlbumsCount = MutableStateFlow<Int?>(null)
    val deletedEmptyAlbumsCount = _deletedEmptyAlbumsCount.asStateFlow()

    fun deleteEmptyAlbums() {
        viewModelScope.launch(Dispatchers.IO) {
            val emptyDirs = mutableListOf<File>()
            val roots = listOf(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            )
            roots.forEach { root ->
                if (root.exists()) {
                    findEmptyDirectories(root, emptyDirs)
                }
            }
            var deletedCount = 0
            emptyDirs.forEach { dir ->
                if (dir.delete()) {
                    deletedCount++
                }
            }
            _deletedEmptyAlbumsCount.value = deletedCount
        }
    }
    
    fun clearDeletedCount() {
        _deletedEmptyAlbumsCount.value = null
    }

    private fun findEmptyDirectories(root: File, emptyDirs: MutableList<File>) {
        if (root.isDirectory) {
            val children = root.listFiles()
            if (children != null) {
                for (child in children) {
                    if (child.isDirectory) {
                        findEmptyDirectories(child, emptyDirs)
                    }
                }
                if (children.isEmpty()) {
                    emptyDirs.add(root)
                }
            }
        }
    }
}
