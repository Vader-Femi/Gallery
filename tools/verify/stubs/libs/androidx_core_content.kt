@file:Suppress("unused")
package androidx.core.content
inline fun android.content.SharedPreferences.edit(commit: Boolean = false, action: android.content.SharedPreferences.Editor.() -> Unit) {
    val editor = edit()
    action(editor)
    if (commit) editor.commit() else editor.apply()
}
object ContextCompat {
    fun checkSelfPermission(context: android.content.Context, permission: String): Int = 0
}
