package com.femi.gallery.presentation.pictures

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.femi.gallery.domain.model.MediaItem
import com.femi.gallery.domain.usecase.GetPagedMediaUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class PicturesViewModel @Inject constructor(
    getPagedMedia: GetPagedMediaUseCase,
) : ViewModel() {

    val media: Flow<PagingData<MediaItem>> = getPagedMedia().cachedIn(viewModelScope)
}
