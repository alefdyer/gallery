package com.asinosoft.gallery.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asinosoft.gallery.data.AlbumDao
import com.asinosoft.gallery.data.Image
import com.asinosoft.gallery.data.ImageDao
import com.asinosoft.gallery.data.ImageFetcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GalleryViewModel
@Inject
constructor(
    albumDao: AlbumDao,
    private val imageDao: ImageDao,
    private val fetcher: ImageFetcher
) : ViewModel() {
    private val albumName = MutableStateFlow<String?>(null)

    val albums = albumDao.getAlbums()

    val images = imageDao.getImages()

    @OptIn(ExperimentalCoroutinesApi::class)
    val albumImages = albumName.filterNotNull().flatMapLatest { imageDao.getAlbumImages(it) }

    fun rescan() = viewModelScope.launch {
        fetcher.fetchAll()
    }

    fun setAlbumName(name: String) = viewModelScope.launch {
        albumName.emit(name)
    }

    fun deleteAll(images: Collection<Image>) = viewModelScope.launch {
        imageDao.deleteAll(images)
    }
}
