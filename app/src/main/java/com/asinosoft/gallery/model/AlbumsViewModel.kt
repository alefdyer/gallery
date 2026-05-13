package com.asinosoft.gallery.model

import androidx.lifecycle.ViewModel
import com.asinosoft.gallery.data.AlbumDao
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AlbumsViewModel @Inject constructor(albumDao: AlbumDao) : ViewModel() {
    val albums = albumDao.getAlbums()
}
