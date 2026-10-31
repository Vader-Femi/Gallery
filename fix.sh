cat << 'INNER_EOF' > app/src/main/java/com/femi/gallery/presentation/settings/SettingsViewModel.kt
package com.femi.gallery.presentation.settings

import android.content.Context
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.femi.gallery.domain.model.Album
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
) : ViewModel() {

    val albums: StateFlow<List<Album>> = observeAlbums()
        .map { list -> list.filter { it.bucketId >= 0 } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
INNER_EOF
