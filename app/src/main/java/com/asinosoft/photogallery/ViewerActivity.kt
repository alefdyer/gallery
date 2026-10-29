package com.asinosoft.photogallery

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.asinosoft.photogallery.data.EXTERNAL_STORAGE_ID
import com.asinosoft.photogallery.data.Image
import com.asinosoft.photogallery.data.Media
import com.asinosoft.photogallery.data.Video
import com.asinosoft.photogallery.model.ViewerTarget
import com.asinosoft.photogallery.model.ViewerViewModel
import com.asinosoft.photogallery.ui.DummyView
import com.asinosoft.photogallery.ui.FloatingCircle
import com.asinosoft.photogallery.ui.GoogleDriveReauthHandler
import com.asinosoft.photogallery.ui.ImageView
import com.asinosoft.photogallery.ui.PagerView
import com.asinosoft.photogallery.ui.VideoView
import com.asinosoft.photogallery.ui.component.rememberAutoplayVideo
import com.asinosoft.photogallery.ui.component.rememberVideoSound
import com.asinosoft.photogallery.ui.theme.GalleryTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ViewerActivity : ComponentActivity() {
    private val viewer: ViewerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val media = intent.toMedia()
        if (null == media) {
            finish()
            return
        }

        if (hasMediaPermissions()) {
            viewer.resolve(requireNotNull(media.uri))
        }

        setContent {
            GalleryTheme {
                GoogleDriveReauthHandler()
                val target by viewer.target.collectAsState()

                when (val current = if (hasMediaPermissions()) target else ViewerTarget.NotFound) {
                    ViewerTarget.Loading -> Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                    )

                    ViewerTarget.NotFound -> SingleMediaView(media)

                    is ViewerTarget.Found -> {
                        remember(current) {
                            intent.putExtra("imageId", current.mediaId)
                            current.albumId?.let { intent.putExtra("albumId", it) }
                        }
                        PagerView(
                            onAlbumClick = { album ->
                                startActivity(
                                    Intent(this, MainActivity::class.java)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                        .putExtra(MainActivity.EXTRA_ALBUM_ID, album.id)
                                )
                            },
                            onClose = ::finish
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun SingleMediaView(media: Media) {
        var showControls by remember { mutableStateOf(true) }
        val toggleControls = { showControls = !showControls }
        val autoplayVideo = rememberAutoplayVideo()
        val videoSound = rememberVideoSound()

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            when {
                null != media.video -> VideoView(
                    media = media,
                    controlsVisible = showControls,
                    muted = !videoSound,
                    autoplay = autoplayVideo,
                    onTap = toggleControls
                )

                null != media.image -> ImageView(media, onTap = toggleControls)
                else -> DummyView()
            }

            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                FloatingCircle {
                    IconButton(onClick = ::finish) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            }
        }
    }

    private fun hasMediaPermissions(): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        return permissions.all { PackageManager.PERMISSION_GRANTED == checkSelfPermission(it) }
    }

    private fun Intent.toMedia(): Media? {
        val uri: Uri = data ?: return null
        val mimeType = type ?: contentResolver.getType(uri).orEmpty()
        return Media(
            uri = uri,
            mimeType = mimeType,
            storageId = EXTERNAL_STORAGE_ID,
            image = Image().takeIf { mimeType.startsWith("image/") },
            video = Video().takeIf { mimeType.startsWith("video/") }
        )
    }
}
