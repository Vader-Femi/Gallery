package com.femi.gallery.domain.model

/** The ways the app can be opened; the presentation layer's `LaunchRequest` maps onto these. */
enum class LaunchKind { NORMAL, VIEW_EXTERNAL, VIEW_PLAYBACK, PICK }

/**
 * Decides which launch request the app shows. Pure, so the rules are tested: they were once broken by a change that
 * dropped [LaunchKind.PICK], and another app's file chooser then opened the whole gallery.
 */
object LaunchRouting {

    /**
     * @param requested what the incoming intent asked for.
     * @param playbackDismissed the person already closed this playback screen (the notification's video).
     * @param galleryOpenedFromExternal the person pressed "Open Gallery" in the single-photo viewer.
     */
    fun effective(requested: LaunchKind, playbackDismissed: Boolean, galleryOpenedFromExternal: Boolean): LaunchKind =
        when (requested) {
            LaunchKind.VIEW_PLAYBACK -> if (playbackDismissed) LaunchKind.NORMAL else LaunchKind.VIEW_PLAYBACK
            LaunchKind.VIEW_EXTERNAL -> if (galleryOpenedFromExternal) LaunchKind.NORMAL else LaunchKind.VIEW_EXTERNAL
            // Another app is waiting for a photo; there is nothing to dismiss, the picker stays until it is answered.
            LaunchKind.PICK -> LaunchKind.PICK
            LaunchKind.NORMAL -> LaunchKind.NORMAL
        }
}
