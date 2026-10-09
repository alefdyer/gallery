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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PagerViewModel @Inject constructor(
    state: SavedStateHandle,
    albumDao: AlbumDao,
    mediaDao: MediaDao,
    @param:ApplicationContext private val context: Context,
    private val mediaService: MediaService
) : ViewModel() {
    private val storageId: Long = state["storageId"] ?: 1
    private val albumId: Long? = state["albumId"]
    private val imageId: Long = state["imageId"]!!
    private val activeFilterPackages: Set<String> = state.get<String>("filters")
        ?.split(",")
        ?.filter(String::isNotEmpty)
        ?.toSet()
        ?: emptySet()
    private val activeDateFilter: DateFilter? = state.get<String?>("date")?.toDateFilter()

    val images: StateFlow<List<Media>> = (
            albumId?.let { albumDao.getMediaInAlbum(albumId) }
                ?: mediaDao.getImages(storageId)
            )
        .map { images ->
            images
                .filter {
                    (activeFilterPackages.isEmpty() || activeFilterPackages.contains(it.owner)) &&
                            (activeDateFilter?.year == null || it.date.year == activeDateFilter.year) &&
                            (activeDateFilter?.month == null || it.date.monthValue == activeDateFilter.month) &&
                            (activeDateFilter?.day == null || it.date.dayOfMonth == activeDateFilter.day)
                }
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
