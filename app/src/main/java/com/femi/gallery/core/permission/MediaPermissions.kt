package com.femi.gallery.core.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

object MediaPermissions {

    /** Permissions that must be granted before the gallery can show anything. */
    fun read(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    /**
     * Everything we ask for in one request. ACCESS_MEDIA_LOCATION is optional: without it,
     * GPS data is stripped from EXIF, which only affects location-based features later.
     */
    fun toRequest(): Array<String> {
        val base = read() + Manifest.permission.ACCESS_MEDIA_LOCATION
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            base + Manifest.permission.POST_NOTIFICATIONS
        } else {
            base
        }
    }
}

@Singleton
class MediaPermissionChecker @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun hasMediaAccess(): Boolean = MediaPermissions.read().all(::isGranted)

    fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
