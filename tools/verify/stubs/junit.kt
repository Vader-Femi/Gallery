package org.junit
@Target(AnnotationTarget.FUNCTION) @Retention(AnnotationRetention.RUNTIME) annotation class Test
object Assert {

    fun assertEquals(e: Any?, a: Any?) { if (e != a) throw AssertionError("expected <$e> but was <$a>") }
    fun assertEquals(m: String?, e: Any?, a: Any?) { if (e != a) throw AssertionError("$m expected <$e> but was <$a>") }
    fun assertEquals(e: Double, a: Double, d: Double) { if (Math.abs(e-a) > d) throw AssertionError("expected <$e> but was <$a>") }
    fun assertEquals(e: Float, a: Float, d: Float) { if (Math.abs(e-a) > d) throw AssertionError("expected <$e> but was <$a>") }
    fun assertEquals(e: Long, a: Long) { if (e != a) throw AssertionError("expected <$e> but was <$a>") }
    fun assertEquals(m: String?, e: Float, a: Float, d: Float) { if (Math.abs(e-a) > d) throw AssertionError("$m expected <$e> but was <$a>") }
    fun assertEquals(m: String?, e: Double, a: Double, d: Double) { if (Math.abs(e-a) > d) throw AssertionError("$m expected <$e> but was <$a>") }
    fun assertNull(a: Any?) { if (a != null) throw AssertionError("expected null but was <$a>") }
    fun assertNotNull(a: Any?) { if (a == null) throw AssertionError("expected not null") }
    fun assertTrue(c: Boolean) { if (!c) throw AssertionError("expected true") }
    fun assertTrue(m: String?, c: Boolean) { if (!c) throw AssertionError(m) }
    fun assertFalse(c: Boolean) { if (c) throw AssertionError("expected false") }
    fun assertFalse(m: String?, c: Boolean) { if (c) throw AssertionError(m) }
    fun assertArrayEquals(e: FloatArray, a: FloatArray, d: Float) { if (e.size != a.size) throw AssertionError("size"); for (i in e.indices) if (Math.abs(e[i]-a[i]) > d) throw AssertionError("idx $i") }
    fun assertArrayEquals(e: ByteArray, a: ByteArray) { if (!e.contentEquals(a)) throw AssertionError("arrays differ") }
    fun assertNotEquals(message: String, u: Long, a: Long) { if (u == a) throw AssertionError(message) }
    fun fail(message: String): Nothing { throw AssertionError(message) }
    fun assertArrayEquals(e: LongArray, a: LongArray) { if (!e.contentEquals(a)) throw AssertionError("arrays differ") }
    fun assertArrayEquals(e: IntArray, a: IntArray) { if (!e.contentEquals(a)) throw AssertionError("arrays differ") }
    fun assertNotEquals(u: Any?, a: Any?) { if (u == a) throw AssertionError("values should differ: <$a>") }
    fun assertNotEquals(u: Long, a: Long) { if (u == a) throw AssertionError("values should differ: <$a>") }
}
