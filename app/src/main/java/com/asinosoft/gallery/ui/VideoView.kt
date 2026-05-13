package com.asinosoft.gallery.ui

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.asinosoft.gallery.GalleryApp
import com.asinosoft.gallery.R
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.model.MediaViewModel
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@Composable
fun VideoView(
    media: Media,
    modifier: Modifier = Modifier,
    model: MediaViewModel = hiltViewModel(),
    onPlaying: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = context.applicationContext as GalleryApp
    var isLoading by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }

    val player =
        remember(media) {
            val mediaSourceFactory = DefaultMediaSourceFactory(
                if (1L == media.storageId) {
                    DefaultDataSource.Factory(context)
                } else {
                    OkHttpDataSource.Factory(app.httpClient)
                }
            )
            ExoPlayer.Builder(context.applicationContext)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()
                .apply {
                    playWhenReady = true
                    repeatMode = Player.REPEAT_MODE_ONE
                    prepare()
                }
        }

    LaunchedEffect(media) {
        isLoading = true
        scope.launch {
            val uri = model.getMediaUri(media)
            player.setMediaItem(MediaItem.fromUri(uri))
        }
    }

    DisposableEffect(player, onPlaying) {
        val listener =
            object : Player.Listener {
                override fun onIsLoadingChanged(loading: Boolean) {
                    isLoading = isLoading and loading
                }
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                    onPlaying(isPlaying)
                }
            }
        player.addListener(listener)
        isPlaying = player.isPlaying
        onDispose {
            player.removeListener(listener)
            player.release()
            onPlaying(false)
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .fillMaxSize()
                .clickable { if (player.isPlaying) player.pause() else player.play() }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    this.player = player
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(
            visible = !isPlaying,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White)
            } else {
                Icon(
                    if (isPlaying) {
                        painterResource(R.drawable.pause)
                    } else {
                        painterResource(R.drawable.play)
                    },
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(64.dp),
                    tint = Color.Unspecified
                )
            }
        }
    }
}
