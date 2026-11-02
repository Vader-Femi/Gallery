@file:Suppress("unused")
package androidx.lifecycle
abstract class ViewModel
val ViewModel.viewModelScope: kotlinx.coroutines.CoroutineScope get() = throw NotImplementedError()
