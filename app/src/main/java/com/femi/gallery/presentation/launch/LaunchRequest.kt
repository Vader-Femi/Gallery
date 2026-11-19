package com.femi.gallery.presentation.launch

import android.content.Context
import android.content.Intent
import com.femi.gallery.domain.model.ExternalMediaRef
import com.femi.gallery.domain.model.ExternalViewSets
import com.femi.gallery.domain.model.PickerFilter

/** Why the app was opened: normally from the launcher, or by another app. */
sealed interface LaunchRequest {

    /** Opened from the launcher. */
    data object Normal : LaunchRequest

    /**
     * Another app wants to show one photo or video ("Open with"). [items] are the photos and videos the sender
     * provided with it (its clip), in its order, [startIndex] being the one opened; empty or one item when it sent
     * only that one.
     */
    data class ViewExternal(
        val uri: String,
        val isVideo: Boolean,
        val items: List<ExternalMediaRef> = emptyList(),
        val startIndex: Int = 0,
    ) : LaunchRequest

    /** Video playback opened directly from system notification. */
    data class ViewPlayback(val uri: String, val timestamp: Long = System.currentTimeMillis()) : LaunchRequest

    /** Another app wants the user to choose photos or videos and get them back. */
    data class Pick(val filter: PickerFilter, val allowMultiple: Boolean) : LaunchRequest

    companion object {
        private val VIEW_ACTIONS = setOf(
            Intent.ACTION_VIEW,
            "com.android.camera.action.REVIEW",
            "android.provider.action.REVIEW",
        )

        @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
        fun from(intent: Intent?, context: Context): LaunchRequest {
            if (intent == null) return Normal
            if (intent.action == com.femi.gallery.playback.VideoPlaybackService.ACTION_PLAYBACK ||
                intent.getBooleanExtra(com.femi.gallery.playback.VideoPlaybackService.EXTRA_PLAYBACK_NOTIFICATION, false)
            ) {
                // MainActivity is exported, so any app can send this intent: it is only trusted for the video that
                // the playback service is holding right now (what the notification is for), never for another URI.
                val activeUri = com.femi.gallery.playback.VideoPlaybackService.getActivePlaybackUri()
                val playbackUri = intent.data?.toString() ?: activeUri
                if (playbackUri != null && activeUri != null &&
                    android.net.Uri.parse(playbackUri) == android.net.Uri.parse(activeUri)
                ) {
                    return ViewPlayback(uri = playbackUri, timestamp = System.currentTimeMillis())
                }
            }
            val action = intent.action ?: return Normal
            return when (action) {
                in VIEW_ACTIONS -> viewRequest(intent, context)
                Intent.ACTION_GET_CONTENT, Intent.ACTION_PICK -> pickRequest(intent)
                else -> Normal
            }
        }

        private fun viewRequest(intent: Intent, context: Context): LaunchRequest {
            val uri = intent.data ?: return Normal
            // MainActivity is exported: an address of this app's own providers (the Locked folder) must not be
            // opened because another app named it, the app would read it with its own rights.
            if (!ExternalViewSets.isAllowedPrimary(uri.toString(), context.packageName)) return Normal
            val mime = intent.type
                ?: runCatching { context.contentResolver.getType(uri) }.getOrNull()
                ?: return Normal
            val isVideo = mime.startsWith("video/")
            if (!isVideo && !mime.startsWith("image/")) return Normal // not something a gallery can show
            val selected = uri.toString()
            // Only what the sender provided can be swiped to, never other photos of the library.
            val clip = intent.clipData
            val clipUris = if (clip == null) emptyList() else (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri?.toString() }
            val set = ExternalViewSets.assemble(
                selectedUri = selected,
                selectedIsVideo = isVideo,
                candidates = ExternalViewSets.candidates(selected, clipUris, context.packageName),
            ) { candidate -> runCatching { context.contentResolver.getType(android.net.Uri.parse(candidate)) }.getOrNull() }
            return ViewExternal(selected, isVideo, set.items, set.startIndex)
        }

        private fun pickRequest(intent: Intent): LaunchRequest {
            // EXTRA_MIME_TYPES narrows down a generic type such as a wildcard type.
            val requested = intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.toList().orEmpty()
                .ifEmpty { listOfNotNull(intent.type) }
                .ifEmpty { listOfNotNull(typeFromCollectionUri(intent.data?.toString())) }
            return Pick(
                filter = PickerFilter.fromMimeTypes(requested),
                allowMultiple = intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false),
            )
        }

        /** ACTION_PICK often names a MediaStore collection instead of a type. */
        private fun typeFromCollectionUri(uri: String?): String? = when {
            uri == null -> null
            uri.contains("/video/") -> "video/*"
            uri.contains("/images/") -> "image/*"
            else -> null
        }
    }
}
