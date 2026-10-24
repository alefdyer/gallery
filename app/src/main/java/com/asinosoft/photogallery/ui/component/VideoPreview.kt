package com.asinosoft.photogallery.ui.component

import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.asinosoft.photogallery.R
import com.asinosoft.photogallery.data.Media

@OptIn(UnstableApi::class)
@Composable
fun VideoPreview(media: Media, modifier: Modifier = Modifier, onEnded: () -> Unit = {}) {
    val context = LocalContext.current
    var firstFrameRendered by remember(media.id) { mutableStateOf(false) }
    val currentOnEnded by rememberUpdatedState(onEnded)

    val player = remember(media.id) {
        ExoPlayer.Builder(context.applicationContext).build().apply {
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .build()
            volume = 0f
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = true
            media.uri?.let {
                setMediaItem(MediaItem.fromUri(it))
                prepare()
            }
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                firstFrameRendered = true
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (Player.STATE_ENDED == playbackState) currentOnEnded()
            }

            override fun onPlayerError(error: PlaybackException) {
                currentOnEnded()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            (LayoutInflater.from(ctx).inflate(R.layout.video_preview, null) as PlayerView).apply {
                this.player = player
            }
        },
        update = { it.player = player },
        onRelease = { it.player = null },
        modifier = modifier.graphicsLayer {
            clip = true
            alpha = if (firstFrameRendered) 1f else 0f
        }
    )
}
