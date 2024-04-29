package com.asinosoft.gallery.model

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asinosoft.gallery.data.Album
import com.asinosoft.gallery.data.AlbumDao
import com.asinosoft.gallery.data.Image
import com.asinosoft.gallery.data.ImageDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.system.measureTimeMillis

const val TAG = "gallery.viewmodel"

@HiltViewModel
class GalleryViewModel
@Inject
constructor(
    private val albumDao: AlbumDao,
    private val imageDao: ImageDao,
) : ViewModel() {
    init {
        viewModelScope.launch(Dispatchers.IO) {
            measureTimeMillis {
                val images = imageDao.getImages()
                _images.emit(images)
                Log.d(TAG, "initial images: ${images.count()}")
            }.also { Log.d(TAG, "initial images: $it ms") }
        }

        viewModelScope.launch(Dispatchers.IO) {
            measureTimeMillis {
                val albums = albumDao.getAlbums()
                _albums.emit(albums)
                Log.d(TAG, "initial albums: ${albums.count()}")
            }.also { Log.d(TAG, "initial albums: $it ms") }
        }
    }

    private val _albums = MutableStateFlow<List<Album>>(emptyList())

    private val _images = MutableStateFlow<List<Image>>(emptyList())

    private val _album = MutableStateFlow<Album?>(null)

    private val _albumImages = MutableStateFlow<List<Image>>(emptyList())

    val albums: StateFlow<List<Album>>
        get() = _albums

    val images: StateFlow<List<Image>>
        get() = _images

    val albumImages: StateFlow<List<Image>>
        get() = _albumImages

    fun switchToAlbum(name: String) {
        if (name == _album.value?.name) {
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            albumDao.getAlbumByName(name)?.let { album ->
                _album.emit(album)
                val images = imageDao.getAlbumImages(album.name)
                Log.d(TAG, "album images: ${images.count()}")
                _albumImages.emit(images)
            }
        }
    }
}
