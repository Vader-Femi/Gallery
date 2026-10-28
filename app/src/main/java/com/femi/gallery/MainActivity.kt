package com.femi.gallery

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.femi.gallery.core.perf.FrameMonitor
import com.femi.gallery.presentation.GalleryApp
import com.femi.gallery.presentation.launch.LaunchRequest
import com.femi.gallery.ui.theme.GalleryTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
// AppCompatActivity (subclass of FragmentActivity) because BiometricPrompt needs one and per-app language needs AppCompatDelegate.
class MainActivity : AppCompatActivity() {

    /** Logs (tag "Frames") which screen drops frames and why; silent while the app is smooth. */
    private val frameMonitor by lazy { FrameMonitor(window) }

    /** Intercepts volume up key when viewing a muted video to unmute automatically. */
    var viewerVolumeHandler: (() -> Unit)? = null

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP && event.action == android.view.KeyEvent.ACTION_DOWN) {
            viewerVolumeHandler?.invoke()
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Opened from the launcher, or by another app to view or pick photos.
        val launch = LaunchRequest.from(intent, this)
        setContent {
            GalleryTheme {
                GalleryApp(
                    launch = launch,
                    onPick = ::finishWithPicked,
                    onFinish = ::finish,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        frameMonitor.start()
    }

    override fun onStop() {
        frameMonitor.stop()
        super.onStop()
    }

    /** Hands the chosen photos or videos back to the app that asked for them. */
    private fun finishWithPicked(uris: List<String>) {
        if (uris.isEmpty()) {
            setResult(RESULT_CANCELED)
        } else {
            val parsed = uris.map(Uri::parse)
            val clip = ClipData.newRawUri(null, parsed.first()).also { data ->
                parsed.drop(1).forEach { data.addItem(ClipData.Item(it)) }
            }
            val result = Intent().apply {
                data = parsed.first()
                clipData = clip
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            setResult(RESULT_OK, result)
        }
        finish()
    }
}
