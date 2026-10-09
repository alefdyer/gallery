package com.asinosoft.gallery.ui

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@OptIn(UnstableApi::class)
@Composable
fun VideoView(
    media: Media,
    modifier: Modifier = Modifier,
    controlsVisible: Boolean = true,
    onTap: () -> Unit = {},
    model: MediaViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = context.applicationContext as GalleryApp
    var isLoading by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }

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
            model.getMediaUri(media).let { uri ->
                player.setMediaItem(MediaItem.fromUri(uri))
            }
        }
    }

    LaunchedEffect(player) {
        while (isActive) {
            position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.takeIf { it > 0 } ?: 0L
            delay(200.milliseconds)
        }
    }

    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onIsLoadingChanged(loading: Boolean) {
                    isLoading = isLoading and loading
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }
            }
        player.addListener(listener)
        isPlaying = player.isPlaying
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .fillMaxSize()
                .clickable(interactionSource = null, indication = null, onClick = onTap)
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

        if (isLoading && !isPlaying) {
            CircularProgressIndicator(color = Color.White)
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 12.dp, end = 12.dp, bottom = VIDEO_CONTROLS_BOTTOM)
        ) {
            VideoControls(
                isPlaying = isPlaying,
                isMuted = isMuted,
                position = position,
                duration = duration,
                onPlayPause = { if (player.isPlaying) player.pause() else player.play() },
                onMuteToggle = {
                    isMuted = !isMuted
                    player.volume = if (isMuted) 0f else 1f
                },
                onSeek = { target ->
                    position = target
                    player.seekTo(target)
                }
            )
        }
    }
}

@Composable
private fun VideoControls(
    isPlaying: Boolean,
    isMuted: Boolean,
    position: Long,
    duration: Long,
    onPlayPause: () -> Unit,
    onMuteToggle: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.Black.copy(alpha = 0.45f),
        contentColor = Color.White,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPlayPause) {
                    Icon(
                        painter = painterResource(if (isPlaying) R.drawable.pause_filled else R.drawable.play_arrow),
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(28.dp)
                    )
                }

                Text(
                    text = "${formatTime(position)} / ${formatTime(duration)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )

                IconButton(onClick = onMuteToggle) {
                    Icon(
                        painter = painterResource(if (isMuted) R.drawable.volume_off else R.drawable.volume_up),
                        contentDescription = if (isMuted) "Unmute" else "Mute",
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            SeekBar(
                fraction = if (duration > 0) position.toFloat() / duration else 0f,
                onSeek = { fraction -> if (duration > 0) onSeek((fraction * duration).toLong()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .height(32.dp)
            )
        }
    }
}

@Composable
private fun SeekBar(
    fraction: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragFraction by remember { mutableFloatStateOf(-1f) }
    val shown = (if (dragFraction >= 0f) dragFraction else fraction).coerceIn(0f, 1f)

    Canvas(
        modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onSeek((offset.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset -> dragFraction = (offset.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { dragFraction = -1f },
                    onDragCancel = { dragFraction = -1f },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        onSeek(dragFraction)
                    }
                )
            }
    ) {
        val trackHeight = 6.dp.toPx()
        val gap = 3.dp.toPx()
        val thumbX = size.width * shown
        val top = (size.height - trackHeight) / 2
        val radius = CornerRadius(trackHeight / 2)

        if (thumbX - gap > 0f) {
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(0f, top),
                size = Size(thumbX - gap, trackHeight),
                cornerRadius = radius
            )
        }
        if (thumbX + gap < size.width) {
            drawRoundRect(
                color = Color.White.copy(alpha = 0.4f),
                topLeft = Offset(thumbX + gap, top),
                size = Size(size.width - thumbX - gap, trackHeight),
                cornerRadius = radius
            )
        }
        drawLine(
            color = Color.White,
            start = Offset(thumbX, 0f),
            end = Offset(thumbX, size.height),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = totalSeconds % 3600 / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private val VIDEO_CONTROLS_BOTTOM = 160.dp
