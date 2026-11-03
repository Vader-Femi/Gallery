@file:Suppress("unused")
package androidx.lifecycle
abstract class ViewModel
val ViewModel.viewModelScope: kotlinx.coroutines.CoroutineScope get() = throw NotImplementedError()

class SavedStateHandle {
    operator fun <T> get(key: String): T? = null
    fun <T> set(key: String, value: T?) {}
}
