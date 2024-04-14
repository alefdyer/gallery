package com.asinosoft.gallery.model

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asinosoft.gallery.data.Album
import com.asinosoft.gallery.data.AlbumDao
import com.asinosoft.gallery.data.Image
import com.asinosoft.gallery.data.ImageDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

const val TAG = "gallery.viewmodel"

@HiltViewModel
class GalleryViewModel
    @Inject
    constructor(
        private val albumDao: AlbumDao,
        private val imageDao: ImageDao,
    ) : ViewModel() {
        init {
            viewModelScope.launch {
                imageDao.getImages().collect {
                    Log.d(TAG, "initial images: ${it.count()}")
                    _images.emit(it)
                }
            }

            viewModelScope.launch {
                albumDao.getAlbums().collect {
                    Log.d(TAG, "initial albums: ${it.count()}")
                    _albums.emit(it.sortedBy { it.name.lowercase() })
                }
            }
        }

        private val _albums = MutableStateFlow<List<Album>>(emptyList())

        private val _images = MutableStateFlow<List<Image>>(emptyList())

        private val _album = MutableStateFlow<Album?>(null)

        val albums: StateFlow<List<Album>>
            get() = _albums

        val images: StateFlow<List<Image>>
            get() = _images

        fun switchToPhotos() {
            viewModelScope.launch {
                _album.emit(null)
                imageDao.getImages().collect {
                    Log.d(TAG, "all images: ${it.count()}")
                    _images.emit(it)
                }
            }
        }

        fun switchToAlbum(album: Album) {
            viewModelScope.launch {
                _album.emit(album)
                imageDao.getAlbumImages(album.name).collect {
                    Log.d(TAG, "album images: ${it.count()}")
                    _images.emit(it)
                }
            }
        }
    }
