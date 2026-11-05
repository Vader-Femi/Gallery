@file:Suppress("unused", "UNUSED_PARAMETER")
package coil3

/** Stubs for tools/verify: only the Coil calls the checked files make. */
class ImageLoader {
    val memoryCache: coil3.memory.MemoryCache? = null
}

object SingletonImageLoader {
    fun get(context: android.content.Context): ImageLoader = ImageLoader()
}
