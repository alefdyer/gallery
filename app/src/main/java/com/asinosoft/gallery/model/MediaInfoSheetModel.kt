package com.asinosoft.gallery.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asinosoft.gallery.data.AlbumCategory
import com.asinosoft.gallery.data.AlbumDao
import com.asinosoft.gallery.data.AlbumWithCover
import com.asinosoft.gallery.data.AppAnalytics
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.data.MediaService
import com.asinosoft.gallery.data.launchAndCatch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class MediaInfoSheetModel @Inject constructor(
    private val albumDao: AlbumDao,
    private val mediaService: MediaService,
    private val analytics: AppAnalytics
) : ViewModel() {
    private val mediaFlow = MutableStateFlow<Long>(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val albums: StateFlow<List<AlbumWithCover>> = mediaFlow
        .flatMapLatest { albumDao.getMediaAlbums(it) }
        .stateIn(
            viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = emptyList()
        )

    fun getMediaInfo(media: Media) {
        mediaFlow.value = media.id
    }

    fun removeFromAlbum(mediaId: Long, albumId: Long) = viewModelScope.launchAndCatch {
        mediaService.removeFromAlbum(listOf(mediaId), albumId)
    }

    fun addToAlbum(mediaId: Long, albumId: Long) = viewModelScope.launchAndCatch {
        mediaService.addToAlbum(listOf(mediaId), albumId)
        analytics.albumMediaAdded()
    }

    fun addToNewAlbum(mediaId: Long, name: String, category: AlbumCategory) =
        viewModelScope.launchAndCatch {
            mediaService.addToNewAlbum(listOf(mediaId), name, category)
        }

    fun addToNewAlbumInNewCategory(mediaId: Long, name: String, categoryName: String) =
        viewModelScope.launchAndCatch {
            mediaService.addToNewAlbumInNewCategory(listOf(mediaId), name, categoryName)
        }
}
