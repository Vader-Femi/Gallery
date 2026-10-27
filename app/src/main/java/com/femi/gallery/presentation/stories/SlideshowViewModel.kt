package com.femi.gallery.presentation.stories

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.femi.gallery.domain.model.MediaItem
import com.femi.gallery.domain.model.MediaType
import com.femi.gallery.domain.search.SearchQueryParser
import com.femi.gallery.domain.usecase.GetPagedMediaUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** The photos of whatever the query describes (a story, a place) or of one album, without videos. */
@HiltViewModel
class SlideshowViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getPagedMedia: GetPagedMediaUseCase,
) : ViewModel() {

    private val criteria = SearchQueryParser
        .parse(savedStateHandle.get<String>(QUERY_ARG).orEmpty())
        .copy(mediaType = MediaType.Image)

    private val bucketId: Long? = savedStateHandle.get<Long>(BUCKET_ARG)?.takeIf { it != NO_BUCKET }

    val media: Flow<PagingData<MediaItem>> =
        getPagedMedia(bucketId = bucketId, search = criteria).cachedIn(viewModelScope)

    companion object {
        const val QUERY_ARG = "query"
        const val BUCKET_ARG = "bucketId"
        private const val NO_BUCKET = Long.MIN_VALUE // keep in sync with GalleryRoutes.NO_BUCKET
    }
}
