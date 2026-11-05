@file:Suppress("unused")
package androidx.room
annotation class Entity(val tableName: String = "", val indices: Array<Index> = [])
annotation class Index(val value: Array<String> = [])
annotation class PrimaryKey(val autoGenerate: Boolean = false)
annotation class Insert(val onConflict: Int = 0)
annotation class Update(val onConflict: Int = 0)
annotation class Delete
object OnConflictStrategy { const val REPLACE = 1; const val IGNORE = 5; const val ABORT = 3 }
annotation class Dao
annotation class Query(val value: String)
annotation class Upsert
annotation class Transaction
annotation class Database(val entities: Array<kotlin.reflect.KClass<*>>, val version: Int, val exportSchema: Boolean = true, val autoMigrations: Array<AutoMigration> = [])
annotation class AutoMigration(val from: Int, val to: Int)
abstract class RoomDatabase
object Room {
    class Builder<T : RoomDatabase> { fun build(): T = throw NotImplementedError() }
    fun <T : RoomDatabase> databaseBuilder(context: android.content.Context, klass: Class<T>, name: String): Builder<T> = throw NotImplementedError()
}
