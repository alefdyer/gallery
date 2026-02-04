package com.asinosoft.gallery.model

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asinosoft.gallery.GalleryApp
import com.asinosoft.gallery.data.AlbumDao
import com.asinosoft.gallery.data.Image
import com.asinosoft.gallery.data.ImageDao
import com.asinosoft.gallery.data.ImageFetcher
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
        private val imageDao: ImageDao,
        private val fetcher: ImageFetcher,
    ) : ViewModel() {
        private val albumName = MutableStateFlow<String?>(null)
        private val rescanFlow = MutableStateFlow(false)

        val albums = albumDao.getAlbums()

        val images = imageDao.getImages()

        val isRescanning: StateFlow<Boolean> = rescanFlow

        @OptIn(ExperimentalCoroutinesApi::class)
        val albumImages = albumName.filterNotNull().flatMapLatest { imageDao.getAlbumImages(it) }

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
            images: Collection<Image>,
            context: Context,
            launcher: ActivityResultLauncher<IntentSenderRequest>,
        ) {
            Log.d(GalleryApp.TAG, "Delete ${images.count()} images")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val sender =
                    MediaStore
                        .createDeleteRequest(
                            context.contentResolver,
                            images.map { it.path.toUri() },
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
            image: Image,
            context: Context,
        ) {
            Log.d(GalleryApp.TAG, "Edit ${image.path}")
            val edit =
                Intent().apply {
                    action = Intent.ACTION_EDIT
                    data = image.path.toUri()
                }
            context.startActivity(edit)
        }

        fun share(
            images: Collection<Image>,
            context: Context,
        ) {
            Log.d(GalleryApp.TAG, "Share ${images.count()} images")
            val send =
                if (1 == images.size) {
                    Intent().apply {
                        action = Intent.ACTION_SEND
                        type = "image/jpeg"
                        putExtra(Intent.EXTRA_STREAM, images.first().path.toUri())
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                } else {
                    Intent().apply {
                        action = Intent.ACTION_SEND_MULTIPLE
                        type = "image/jpeg"

                        putParcelableArrayListExtra(
                            Intent.EXTRA_STREAM,
                            images.map { it.path.toUri() }.toCollection(ArrayList()),
                        )
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                }
            val chooser = Intent.createChooser(send, null)
            context.startActivity(chooser)
        }

        fun deleteAll(images: Collection<Image>) =
            viewModelScope.launch {
                imageDao.deleteAll(images)
                albumDao.deleteAll(albumDao.getEmptyAlbums())
            }
    }
