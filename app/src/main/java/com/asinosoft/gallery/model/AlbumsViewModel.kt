package com.asinosoft.gallery.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asinosoft.gallery.data.Album
import com.asinosoft.gallery.data.AlbumCategory
import com.asinosoft.gallery.data.AlbumDao
import com.asinosoft.gallery.data.CategoryWithAlbums
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class AlbumsViewModel @Inject constructor(
    private val albumDao: AlbumDao
) : ViewModel() {
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
                }.sortedBy { it.category.name }

                albumsFlow.emit(categories)
            }
        }
    }

    fun moveAlbumIntoCategory(album: Album, category: AlbumCategory) = viewModelScope.launch {
        albumDao.upsert(album.copy(categoryId = category.id))
    }

    fun moveAlbumIntoNewCategory(album: Album, categoryName: String) = viewModelScope.launch {
        val categoryId = albumDao.createCategory(AlbumCategory(name = categoryName))
        albumDao.upsert(album.copy(categoryId = categoryId))
    }
}
