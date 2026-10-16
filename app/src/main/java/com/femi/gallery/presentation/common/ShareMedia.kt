package com.femi.gallery.presentation.common

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Shares one or more photos/videos through the system share sheet. */
fun shareUris(context: Context, uris: List<Uri>) {
    if (uris.isEmpty()) return
    val send = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris.first()) }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
    }
    send.type = "*/*" // photos and videos can be mixed
    send.clipData = ClipData.newRawUri(null, uris.first()).also { clip ->
        uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
    }
    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, null))
}
