import java.io.File
fun main(args: Array<String>) {
  var pass = 0; var fail = 0
  for (name in args) {
    val c = Class.forName(name)
    val inst = c.getDeclaredConstructor().newInstance()
    for (m in c.declaredMethods.sortedBy { it.name }) {
      if (m.getAnnotation(org.junit.Test::class.java) == null) continue
      try { m.invoke(inst); pass++ } catch (e: java.lang.reflect.InvocationTargetException) { fail++; println("FAIL $name.${m.name}: ${e.cause}") }
    }
  }
  println("passed=$pass failed=$fail")
}
