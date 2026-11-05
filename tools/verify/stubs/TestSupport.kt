@file:Suppress("unused", "UNUSED_PARAMETER")
package javax.inject

/** Stub for tools/verify's unit tests: the injection annotation on the use cases. */
@Target(AnnotationTarget.CONSTRUCTOR, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
annotation class Inject

@Target(AnnotationTarget.CLASS)
annotation class Singleton
