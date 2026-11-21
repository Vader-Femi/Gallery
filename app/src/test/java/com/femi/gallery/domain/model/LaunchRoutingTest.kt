package com.femi.gallery.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LaunchRoutingTest {

    private fun route(kind: LaunchKind, playbackDismissed: Boolean = false, openedGallery: Boolean = false) =
        LaunchRouting.effective(kind, playbackDismissed, openedGallery)

    @Test
    fun aPickRequestAlwaysShowsThePicker() {
        assertEquals(LaunchKind.PICK, route(LaunchKind.PICK))
        assertEquals(LaunchKind.PICK, route(LaunchKind.PICK, playbackDismissed = true, openedGallery = true))
    }

    @Test
    fun playbackShowsUntilItIsDismissed() {
        assertEquals(LaunchKind.VIEW_PLAYBACK, route(LaunchKind.VIEW_PLAYBACK))
        assertEquals(LaunchKind.NORMAL, route(LaunchKind.VIEW_PLAYBACK, playbackDismissed = true))
    }

    @Test
    fun aSinglePhotoShowsUntilTheGalleryIsOpenedFromIt() {
        assertEquals(LaunchKind.VIEW_EXTERNAL, route(LaunchKind.VIEW_EXTERNAL))
        assertEquals(LaunchKind.NORMAL, route(LaunchKind.VIEW_EXTERNAL, openedGallery = true))
    }

    @Test
    fun theOtherFlagsDoNotAffectARequest() {
        assertEquals(LaunchKind.VIEW_PLAYBACK, route(LaunchKind.VIEW_PLAYBACK, openedGallery = true))
        assertEquals(LaunchKind.VIEW_EXTERNAL, route(LaunchKind.VIEW_EXTERNAL, playbackDismissed = true))
    }

    @Test
    fun aNormalLaunchStaysNormal() {
        assertEquals(LaunchKind.NORMAL, route(LaunchKind.NORMAL))
        assertEquals(LaunchKind.NORMAL, route(LaunchKind.NORMAL, playbackDismissed = true, openedGallery = true))
    }

    @Test
    fun everyKindIsCovered() {
        // A new kind must be given a rule above, not silently fall through to the normal gallery.
        assertEquals(4, LaunchKind.entries.size)
    }
}
