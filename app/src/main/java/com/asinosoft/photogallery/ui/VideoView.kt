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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil3.SingletonImageLoader
import com.asinosoft.photogallery.GalleryApp
import com.asinosoft.photogallery.R
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.net.toUri
import com.asinosoft.photogallery.data.EXTERNAL_STORAGE_ID
import com.asinosoft.photogallery.data.Media
import com.asinosoft.photogallery.data.ShareUi
import com.asinosoft.photogallery.data.ThumbnailCache
import com.asinosoft.photogallery.data.storage.StorageAuthProvider
import com.asinosoft.photogallery.model.MediaViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import com.asinosoft.photogallery.ui.component.LocalDismissDrag
import com.asinosoft.photogallery.ui.component.StatusBanner
import com.asinosoft.photogallery.ui.component.ZoomableMediaBox
import com.asinosoft.photogallery.ui.component.dismissDragTransform
import com.asinosoft.photogallery.ui.component.isOpeningTransition
import com.asinosoft.photogallery.ui.component.mediaSharedElement
import com.asinosoft.photogallery.ui.component.rememberCachedThumbnail

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
    var forceSoftware by remember(media.id) { mutableStateOf(false) }
    if (needsSoftwareVideoPlayer(media) || forceSoftware) {
        LibVlcVideoView(
            media = media,
            modifier = modifier,
            isActive = isActive,
            controlsVisible = controlsVisible,
            controlsBottomPadding = controlsBottomPadding,
            muted = muted,
            autoplay = autoplay,
            onTap = onTap,
            model = model,
        )
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = context.applicationContext as GalleryApp
    var isPlaying by remember { mutableStateOf(false) }
    var buffering by remember(media.id) { mutableStateOf(false) }
    var showProgress by remember(media.id) { mutableStateOf(false) }
    var loadProgress by remember(media.id) { mutableStateOf<Float?>(null) }
    var downloading by remember(media.id) { mutableStateOf(false) }
    var localPlaybackUri by remember(media.id) { mutableStateOf<Uri?>(null) }

    /** После «Загрузить» — сразу play, не ждём opening/гонок paused. */
    var startAfterDownload by remember(media.id) { mutableStateOf(false) }
    val isRemoteMedia = LOCAL_PHONE_STORAGE_ID != media.storageId &&
            EXTERNAL_STORAGE_ID != media.storageId
    LaunchedEffect(media.id) {
        if (!isRemoteMedia) return@LaunchedEffect
        model.cachedVideoFile(app, media)?.let { localPlaybackUri = it.toUri() }
    }
    LaunchedEffect(buffering, downloading) {
        if (downloading) {
            showProgress = true
            return@LaunchedEffect
        }
        if (buffering) {
            delay(PROGRESS_DELAY_MS.milliseconds)
            if (buffering && !downloading) {
                showProgress = true
                if (loadProgress == null) loadProgress = 0f
            }
        } else {
            showProgress = false
            loadProgress = null
        }
    }
    var position by remember { mutableLongStateOf(0L) }
    val metadataDuration = media.video?.duration?.takeIf { it > 0 } ?: 0L
    var duration by remember(media.id) { mutableLongStateOf(metadataDuration) }
    var videoRatio by remember(media.id) { mutableStateOf<Float?>(null) }
    var firstFrameRendered by remember(media.id) { mutableStateOf(false) }
    var playbackError by remember(media.id) { mutableStateOf<String?>(null) }

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
    LaunchedEffect(player, showProgress, media.id) {
        val current = player
        if (!showProgress || current == null) {
            return@LaunchedEffect
        }
        if (loadProgress == null) loadProgress = 0f
        while (true) {
            val pct = current.bufferedPercentage
            val dur = current.duration
            val buffered = current.bufferedPosition
            val fromBuffer = if (dur > 0L && buffered >= 0L) {
                (buffered.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
            } else {
                null
            }
            val fromPct = when {
                pct in 0..100 -> pct / 100f
                else -> null
            }
            val next = listOfNotNull(fromBuffer, fromPct).maxOrNull()
                ?.takeIf { it.isFinite() }
            if (next != null && next >= (loadProgress ?: 0f)) {
                loadProgress = next
            }
            delay(150.milliseconds)
        }
    }
    val isLocalMedia = 1L == media.storageId || EXTERNAL_STORAGE_ID == media.storageId

    // Плеер только на активной странице (и local, и remote): соседние Exo + буферы → OOM 512МБ.
    DisposableEffect(media.id, isActive) {
        if (!isActive) {
            player = null
            onDispose { }
        } else {
            if (!isLocalMedia) {
                SingletonImageLoader.get(context).memoryCache?.clear()
            }
            // DefaultDataSource: file:// после «Загрузить» + http через OkHttp для remote.
            val dataSourceFactory = if (isLocalMedia) {
                DefaultDataSource.Factory(context)
            } else {
                DefaultDataSource.Factory(
                    context,
                    OkHttpDataSource.Factory(app.httpClient)
                        .setDefaultRequestProperties(
                            mapOf(
                                StorageAuthProvider.HEADER_STORAGE_ID to media.storageId.toString()
                            )
                        ),
                )
            }
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    if (isLocalMedia) MIN_BUFFER_MS else REMOTE_MIN_BUFFER_MS,
                    if (isLocalMedia) MAX_BUFFER_MS else REMOTE_MAX_BUFFER_MS,
                    BUFFER_FOR_PLAYBACK_MS,
                    BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
                )
                .apply {
                    if (!isLocalMedia) {
                        setTargetBufferBytes(REMOTE_TARGET_BUFFER_BYTES)
                        setPrioritizeTimeOverSizeThresholds(false)
                    }
                }
                .build()
            val created = ExoPlayer.Builder(context.applicationContext)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                .setLoadControl(loadControl)
                .build()
                .apply {
                    playWhenReady = false
                    repeatMode = Player.REPEAT_MODE_ONE
                }
            player = created
            onDispose {
                runCatching { created.playWhenReady = false }
                runCatching { created.stop() }
                runCatching { created.clearMediaItems() }
                runCatching { created.release() }
                if (player === created) player = null
                if (!isLocalMedia) {
                    SingletonImageLoader.get(context).memoryCache?.clear()
                }
            }
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

    val shareBusy by ShareUi.status.collectAsState()
    LaunchedEffect(
        player,
        isActive,
        resumed,
        paused,
        opening,
        shareBusy,
        downloading,
        startAfterDownload
    ) {
        // Во время share / полной закачки не играем.
        // startAfterDownload: после «Загрузить» игнорируем opening.
        val ready = isActive && resumed && !paused &&
                shareBusy == null && !downloading &&
                (startAfterDownload || !opening)
        player?.playWhenReady = ready
    }

    LaunchedEffect(player, muted) {
        player?.volume = if (muted) 0f else 1f
    }

    LaunchedEffect(downloading, media.id) {
        if (!downloading) return@LaunchedEffect
        val current = player
        startAfterDownload = false
        paused = true
        current?.playWhenReady = false
        runCatching { current?.stop() }
        playbackError = null
        loadProgress = 0f
        showProgress = true
        try {
            val file = model.downloadFullVideo(app, media) { p -> loadProgress = p }
            Log.i(
                TAG,
                "Full download ok media=${media.id} path=${file.absolutePath} size=${file.length()}"
            )
            // Сначала снимаем downloading — иначе prepare-эффект выходит early и не перезапускается.
            loadProgress = 1f
            showProgress = false
            paused = false
            startAfterDownload = true
            downloading = false
            localPlaybackUri = file.toUri()
        } catch (ex: CancellationException) {
            downloading = false
            startAfterDownload = false
            throw ex
        } catch (ex: Throwable) {
            Log.w(TAG, "Full download failed media=${media.id}", ex)
            playbackError = ex.message ?: "download error"
            downloading = false
            startAfterDownload = false
            showProgress = false
        }
    }

    LaunchedEffect(player, media.id, localPlaybackUri, downloading, startAfterDownload) {
        val current = player ?: return@LaunchedEffect
        if (downloading) return@LaunchedEffect
        playbackError = null
        firstFrameRendered = false
        try {
            val local = localPlaybackUri
            val uri = if (local != null) {
                local
            } else {
                model.getMediaUri(media)
            }
            Log.i(
                TAG,
                "Exo setMedia media=${media.id} uri=$uri local=${local != null} auto=$startAfterDownload"
            )
            // mime не навязываем: для .MPG/Drive sniff надёжнее, чем video/mpeg.
            current.setMediaItem(MediaItem.fromUri(uri))
            current.prepare()
            if (startAfterDownload && isActive) {
                current.playWhenReady = true
                current.play()
                Log.i(TAG, "Exo play after download media=${media.id}")
            }
        } catch (ex: CancellationException) {
            // Свайп/уход с экрана — не ошибка воспроизведения.
            throw ex
        } catch (ex: Throwable) {
            Log.w(TAG, "Can't open video ${media.id}", ex)
            playbackError = ex.message ?: "playback error"
            startAfterDownload = false
        }
    }

    LaunchedEffect(firstFrameRendered, startAfterDownload) {
        if (firstFrameRendered && startAfterDownload) {
            startAfterDownload = false
        }
    }

    LaunchedEffect(player) {
        val current = player ?: return@LaunchedEffect
        var savedDuration = false
        while (true) {
            position = current.currentPosition.coerceAtLeast(0L)
            // Exo на MPEG-PS иногда отдаёт мусорную «огромную» duration — режем.
            val next = saneDurationMs(current.duration)
                ?: saneDurationMs(metadataDuration)
                ?: 0L
            duration = next
            if (!savedDuration && next > 0L &&
                (media.video?.duration ?: 0L) <= 0L
            ) {
                savedDuration = true
                model.saveVideoDuration(media.id, next)
            }
            delay(200.milliseconds)
        }
    }

    // Старые локальные контейнеры часто «висят» без кадра — уходим в LibVLC/FFmpeg.
    // Remote (WebDAV и т.п.): 4с буфера на сотнях МБ нормальны; LibVLC иначе качает файл целиком.
    LaunchedEffect(player, media.id, firstFrameRendered, playbackError) {
        if (player == null || firstFrameRendered || playbackError != null) return@LaunchedEffect
        val isRemote = LOCAL_PHONE_STORAGE_ID != media.storageId &&
                EXTERNAL_STORAGE_ID != media.storageId
        if (isRemote) return@LaunchedEffect
        delay(PLAYBACK_STALL_MS.milliseconds)
        if (!firstFrameRendered && playbackError == null) {
            Log.i(TAG, "Exo stall → LibVLC media=${media.id} file=${media.filename}")
            forceSoftware = true
        }
    }

    DisposableEffect(player) {
        val current = player ?: return@DisposableEffect onDispose { }
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

                override fun onTracksChanged(tracks: Tracks) {
                    val videoGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
                    if (videoGroups.isEmpty()) return
                    val supported = videoGroups.any { group ->
                        (0 until group.length).any { group.isTrackSupported(it) }
                    }
                    if (!supported && !isHardwareFriendlyContainer(media.filename)) {
                        Log.i(TAG, "Exo unsupported tracks → LibVLC media=${media.id}")
                        forceSoftware = true
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.w(TAG, "ExoPlayer error media=${media.id}", error)
                    when (error.errorCode) {
                        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                        PlaybackException.ERROR_CODE_DECODING_FAILED,
                        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                            -> {
                            // .mp4 с WebDAV не гоняем в LibVLC: там лишний download и ложные size.
                            if (isHardwareFriendlyContainer(media.filename)) {
                                playbackError = error.message?.takeIf { it.isNotBlank() }
                                    ?: "Error ${error.errorCode}"
                                buffering = false
                            } else {
                                Log.i(TAG, "Exo decode/container error → LibVLC media=${media.id}")
                                forceSoftware = true
                            }
                        }

                        else -> {
                            playbackError = error.message?.takeIf { it.isNotBlank() }
                                ?: "Error ${error.errorCode}"
                            buffering = false
                        }
                    }
                }

                override fun onRenderedFirstFrame() {
                    firstFrameRendered = true
                    playbackError = null
                    if (media.storageId != EXTERNAL_STORAGE_ID &&
                        media.storageId != LOCAL_PHONE_STORAGE_ID
                    ) {
                        scope.launch {
                            saveRemoteVideoThumbIfNeeded(context, media, model)
                        }
                    }
                }
            }
        current.addListener(listener)
        isPlaying = current.isPlaying
        buffering = Player.STATE_BUFFERING == current.playbackState
        onDispose {
            current.removeListener(listener)
            // release — в DisposableEffect(isActive), здесь только listener.
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
                seekBy(player, -DOUBLE_TAP_SEEK_MS, duration) { position = it }
                seekHintShown = -1
                seekHintDirection = -1
                seekHintToken++
            },
            onDoubleTapSeekForward = {
                seekBy(player, DOUBLE_TAP_SEEK_MS, duration) { position = it }
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

        val loadingBanner = when {
            shareBusy != null -> null
            downloading -> stringResource(R.string.video_downloading)
            isRemoteMedia && showProgress && playbackError == null ->
                stringResource(R.string.video_loading)

            else -> null
        }
        StatusBanner(
            text = loadingBanner,
            progress = loadProgress.takeIf { isRemoteMedia || downloading },
            dark = true,
            actionLabel = stringResource(R.string.video_download).takeIf {
                isRemoteMedia &&
                        !downloading &&
                        localPlaybackUri == null &&
                        loadingBanner != null &&
                        shareBusy == null
            },
            onAction = { downloading = true }.takeIf {
                isRemoteMedia && !downloading && localPlaybackUri == null
            },
        )

        playbackError?.let { message ->
            Text(
                text = message,
                color = Color.Red,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
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
internal fun VideoControls(
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

/** Exo на битых/legacy контейнерах иногда отдаёт астрономическую длительность. */
private fun saneDurationMs(value: Long): Long? =
    value.takeIf { it > 0L && it <= MAX_DISPLAY_DURATION_MS }

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

private suspend fun saveRemoteVideoThumbIfNeeded(
    context: android.content.Context,
    media: Media,
    model: MediaViewModel
) = withContext(Dispatchers.IO) {
    val file = ThumbnailCache.getFile(context, media.id)
    if (file.exists() && file.length() > 0) return@withContext
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return@withContext
    val uri = runCatching { model.getMediaUri(media) }.getOrNull() ?: return@withContext
    val retriever = MediaMetadataRetriever()
    try {
        when (uri.scheme) {
            "http", "https" -> retriever.setDataSource(uri.toString(), HashMap())
            else -> retriever.setDataSource(context, uri)
        }
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            ?.toIntOrNull() ?: return@withContext
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            ?.toIntOrNull() ?: return@withContext
        if (width <= 0 || height <= 0) return@withContext
        val side = (context.resources.displayMetrics.widthPixels / 3).coerceAtLeast(128)
        val scale = minOf(1f, side.toFloat() / minOf(width, height))
        val frame: Bitmap = retriever.getScaledFrameAtTime(
            0,
            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
            (width * scale).roundToInt().coerceAtLeast(1),
            (height * scale).roundToInt().coerceAtLeast(1)
        ) ?: return@withContext
        ThumbnailCache.saveBitmap(context, media.id, frame)
        frame.recycle()
    } catch (_: Throwable) {
    } finally {
        retriever.release()
    }
}

private fun seekBy(
    player: ExoPlayer?,
    deltaMs: Long,
    knownDuration: Long,
    onPosition: (Long) -> Unit,
) {
    val current = player ?: return
    val duration = current.duration.takeIf { it > 0 } ?: knownDuration.takeIf { it > 0 } ?: 0L
    val position = current.currentPosition.coerceAtLeast(0L)
    val target = if (duration > 0) {
        (position + deltaMs).coerceIn(0L, duration)
    } else {
        (position + deltaMs).coerceAtLeast(0L)
    }
    onPosition(target)
    current.seekTo(target)
}

private const val TAG = "VideoView"
private const val LOCAL_PHONE_STORAGE_ID = 1L
private val VIDEO_CONTROLS_BOTTOM = 160.dp
private const val THUMBNAIL_FADE_MS = 200
private const val PROGRESS_DELAY_MS = 500
private const val PLAYBACK_STALL_MS = 4_000L
private const val MAX_DISPLAY_DURATION_MS = 12L * 60 * 60 * 1000
private const val DOUBLE_TAP_SEEK_MS = 5_000L
private const val SEEK_HINT_MS = 700
private const val MIN_BUFFER_MS = 2_000
private const val MAX_BUFFER_MS = 15_000
private const val REMOTE_MIN_BUFFER_MS = 1_000
private const val REMOTE_MAX_BUFFER_MS = 3_000

/** Жёсткий потолок: иначе при свайпе WebDAV-видео куча упирается в 512МБ. */
private const val REMOTE_TARGET_BUFFER_BYTES = 2 * 1024 * 1024
private const val BUFFER_FOR_PLAYBACK_MS = 250
private const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 750
private val SEEK_THUMB_RADIUS = 9.dp
