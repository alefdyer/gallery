package com.asinosoft.gallery.model

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asinosoft.gallery.GalleryApp
import com.asinosoft.gallery.data.AlbumDao
import com.asinosoft.gallery.data.ImageFetcher
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.data.MediaDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GalleryViewModel
    @Inject
    constructor(
        private val albumDao: AlbumDao,
        private val mediaDao: MediaDao,
        private val fetcher: ImageFetcher,
    ) : ViewModel() {
        private val albumName = MutableStateFlow<String?>(null)
        private val rescanFlow = MutableStateFlow(false)

        val albums = albumDao.getAlbums()

        val images = mediaDao.getImages()

        val isRescanning: StateFlow<Boolean> = rescanFlow

        @OptIn(ExperimentalCoroutinesApi::class)
        val albumImages = albumName.filterNotNull().flatMapLatest { mediaDao.getAlbumImages(it) }

        fun rescan() =
            viewModelScope.launch {
                rescanFlow.emit(true)
                fetcher.fetchAll()
                rescanFlow.emit(false)
            }

        fun setAlbumName(name: String) =
            viewModelScope.launch {
                albumName.emit(name)
            }

        fun delete(
            media: Collection<Media>,
            context: Context,
            launcher: ActivityResultLauncher<IntentSenderRequest>,
        ) {
            Log.d(GalleryApp.TAG, "Delete ${media.count()} images")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val sender =
                    MediaStore
                        .createDeleteRequest(
                            context.contentResolver,
                            media.map { it.uri },
                        ).intentSender

                val request =
                    IntentSenderRequest
                        .Builder(sender)
                        .setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION, 0)
                        .build()

                launcher.launch(request)
            } else {
                throw NotImplementedError()
            }
        }

        fun edit(
            media: Media,
            context: Context,
        ) {
            Log.d(GalleryApp.TAG, "Edit ${media.uri}")
            val edit =
                Intent().apply {
                    action = Intent.ACTION_EDIT
                    data = media.uri
                }
            context.startActivity(edit)
        }

        fun share(
            media: Collection<Media>,
            context: Context,
        ) {
            Log.d(GalleryApp.TAG, "Share ${media.count()} images")
            val send =
                if (1 == media.size) {
                    Intent().apply {
                        action = Intent.ACTION_SEND
                        type = "image/jpeg"
                        putExtra(Intent.EXTRA_STREAM, media.first().uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                } else {
                    Intent().apply {
                        action = Intent.ACTION_SEND_MULTIPLE
                        type = "image/jpeg"

                        putParcelableArrayListExtra(
                            Intent.EXTRA_STREAM,
                            media.map { it.uri }.toCollection(ArrayList()),
                        )
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                }
            val chooser = Intent.createChooser(send, null)
            context.startActivity(chooser)
        }

        fun deleteAll(media: Collection<Media>) =
            viewModelScope.launch {
                mediaDao.deleteAll(media)
                albumDao.deleteAll(albumDao.getEmptyAlbums())
            }

        fun moveIntoAlbum(selection: Collection<Media>, albumName: String, context: Context) {

        }
    }
