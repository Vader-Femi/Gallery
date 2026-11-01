@file:Suppress("unused")
package androidx.datastore.preferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
class SharedPreferencesMigration(context: android.content.Context, sharedPreferencesName: String)
fun preferencesDataStore(
    name: String,
    produceMigrations: (android.content.Context) -> List<SharedPreferencesMigration> = { emptyList() },
): kotlin.properties.ReadOnlyProperty<android.content.Context, DataStore<Preferences>> = throw NotImplementedError()
