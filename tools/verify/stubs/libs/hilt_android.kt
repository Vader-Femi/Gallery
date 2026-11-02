@file:Suppress("unused")
package dagger.hilt.android
object EntryPointAccessors { fun <T> fromApplication(context: android.content.Context, entryPoint: Class<T>): T = throw NotImplementedError() }
annotation class HiltAndroidApp
