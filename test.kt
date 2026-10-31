import java.io.File
import android.os.Environment

fun getEmptyDirectories(root: File): List<File> {
    val emptyDirs = mutableListOf<File>()
    if (root.isDirectory) {
        val children = root.listFiles()
        if (children != null) {
            for (child in children) {
                if (child.isDirectory) {
                    emptyDirs.addAll(getEmptyDirectories(child))
                }
            }
            if (children.isEmpty()) {
                emptyDirs.add(root)
            }
        }
    }
    return emptyDirs
}
