package com.asinosoft.gallery.model

import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.provider.MediaStore
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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
    val imageId: Long = state["imageId"]!!
    private val activeFilterPackages: Set<String> = state.get<String>("filters")
        ?.split(",")
        ?.filter(String::isNotEmpty)
        ?.toSet()
        ?: emptySet()
    private val activeDateFilter: DateFilter? = state.get<String?>("date")?.toDateFilter()
    private val activeMediaType: MediaType? = state.get<String?>("type")
        ?.let { type -> MediaType.entries.firstOrNull { it.name == type } }

    val favoritesOnly: Boolean = state["favorites"] ?: false

    val images: StateFlow<List<Media>> = (
            if (favoritesOnly) {
                mediaDao.getFavorites()
            } else {
                albumId?.let { albumDao.getMediaInAlbum(albumId) } ?: mediaDao.getImages(storageId)
            }
            )
        .map { images ->
            images
                .filter {
                    (activeFilterPackages.isEmpty() || activeFilterPackages.contains(it.owner)) &&
                            (activeDateFilter?.year == null || it.date.year == activeDateFilter.year) &&
                            (activeDateFilter?.month == null || it.date.monthValue == activeDateFilter.month) &&
                            (activeDateFilter?.day == null || it.date.dayOfMonth == activeDateFilter.day) &&
                            activeMediaType.matches(it)
                }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    val offset = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            offset.value = images
                .first { images -> images.any { it.id == imageId } }
                .indexOfFirst { it.id == imageId }
        }
    }

    /**
     * Для файлов из MediaStore отметку меняет система: возвращается запрос,
     * который надо показать пользователю, а база обновится после подтверждения.
     */
    fun favoriteRequest(media: Media, favorite: Boolean): IntentSender? {
        val uri = media.uri
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || LOCAL_STORAGE_ID != media.storageId || null == uri) {
            setFavorite(media, favorite)
            return null
        }
        return MediaStore.createFavoriteRequest(context.contentResolver, listOf(uri), favorite).intentSender
    }

    fun setFavorite(media: Media, favorite: Boolean) = viewModelScope.launchAndCatch {
        mediaService.setFavorite(listOf(media.id), favorite)
    }

    fun moveToPrivate(media: Media, callback: () -> Unit) = viewModelScope.launchAndCatch {
        mediaService.moveToPrivate(listOf(media.id), context, callback)
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

private const val LOCAL_STORAGE_ID = 1L
