@file:Suppress("unused")
package javax.inject
annotation class Inject
annotation class Singleton
annotation class Qualifier
annotation class Named(val value: String = "")
interface Provider<T> { fun get(): T }
