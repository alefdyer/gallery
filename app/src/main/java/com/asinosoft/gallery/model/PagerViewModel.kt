package com.asinosoft.gallery.model

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asinosoft.gallery.data.AlbumDao
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.data.MediaDao
import com.asinosoft.gallery.data.MediaService
import com.asinosoft.gallery.data.launchAndCatch
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PagerViewModel @Inject constructor(
    state: SavedStateHandle,
    albumDao: AlbumDao,
    mediaDao: MediaDao,
    @param:ApplicationContext private val context: Context,
    private val mediaService: MediaService
) : ViewModel() {
    private val albumId: Long? = state["albumId"]
    private val imageId: Long = state["imageId"]!!
    private val activeFilterPackages: Set<String> = state.get<String>("filters")
        ?.split(",")
        ?.filter(String::isNotEmpty)
        ?.toSet()
        ?: emptySet()

    val images: StateFlow<List<Media>> = (
            albumId?.let { albumDao.getMediaInAlbum(albumId) }
                ?: mediaDao.getImages()
            )
        .combine(flowOf(activeFilterPackages)) { images, filters ->
            if (filters.isEmpty()) images else images.filter { filters.contains(it.owner) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    val offset = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            images.collect { images ->
                val index = images.indexOfFirst { image -> image.id == imageId }
                if (index >= 0) {
                    offset.emit(index)
                }
            }
        }
    }

    fun delete(media: Media, callback: () -> Unit) = viewModelScope.launchAndCatch {
        mediaService.delete(setOf(media.id), context, callback)
    }

    fun edit(media: Media) = viewModelScope.launchAndCatch {
        mediaService.edit(media.id, context)
    }

    fun share(media: Media) = viewModelScope.launchAndCatch {
        mediaService.share(setOf(media.id), context)
    }
}
