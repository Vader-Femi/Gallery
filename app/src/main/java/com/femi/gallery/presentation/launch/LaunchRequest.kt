package com.femi.gallery.presentation.launch

import android.content.Context
import android.content.Intent
import com.femi.gallery.domain.model.PickerFilter

/** Why the app was opened: normally from the launcher, or by another app. */
sealed interface LaunchRequest {

    /** Opened from the launcher. */
    data object Normal : LaunchRequest

    /** Another app wants to show one photo or video ("Open with"). */
    data class ViewExternal(val uri: String, val isVideo: Boolean) : LaunchRequest

    /** Another app wants the user to choose photos or videos and get them back. */
    data class Pick(val filter: PickerFilter, val allowMultiple: Boolean) : LaunchRequest

    companion object {
        private val VIEW_ACTIONS = setOf(
            Intent.ACTION_VIEW,
            "com.android.camera.action.REVIEW",
            "android.provider.action.REVIEW",
        )

        fun from(intent: Intent?, context: Context): LaunchRequest {
            val action = intent?.action ?: return Normal
            return when (action) {
                in VIEW_ACTIONS -> viewRequest(intent, context)
                Intent.ACTION_GET_CONTENT, Intent.ACTION_PICK -> pickRequest(intent)
                else -> Normal
            }
        }

        private fun viewRequest(intent: Intent, context: Context): LaunchRequest {
            val uri = intent.data ?: return Normal
            val mime = intent.type
                ?: runCatching { context.contentResolver.getType(uri) }.getOrNull()
                ?: return Normal
            return when {
                mime.startsWith("video/") -> ViewExternal(uri.toString(), isVideo = true)
                mime.startsWith("image/") -> ViewExternal(uri.toString(), isVideo = false)
                else -> Normal // not something a gallery can show
            }
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
