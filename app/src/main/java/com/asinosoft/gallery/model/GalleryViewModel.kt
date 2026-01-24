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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GalleryViewModel
    @Inject
    constructor(
        private val albumDao: AlbumDao,
        private val imageDao: ImageDao,
        private val fetcher: ImageFetcher,
    ) : ViewModel() {
        private val albumName = MutableStateFlow<String?>(null)
        private val rescanFlow = MutableStateFlow(false)

        val albums = albumDao.getAlbums()

        val images = imageDao.getImages()

        val isRescanning: StateFlow<Boolean> = rescanFlow

        @OptIn(ExperimentalCoroutinesApi::class)
        val albumImages = albumName.filterNotNull().flatMapLatest { imageDao.getAlbumImages(it) }

        fun rescan() =
            viewModelScope.launch {
                rescanFlow.emit(true)
                fetcher.fetchAll()
                rescanFlow.emit(false)
            }

        fun setAlbumName(name: String) =
            viewModelScope.launch {
                albumName.emit(name)
            }

        fun deleteAll(images: Collection<Image>) =
            viewModelScope.launch {
                imageDao.deleteAll(images)
                albumDao.deleteAll(albumDao.getEmptyAlbums())
            }
    }
