package com.asinosoft.photogallery.model

import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asinosoft.photogallery.data.AlbumCategory
import com.asinosoft.photogallery.data.AlbumDao
import com.asinosoft.photogallery.data.AlbumWithCover
import com.asinosoft.photogallery.data.AppAnalytics
import com.asinosoft.photogallery.data.Media
import com.asinosoft.photogallery.data.MediaDao
import com.asinosoft.photogallery.data.MediaService
import com.asinosoft.photogallery.data.launchAndCatch
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class PagerViewModel @Inject constructor(
    state: SavedStateHandle,
    albumDao: AlbumDao,
    mediaDao: MediaDao,
    @param:ApplicationContext private val context: Context,
    private val mediaService: MediaService,
    private val analytics: AppAnalytics
) : ViewModel() {
    private val paramsFlow = MutableStateFlow<Params>(
        Params(
            storageId = state["storageId"] ?: 1,
            albumId = state["albumId"],
            imageId = state["imageId"]!!,
            favoritesOnly = state["favorites"] ?: false
        )
    )
    val params: StateFlow<Params> = paramsFlow
    private val activeFilterPackages: Set<String> = state.get<String>("filters")
        ?.split(",")
        ?.filter(String::isNotEmpty)
        ?.toSet()
        ?: emptySet()
    private val activeDateFilter: DateFilter? = state.get<String?>("date")?.toDateFilter()
    private val activeMediaType: MediaType? = state.get<String?>("type")
        ?.let { type -> MediaType.entries.firstOrNull { it.name == type } }

    @OptIn(ExperimentalCoroutinesApi::class)
    val images: StateFlow<List<Media>> = params.flatMapLatest { params ->
        if (params.favoritesOnly) {
            mediaDao.getFavorites()
        } else {
            params.albumId?.let { albumDao.getMediaInAlbum(params.albumId) }
                ?: mediaDao.getImages(params.storageId)
        }
    }.map { images ->
        images.filter {
            (activeFilterPackages.isEmpty() || activeFilterPackages.contains(it.owner)) &&
                    (activeDateFilter?.year == null || it.date.year == activeDateFilter.year) &&
                    (activeDateFilter?.month == null || it.date.monthValue == activeDateFilter.month) &&
                    (activeDateFilter?.day == null || it.date.dayOfMonth == activeDateFilter.day) &&
                    activeMediaType.matches(it)
        }
    }.flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )


    val offset = images.map { images ->
        images.indexOfFirst { it.id == params.value.imageId }.coerceAtLeast(0)
    }

    private val mediaFlow = MutableStateFlow<Long>(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val albums: StateFlow<List<AlbumWithCover>> = mediaFlow
        .flatMapLatest { albumDao.getMediaAlbums(it) }
        .stateIn(
            viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = emptyList()
        )

    /** Актуальная строка из БД (после EXIF: разрешение / съёмка). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val infoMedia: StateFlow<Media?> = mediaFlow
        .flatMapLatest { id ->
            if (id <= 0L) kotlinx.coroutines.flow.flowOf(null)
            else mediaDao.observeById(id)
        }
        .stateIn(
            viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = null
        )

    fun getMediaInfo(media: Media) {
        mediaFlow.value = media.id
        mediaService.enqueueRemoteImageMeta(media)
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
        return MediaStore.createFavoriteRequest(
            context.contentResolver,
            listOf(uri),
            favorite
        ).intentSender
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

    fun removeFromAlbum(mediaId: Long, albumId: Long) = viewModelScope.launchAndCatch {
        mediaService.removeFromAlbum(listOf(mediaId), albumId)

        // При удалении фото из текущего альбома переключаем пейджер на просмотр всех фото
        if (albumId == params.value.albumId) {
            paramsFlow.emit(params.value.copy(albumId = null))
        }
    }

    fun addToAlbum(mediaId: Long, albumId: Long) = viewModelScope.launchAndCatch {
        mediaService.addToAlbum(listOf(mediaId), albumId)
        analytics.albumMediaAdded()
    }

    fun addToNewAlbum(mediaId: Long, name: String, category: AlbumCategory) =
        viewModelScope.launchAndCatch {
            mediaService.addToNewAlbum(listOf(mediaId), name, category)
        }

    fun addToNewAlbumInNewCategory(mediaId: Long, name: String, categoryName: String) =
        viewModelScope.launchAndCatch {
            mediaService.addToNewAlbumInNewCategory(listOf(mediaId), name, categoryName)
   }
}

private const val LOCAL_STORAGE_ID = 1L

data class Params(
    val storageId: Long,
    val albumId: Long?,
    val imageId: Long,
    val favoritesOnly: Boolean
)
