@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.annotation

/** Stub for tools/verify. */
@Retention(AnnotationRetention.BINARY)
annotation class OptIn(vararg val markerClass: kotlin.reflect.KClass<out Annotation>)

// ---- Media3, only what the app calls; signatures checked against androidx/media tag 1.11.1 ----
