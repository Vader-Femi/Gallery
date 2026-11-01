@file:Suppress("unused")
package androidx.datastore.preferences.core
import androidx.datastore.core.DataStore
abstract class Preferences {
    class Key<T>(val name: String)
    abstract operator fun <T> get(key: Key<T>): T?
}
abstract class MutablePreferences : Preferences() {
    abstract operator fun <T> set(key: Key<T>, value: T)
}
fun intPreferencesKey(name: String) = Preferences.Key<Int>(name)
fun booleanPreferencesKey(name: String) = Preferences.Key<Boolean>(name)
fun stringPreferencesKey(name: String) = Preferences.Key<String>(name)
fun stringSetPreferencesKey(name: String) = Preferences.Key<Set<String>>(name)
suspend fun DataStore<Preferences>.edit(transform: suspend (MutablePreferences) -> Unit): Preferences = throw NotImplementedError()
