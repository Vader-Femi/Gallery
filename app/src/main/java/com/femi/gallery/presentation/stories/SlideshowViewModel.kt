package com.femi.gallery.presentation.stories

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.femi.gallery.di.DefaultDispatcher
import com.femi.gallery.domain.model.MediaItem
import com.femi.gallery.domain.model.MediaType
import com.femi.gallery.domain.model.SlideshowOrder
import com.femi.gallery.domain.search.SearchQueryParser
import com.femi.gallery.domain.usecase.GetAllMediaRefsUseCase
import com.femi.gallery.domain.usecase.GetPagedMediaUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** The photos of whatever the query describes (a story, a place) or of one album, without videos. */
@HiltViewModel
class SlideshowViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getPagedMedia: GetPagedMediaUseCase,
    private val getAllMediaRefs: GetAllMediaRefsUseCase,
    @DefaultDispatcher private val default: CoroutineDispatcher,
) : ViewModel() {

    private val criteria = SearchQueryParser
        .parse(savedStateHandle.get<String>(QUERY_ARG).orEmpty())
        .copy(mediaType = MediaType.Image)

    private val bucketId: Long? = savedStateHandle.get<Long>(BUCKET_ARG)?.takeIf { it != NO_BUCKET }

    /** In list order, loaded page by page; used when the slideshow is not shuffled. */
    val media: Flow<PagingData<MediaItem>> =
        getPagedMedia(bucketId = bucketId, search = criteria).cachedIn(viewModelScope)

    private val _shuffled = MutableStateFlow<List<String>?>(null)

    /**
     * Every photo in a random order, ready to play; null while it is being prepared and while the
     * slideshow is not shuffled. A shuffled show needs the whole list up front: asking the paged list
     * for random positions would find most of them not loaded yet and show black slides.
     */
    val shuffled: StateFlow<List<String>?> = _shuffled.asStateFlow()

    private var allUris: List<String>? = null
    private var shuffleJob: Job? = null

    /** Prepares a shuffled show for [seed] (the same seed gives the same order). */
    fun shuffle(seed: Long) {
        shuffleJob?.cancel()
        _shuffled.value = null
        shuffleJob = viewModelScope.launch {
            val uris = allUris ?: getAllMediaRefs(bucketId = bucketId, search = criteria).map { it.uri }.also { allUris = it }
            _shuffled.value = withContext(default) {
                val order = SlideshowOrder.shuffled(uris.size, seed)
                List(uris.size) { uris[order[it]] }
            }
        }
    }

    /** Back to the list order. */
    fun stopShuffle() {
        shuffleJob?.cancel()
        _shuffled.value = null
    }

    companion object {
        const val QUERY_ARG = "query"
        const val BUCKET_ARG = "bucketId"
        private const val NO_BUCKET = Long.MIN_VALUE // keep in sync with GalleryRoutes.NO_BUCKET
    }
}
