@file:Suppress("unused")
package dagger
annotation class Module
annotation class Provides
annotation class Binds
interface Lazy<T> { fun get(): T }
