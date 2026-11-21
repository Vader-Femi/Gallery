package com.femi.gallery.presentation.viewer

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A guard on the source text, not a behaviour test: a Locked video must never get Picture-in-Picture or sound-only
 * playback (its audio would play on after the folder re-locks, and its name would show on the lock screen). The
 * wiring is Compose, which these tests cannot run, and it was once undone by an unrelated restyle of the player.
 * If one of these fails after an edit, the rule may have been broken: read the failure before changing the test.
 */
class LockedPlaybackGuardTest {

    private fun source(relative: String): String {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (root in listOf("src/main/java", "app/src/main/java")) {
                val file = File(dir, "$root/com/femi/gallery/$relative")
                if (file.exists()) return file.readText()
            }
            dir = dir.parentFile
        }
        error("Source file not found: $relative")
    }

    @Test
    fun pictureInPictureIsOffWhenBackgroundPlaybackIsNotAllowed() {
        val player = source("presentation/viewer/VideoPlayerScreen.kt")
        val declaration = Regex("""val supportsPip\s*=\s*remember\([^)]*\)\s*\{[^}]*\}""").find(player)
        assertTrue("supportsPip declaration not found", declaration != null)
        assertTrue("supportsPip must depend on allowBackground", declaration!!.value.contains("allowBackground &&"))
    }

    @Test
    fun theSoundOnlyButtonIsOnlyOfferedWhenBackgroundPlaybackIsAllowed() {
        val player = source("presentation/viewer/VideoPlayerScreen.kt")
        val uses = Regex("""onClick\s*=\s*switchToBackgroundAudio""").findAll(player).toList()
        assertEquals("exactly one button starts sound-only playback", 1, uses.size)
        val before = player.substring(0, uses.single().range.first)
        val lastGate = before.lastIndexOf("if (allowBackground)")
        assertTrue("the button must sit inside if (allowBackground)", lastGate >= 0)
        // The gate must still be open at the button: no other top-level block started in between.
        val between = before.substring(lastGate)
        assertTrue("the button is too far from its allowBackground check", between.length < 400)
    }

    @Test
    fun theViewerTurnsBackgroundPlaybackOffForTheLockedFolder() {
        val viewer = source("presentation/viewer/ViewerScreen.kt")
        assertTrue(viewer.contains("allowBackground = !viewModel.isLocked"))
    }

    @Test
    fun everyScreenThatPlaysVideoFullScreenIsKnown() {
        // A new caller of FullVideoPlayer must decide whether its videos may play in the background.
        val callers = listOf(
            "presentation/GalleryApp.kt",
            "presentation/viewer/ViewerScreen.kt",
            "presentation/viewer/ExternalViewerScreen.kt",
        )
        for (file in callers) {
            assertTrue("$file must call FullVideoPlayer", source(file).contains("FullVideoPlayer("))
        }
        val root = findSourceRoot()
        val found = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "VideoPlayerScreen.kt" }
            .filter { it.readText().contains("FullVideoPlayer(") }
            .map { it.relativeTo(root).path.replace(File.separatorChar, '/') }
            .toSet()
        assertEquals(callers.toSet(), found)
    }

    private fun findSourceRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (root in listOf("src/main/java", "app/src/main/java")) {
                val file = File(dir, "$root/com/femi/gallery")
                if (file.isDirectory) return file
            }
            dir = dir.parentFile
        }
        error("Source root not found")
    }
}
