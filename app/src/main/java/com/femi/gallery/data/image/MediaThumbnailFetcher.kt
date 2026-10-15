package com.femi.gallery.data.image

import android.content.ContentResolver
import android.net.Uri
import android.util.Size
import coil3.ImageLoader
import coil3.Uri as CoilUri
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads grid thumbnails through [ContentResolver.loadThumbnail] instead of decoding the full
 * photo. Android keeps pre-generated thumbnails for every photo and video, so this is far
 * cheaper than decoding 12MP+ JPEGs (or extracting video frames) while scrolling.
 */
class MediaThumbnailFetcher(
    private val data: CoilUri,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        val bitmap = options.context.contentResolver.loadThumbnail(
            Uri.parse(data.toString()),
            Size(THUMBNAIL_SIZE, THUMBNAIL_SIZE),
            null,
        )
        ImageFetchResult(
            image = bitmap.asImage(),
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<CoilUri> {
        override fun create(data: CoilUri, options: Options, imageLoader: ImageLoader): Fetcher? {
            // Only MediaStore images and videos; everything else uses Coil's defaults.
            if (data.scheme != "content" || data.authority != "media") return null
            return MediaThumbnailFetcher(data, options)
        }
    }

    private companion object {
        const val THUMBNAIL_SIZE = 512
    }
}
