package com.asinosoft.gallery.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asinosoft.gallery.data.AlbumDao
import com.asinosoft.gallery.data.CategoryWithAlbums
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class AlbumsViewModel @Inject constructor(albumDao: AlbumDao) : ViewModel() {
    val albumsFlow = MutableStateFlow<List<CategoryWithAlbums>>(listOf())
    val albums: StateFlow<List<CategoryWithAlbums>> = albumsFlow

    init {
        viewModelScope.launch {
            albumDao.getAlbums().collect {
                val categories = it.groupBy { it.category }.map {
                    CategoryWithAlbums(
                        it.key,
                        it.value
                    )
                }
                albumsFlow.emit(categories)
            }
        }
    }
}
