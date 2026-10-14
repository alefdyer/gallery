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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
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
import com.asinosoft.gallery.data.EXTERNAL_STORAGE_ID
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.model.MediaViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@OptIn(UnstableApi::class)
@Composable
fun VideoView(
    media: Media,
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
    controlsVisible: Boolean = true,
    controlsBottomPadding: Dp = VIDEO_CONTROLS_BOTTOM,
    muted: Boolean = false,
    onTap: () -> Unit = {},
    model: MediaViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = context.applicationContext as GalleryApp
    var isLoading by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }

    val player =
        remember(media.id) {
            val mediaSourceFactory = DefaultMediaSourceFactory(
                if (1L == media.storageId || EXTERNAL_STORAGE_ID == media.storageId) {
                    DefaultDataSource.Factory(context)
                } else {
                    OkHttpDataSource.Factory(app.httpClient)
                }
            )
            ExoPlayer.Builder(context.applicationContext)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()
                .apply {
                    playWhenReady = false
                    repeatMode = Player.REPEAT_MODE_ONE
                    prepare()
                }
        }

    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)

    var paused by remember(media.id) { mutableStateOf(false) }
    LaunchedEffect(isActive) {
        if (!isActive) paused = false
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (Lifecycle.Event.ON_STOP == event) paused = true
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(player, isActive, resumed, paused) {
        player.playWhenReady = isActive && resumed && !paused
    }

    LaunchedEffect(player, muted) {
        player.volume = if (muted) 0f else 1f
    }

    LaunchedEffect(player) {
        isLoading = true
        scope.launch {
            model.getMediaUri(media).let { uri ->
                player.setMediaItem(MediaItem.fromUri(uri))
            }
        }
    }

    LaunchedEffect(player) {
        while (true) {
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
            update = {
                it.player = player
                it.keepScreenOn = isPlaying
            },
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
                .padding(start = 12.dp, end = 12.dp, bottom = controlsBottomPadding)
        ) {
            VideoControls(
                isPlaying = isPlaying,
                position = position,
                duration = duration,
                onPlayPause = { paused = player.playWhenReady },
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
    position: Long,
    duration: Long,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.Black.copy(alpha = 0.45f),
        contentColor = Color.White,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
        ) {
            IconButton(onClick = onPlayPause) {
                Icon(
                    painter = painterResource(if (isPlaying) R.drawable.pause_filled else R.drawable.play_arrow),
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(28.dp)
                )
            }

            SeekBar(
                fraction = if (duration > 0) position.toFloat() / duration else 0f,
                onSeek = { fraction -> if (duration > 0) onSeek((fraction * duration).toLong()) },
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
            )

            Text(
                text = "${formatTime(position)} / ${formatTime(duration)}",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                modifier = Modifier.padding(start = 16.dp)
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
                    onSeek(fractionAt(offset.x))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset -> dragFraction = fractionAt(offset.x) },
                    onDragEnd = { dragFraction = -1f },
                    onDragCancel = { dragFraction = -1f },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragFraction = fractionAt(change.position.x)
                        onSeek(dragFraction)
                    }
                )
            }
    ) {
        val trackHeight = 5.dp.toPx()
        val thumbRadius = SEEK_THUMB_RADIUS.toPx()
        val start = thumbRadius
        val width = size.width - thumbRadius * 2
        val thumbX = start + width * shown
        val centerY = size.height / 2
        val top = centerY - trackHeight / 2
        val radius = CornerRadius(trackHeight / 2)

        drawRoundRect(
            color = Color.White.copy(alpha = 0.35f),
            topLeft = Offset(start, top),
            size = Size(width, trackHeight),
            cornerRadius = radius
        )
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(start, top),
            size = Size(thumbX - start, trackHeight),
            cornerRadius = radius
        )
        drawCircle(
            color = Color.White,
            radius = thumbRadius,
            center = Offset(thumbX, centerY)
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
        "%02d:%02d".format(minutes, seconds)
    }
}

private fun PointerInputScope.fractionAt(x: Float): Float {
    val radius = SEEK_THUMB_RADIUS.toPx()
    val width = (size.width - radius * 2).coerceAtLeast(1f)
    return ((x - radius) / width).coerceIn(0f, 1f)
}

private val VIDEO_CONTROLS_BOTTOM = 160.dp
private val SEEK_THUMB_RADIUS = 9.dp
