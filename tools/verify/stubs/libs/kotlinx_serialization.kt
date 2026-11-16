package kotlinx.serialization

/** Marker only: the real one makes the compiler plugin generate a serializer, which the checks do not need. */
@Target(AnnotationTarget.CLASS)
annotation class Serializable
