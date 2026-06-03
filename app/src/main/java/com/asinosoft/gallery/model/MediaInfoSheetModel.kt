package com.asinosoft.gallery.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asinosoft.gallery.data.AlbumDao
import com.asinosoft.gallery.data.AlbumWithCover
import com.asinosoft.gallery.data.Media
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MediaInfoSheetModel @Inject constructor(private val albumDao: AlbumDao): ViewModel() {
    private val mediaFlow = MutableStateFlow<Long>(0)
    @OptIn(ExperimentalCoroutinesApi::class)
    val albums: StateFlow<List<AlbumWithCover>> = mediaFlow.flatMapLatest { albumDao.getMediaAlbums(it) }
        .stateIn(
            viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = emptyList()
        )

    fun getMediaInfo(media: Media) {
        mediaFlow.value = media.id
    }
}