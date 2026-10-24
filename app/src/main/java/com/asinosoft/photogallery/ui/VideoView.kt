package com.asinosoft.photogallery.ui

import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.asinosoft.photogallery.GalleryApp
import com.asinosoft.photogallery.R
import com.asinosoft.photogallery.data.EXTERNAL_STORAGE_ID
import com.asinosoft.photogallery.data.Media
import com.asinosoft.photogallery.model.MediaViewModel
import com.asinosoft.photogallery.ui.component.LocalDismissDrag
import com.asinosoft.photogallery.ui.component.ZoomableMediaBox
import com.asinosoft.photogallery.ui.component.dismissDragTransform
import com.asinosoft.photogallery.ui.component.isOpeningTransition
import com.asinosoft.photogallery.ui.component.mediaSharedElement
import com.asinosoft.photogallery.ui.component.rememberCachedThumbnail
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
    autoplay: Boolean = true,
    onTap: () -> Unit = {},
    model: MediaViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = context.applicationContext as GalleryApp
    var isPlaying by remember { mutableStateOf(false) }
    var buffering by remember(media.id) { mutableStateOf(false) }
    var showProgress by remember(media.id) { mutableStateOf(false) }
    LaunchedEffect(buffering) {
        showProgress = false
        if (buffering) {
            delay(PROGRESS_DELAY_MS.milliseconds)
            showProgress = true
        }
    }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var videoRatio by remember(media.id) { mutableStateOf<Float?>(null) }
    var firstFrameRendered by remember(media.id) { mutableStateOf(false) }

    /** −1 = назад, +1 = вперёд; null = скрыто */
    var seekHintDirection by remember(media.id) { mutableStateOf<Int?>(null) }
    var seekHintShown by remember(media.id) { mutableStateOf(-1) }
    var seekHintToken by remember(media.id) { mutableStateOf(0) }
    LaunchedEffect(seekHintToken) {
        if (0 == seekHintToken) return@LaunchedEffect
        delay(SEEK_HINT_MS.milliseconds)
        seekHintDirection = null
    }
    val thumbnail = rememberCachedThumbnail(media)
    val thumbnailRatio = thumbnail?.intrinsicSize
        ?.takeIf { it.isSpecified && !it.isEmpty() }
        ?.let { it.width / it.height }
    val opening = isOpeningTransition()
    val thumbnailAlpha by animateFloatAsState(
        targetValue = if (firstFrameRendered && !opening) 0f else 1f,
        animationSpec = tween(THUMBNAIL_FADE_MS),
        label = "videoThumbnail"
    )

    /**
     * ExoPlayer готовится во время анимации открытия.
     * PlayerView и смена aspectRatio — только после неё, чтобы не дёргать shared element.
     */
    var player by remember(media.id) { mutableStateOf<ExoPlayer?>(null) }
    LaunchedEffect(media.id) {
        val mediaSourceFactory = DefaultMediaSourceFactory(
            if (1L == media.storageId || EXTERNAL_STORAGE_ID == media.storageId) {
                DefaultDataSource.Factory(context)
            } else {
                OkHttpDataSource.Factory(app.httpClient)
            }
        )
        player = ExoPlayer.Builder(context.applicationContext)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        MIN_BUFFER_MS,
                        MAX_BUFFER_MS,
                        BUFFER_FOR_PLAYBACK_MS,
                        BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
                    )
                    .build()
            )
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

    var paused by remember(media.id, autoplay) { mutableStateOf(!autoplay) }
    LaunchedEffect(isActive, autoplay) {
        if (!isActive) {
            // При уходе со страницы сбрасываем в исходное: автозапуск или пауза
            paused = !autoplay
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (Lifecycle.Event.ON_STOP == event) paused = true
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(player, isActive, resumed, paused, opening) {
        player?.playWhenReady = isActive && resumed && !paused && !opening
    }

    LaunchedEffect(player, muted) {
        player?.volume = if (muted) 0f else 1f
    }

    LaunchedEffect(player) {
        val current = player ?: return@LaunchedEffect
        scope.launch {
            model.getMediaUri(media).let { uri ->
                current.setMediaItem(MediaItem.fromUri(uri))
            }
        }
    }

    LaunchedEffect(player) {
        val current = player ?: return@LaunchedEffect
        while (true) {
            position = current.currentPosition.coerceAtLeast(0L)
            duration = current.duration.takeIf { it > 0 } ?: 0L
            delay(200.milliseconds)
        }
    }

    DisposableEffect(player) {
        val current = player
        val listener =
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    buffering = Player.STATE_BUFFERING == playbackState
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    videoRatio = videoSize.aspectRatio()
                }

                override fun onRenderedFirstFrame() {
                    firstFrameRendered = true
                }
            }
        current?.addListener(listener)
        isPlaying = true == current?.isPlaying
        buffering = Player.STATE_BUFFERING == current?.playbackState
        onDispose {
            current?.removeListener(listener)
            current?.release()
        }
    }

    // Во время открытия фиксируем пропорции миниатюры — иначе onVideoSizeChanged дергает рамку.
    val frameRatio = if (opening) thumbnailRatio else (videoRatio ?: thumbnailRatio)

    Box(modifier = modifier.fillMaxSize()) {
        ZoomableMediaBox(
            aspectRatio = frameRatio,
            frameModifier = Modifier
                .dismissDragTransform(LocalDismissDrag.current)
                .mediaSharedElement(media.id),
            onTap = onTap,
            onDoubleTapSeekBackward = {
                seekBy(player, -DOUBLE_TAP_SEEK_MS) { position = it }
                seekHintShown = -1
                seekHintDirection = -1
                seekHintToken++
            },
            onDoubleTapSeekForward = {
                seekBy(player, DOUBLE_TAP_SEEK_MS) { position = it }
                seekHintShown = 1
                seekHintDirection = 1
                seekHintToken++
            },
        ) {
            // Surface/PlayerView тяжёлые: вешаем после shared element, prepare уже идёт.
            if (!opening) {
                player?.let { current ->
                    AndroidView(
                        factory = { ctx ->
                            (LayoutInflater.from(ctx)
                                .inflate(R.layout.video_player, null) as PlayerView).apply {
                                this.player = current
                            }
                        },
                        update = {
                            it.player = current
                            it.keepScreenOn = isPlaying
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            if (thumbnailAlpha > 0f) {
                thumbnail?.let {
                    Image(
                        painter = it,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alpha = thumbnailAlpha,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        if (showProgress) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        AnimatedVisibility(
            visible = null != seekHintDirection,
            enter = fadeIn(tween(100)),
            exit = fadeOut(tween(200)),
            modifier = Modifier
                .align(
                    if (-1 == seekHintShown) Alignment.CenterStart else Alignment.CenterEnd
                )
                .padding(horizontal = 20.dp)
        ) {
            SeekHintBadge(
                backward = -1 == seekHintShown,
                text = stringResource(
                    if (-1 == seekHintShown) {
                        R.string.video_seek_backward
                    } else {
                        R.string.video_seek_forward
                    }
                )
            )
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
                onPlayPause = { paused = true == player?.playWhenReady },
                onSeek = { target ->
                    position = target
                    player?.seekTo(target)
                }
            )
        }
    }
}

@Composable
private fun SeekHintBadge(
    backward: Boolean,
    text: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            painter = painterResource(
                if (backward) R.drawable.fast_rewind else R.drawable.fast_forward
            ),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(40.dp)
        )
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
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
                    contentDescription = stringResource(
                        if (isPlaying) R.string.pause else R.string.play
                    ),
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

private fun VideoSize.aspectRatio(): Float? {
    if (width <= 0 || height <= 0) return null
    val ratio = width * pixelWidthHeightRatio / height
    return if (90 == unappliedRotationDegrees || 270 == unappliedRotationDegrees) 1f / ratio else ratio
}

private fun seekBy(player: ExoPlayer?, deltaMs: Long, onPosition: (Long) -> Unit) {
    val current = player ?: return
    val duration = current.duration.takeIf { it > 0 } ?: 0L
    val position = current.currentPosition.coerceAtLeast(0L)
    val target = if (duration > 0) {
        (position + deltaMs).coerceIn(0L, duration)
    } else {
        (position + deltaMs).coerceAtLeast(0L)
    }
    onPosition(target)
    current.seekTo(target)
}

private val VIDEO_CONTROLS_BOTTOM = 160.dp
private const val THUMBNAIL_FADE_MS = 200
private const val PROGRESS_DELAY_MS = 500
private const val DOUBLE_TAP_SEEK_MS = 5_000L
private const val SEEK_HINT_MS = 700
private const val MIN_BUFFER_MS = 2_000
private const val MAX_BUFFER_MS = 30_000
private const val BUFFER_FOR_PLAYBACK_MS = 250
private const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 1_000
private val SEEK_THUMB_RADIUS = 9.dp
