package com.asinosoft.photogallery.model

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asinosoft.photogallery.GalleryApp
import com.asinosoft.photogallery.data.AlbumDao
import com.asinosoft.photogallery.data.Media
import com.asinosoft.photogallery.data.MediaDao
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface ViewerTarget {
    data object Loading : ViewerTarget
    data object NotFound : ViewerTarget
    data class Found(val mediaId: Long, val albumId: Long?) : ViewerTarget
}

@HiltViewModel
class ViewerViewModel @Inject constructor(
    private val mediaDao: MediaDao,
    private val albumDao: AlbumDao,
    @param:ApplicationContext private val context: Context
) : ViewModel() {
    private val _target = MutableStateFlow<ViewerTarget>(ViewerTarget.Loading)
    val target: StateFlow<ViewerTarget> = _target

    fun resolve(uri: Uri) {
        if (ViewerTarget.Loading != _target.value) return
        viewModelScope.launch {
            _target.value = runCatching { withContext(Dispatchers.IO) { find(uri) } }
                .onFailure { Log.w(GalleryApp.TAG, "Can't resolve $uri", it) }
                .getOrNull()
                ?.let { media -> ViewerTarget.Found(media.id, folderAlbumId(media)) }
                ?: ViewerTarget.NotFound
        }
    }

    private suspend fun find(uri: Uri): Media? = when {
        ContentResolver.SCHEME_FILE == uri.scheme ->
            uri.path?.let { mediaDao.findByFilename(it) }

        MediaStore.AUTHORITY == uri.authority ->
            uri.lastPathSegment?.toLongOrNull()?.let { id ->
                mediaDao.getMediaByStorageItemIds(LOCAL_STORAGE_ID, listOf(id.toString())).firstOrNull()
            } ?: findByNameAndSize(uri)

        else -> findByNameAndSize(uri)
    }

    private suspend fun findByNameAndSize(uri: Uri): Media? {
        val (name, size) = context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val name = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            val size = cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE))
            name to size
        } ?: return null

        if (name.isNullOrEmpty() || size <= 0) return null
        return mediaDao.findByNameAndSize(name, size)
    }

    private suspend fun folderAlbumId(media: Media): Long? {
        val albums = albumDao.getMediaAlbums(media.id).first().map { it.album }
        val folder = media.path.split('/').lastOrNull { it.isNotEmpty() }
        return (albums.firstOrNull { it.name == folder } ?: albums.firstOrNull())?.id
    }

    private companion object {
        const val LOCAL_STORAGE_ID = 1L
    }
}
