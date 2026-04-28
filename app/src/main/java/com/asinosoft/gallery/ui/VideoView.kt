package com.asinosoft.gallery.ui

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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

@OptIn(UnstableApi::class)
@Composable
fun VideoView(media: Media, modifier: Modifier = Modifier, onPlaying: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as GalleryApp
    var isPlaying by remember { mutableStateOf(false) }

    val player =
        remember(media.uri) {
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
                    setMediaItem(MediaItem.fromUri(media.uri))
                    repeatMode = Player.REPEAT_MODE_ONE
                    prepare()
                }
        }

    DisposableEffect(player, onPlaying) {
        val listener =
            object : Player.Listener {
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
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Icon(
                if (isPlaying) {
                    painterResource(
                        R.drawable.pause
                    )
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
