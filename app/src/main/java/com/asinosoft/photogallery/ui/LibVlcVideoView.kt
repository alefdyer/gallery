package com.asinosoft.photogallery.ui

import android.media.MediaMetadataRetriever
import android.util.Log
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.asinosoft.photogallery.GalleryApp
import com.asinosoft.photogallery.data.Media
import com.asinosoft.photogallery.data.VlcMediaCache
import com.asinosoft.photogallery.data.bestVideoDurationMs
import com.asinosoft.photogallery.data.plausibleVideoDurationMs
import com.asinosoft.photogallery.model.MediaViewModel
import com.asinosoft.photogallery.ui.component.LocalDismissDrag
import com.asinosoft.photogallery.ui.component.ZoomableMediaBox
import com.asinosoft.photogallery.ui.component.dismissDragTransform
import com.asinosoft.photogallery.ui.component.isOpeningTransition
import com.asinosoft.photogallery.ui.component.mediaSharedElement
import com.asinosoft.photogallery.ui.component.rememberCachedThumbnail
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds
import okhttp3.Request
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media as VlcMedia
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * Soft-decode через LibVLC (FFmpeg) для MPEG/.MPG.
 * Remote (Drive): сначала качаем своим OkHttp с Bearer — LibVLC http-headers к Drive
 * часто не проходит, из‑за этого вечный спиннер.
 */
@Composable
fun LibVlcVideoView(
    media: Media,
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
    controlsVisible: Boolean = true,
    controlsBottomPadding: Dp = 160.dp,
    muted: Boolean = false,
    autoplay: Boolean = true,
    onTap: () -> Unit = {},
    model: MediaViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val app = appContext as GalleryApp

    var isPlaying by remember { mutableStateOf(false) }
    var buffering by remember(media.id) { mutableStateOf(true) }
    var showProgress by remember(media.id) { mutableStateOf(false) }
    LaunchedEffect(buffering) {
        showProgress = false
        if (buffering) {
            delay(400.milliseconds)
            showProgress = true
        }
    }

    val apiDuration = bestVideoDurationMs(
        media.size,
        media.filename,
        media.mimeType,
        media.video?.duration ?: 0L,
    )
    val apiTrusted = plausibleVideoDurationMs(media.size, media.video?.duration ?: 0L) != null
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember(media.id) { mutableLongStateOf(apiDuration) }

    /** true = длительность из файла/конца ролика, не оценка и не сырой VLC. */
    var durationTrusted by remember(media.id) { mutableStateOf(apiTrusted) }
    var videoRatio by remember(media.id) { mutableFloatStateOf(0f) }
    var firstFrameRendered by remember(media.id) { mutableStateOf(false) }
    var playbackError by remember(media.id) { mutableStateOf<String?>(null) }

    val thumbnail = rememberCachedThumbnail(media)
    val thumbnailRatio = thumbnail?.intrinsicSize
        ?.takeIf { it.isSpecified && !it.isEmpty() }
        ?.let { it.width / it.height }
    val opening = isOpeningTransition()
    val thumbnailAlpha by animateFloatAsState(
        targetValue = if (firstFrameRendered && !opening) 0f else 1f,
        animationSpec = tween(200),
        label = "vlcThumbnail"
    )

    var libVlc by remember(media.id) { mutableStateOf<LibVLC?>(null) }
    var vlcPlayer by remember(media.id) { mutableStateOf<MediaPlayer?>(null) }
    var videoLayout by remember(media.id) { mutableStateOf<VLCVideoLayout?>(null) }
    var mediaReady by remember(media.id) { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    var paused by remember(media.id, autoplay) { mutableStateOf(!autoplay) }

    LaunchedEffect(isActive, autoplay) {
        if (!isActive) paused = !autoplay
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (Lifecycle.Event.ON_STOP == event) paused = true
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(media.id) {
        // hw=none: MPEG-2 на телефоне без аппаратного декодера
        val options = arrayListOf(
            "--network-caching=3000",
            "--file-caching=1500",
            "--avcodec-hw=none",
            "--no-audio-time-stretch",
        )
        val lib = LibVLC(appContext, options)
        val player = MediaPlayer(lib)
        libVlc = lib
        vlcPlayer = player
        mediaReady = false
        Log.i(TAG, "LibVLC created for media=${media.id} file=${media.filename}")

        val eventListener = MediaPlayer.EventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Opening -> buffering = true
                MediaPlayer.Event.Playing -> {
                    isPlaying = true
                    buffering = false
                    firstFrameRendered = true
                    playbackError = null
                }

                MediaPlayer.Event.Paused, MediaPlayer.Event.Stopped -> isPlaying = false
                MediaPlayer.Event.Buffering -> {
                    buffering = event.buffering in 0f..<100f
                }

                MediaPlayer.Event.EncounteredError -> {
                    buffering = false
                    isPlaying = false
                    playbackError = "VLC playback error"
                    Log.w(TAG, "LibVLC EncounteredError media=${media.id}")
                }

                MediaPlayer.Event.EndReached -> {
                    isPlaying = false
                    val endedAt = player.time.takeIf { it > 0 } ?: position
                    plausibleVideoDurationMs(media.size, endedAt)?.let { real ->
                        if (!durationTrusted || duration <= 0L || real < duration) {
                            duration = real
                            durationTrusted = true
                            Log.i(TAG, "Duration from EndReached: $real ms")
                        }
                    }
                    player.stop()
                    player.play()
                }

                MediaPlayer.Event.LengthChanged -> {
                    // MPEG-PS: VLC часто врёт length (часы вместо минут) — не затираем доверенную.
                    mergeVlcDuration(
                        duration,
                        durationTrusted,
                        player.length,
                        media.size,
                    )?.let { (next, trusted) ->
                        duration = next
                        durationTrusted = trusted
                    }
                }

                MediaPlayer.Event.Vout -> {
                    firstFrameRendered = true
                    buffering = false
                }
            }
        }
        player.setEventListener(eventListener)

        onDispose {
            player.setEventListener(null)
            runCatching { player.stop() }
            runCatching { player.detachViews() }
            runCatching { player.release() }
            runCatching { lib.release() }
            vlcPlayer = null
            libVlc = null
            videoLayout = null
            mediaReady = false
        }
    }

    // Готовим media (скачивание Drive → файл), поверхность может подключиться позже.
    LaunchedEffect(vlcPlayer, media.id) {
        val player = vlcPlayer ?: return@LaunchedEffect
        val lib = libVlc ?: return@LaunchedEffect
        playbackError = null
        buffering = true
        mediaReady = false
        try {
            val source = model.getPlaybackSource(media)
            Log.i(TAG, "Playback source scheme=${source.uri.scheme} headers=${source.headers.keys}")
            val playUri = resolvePlayableUri(app, media, source.uri, source.headers)
            Log.i(TAG, "Play path/uri=$playUri")
            val fileSize = when {
                playUri.startsWith("/") -> File(playUri).length()
                playUri.startsWith("file:") -> File(playUri.removePrefix("file://")).length()
                else -> media.size
            }.takeIf { it > 0 } ?: media.size
            probeFileDuration(playUri, fileSize)?.let { probed ->
                duration = probed
                durationTrusted = true
                Log.i(TAG, "Trusted duration from file: $probed ms")
            }
            val vlcMedia = when {
                playUri.startsWith("/") || playUri.startsWith("file:") ->
                    VlcMedia(lib, playUri.removePrefix("file://"))

                else ->
                    VlcMedia(lib, android.net.Uri.parse(playUri))
            }
            player.media = vlcMedia
            vlcMedia.release()
            player.volume = if (muted) 0 else 100
            mediaReady = true
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Throwable) {
            Log.w(TAG, "LibVLC open failed media=${media.id}", ex)
            playbackError = ex.message ?: "playback error"
            buffering = false
        }
    }

    LaunchedEffect(vlcPlayer, videoLayout, mediaReady) {
        val player = vlcPlayer ?: return@LaunchedEffect
        val layout = videoLayout ?: return@LaunchedEffect
        if (!mediaReady) return@LaunchedEffect
        runCatching {
            player.attachViews(layout, null, false, false)
            Log.i(TAG, "attachViews ok")
        }.onFailure { Log.w(TAG, "attachViews failed", it) }
    }

    LaunchedEffect(vlcPlayer, mediaReady, isActive, resumed, paused) {
        val player = vlcPlayer ?: return@LaunchedEffect
        if (!mediaReady) return@LaunchedEffect
        // Не ждём конец shared-element: иначе VLC так и не стартует.
        val shouldPlay = isActive && resumed && !paused
        Log.i(TAG, "playState shouldPlay=$shouldPlay isPlaying=${player.isPlaying}")
        if (shouldPlay) {
            if (!player.isPlaying) player.play()
        } else if (player.isPlaying) {
            player.pause()
        }
    }

    LaunchedEffect(vlcPlayer, muted) {
        vlcPlayer?.volume = if (muted) 0 else 100
    }

    LaunchedEffect(media.id, duration, durationTrusted) {
        if (durationTrusted && duration > 0L) {
            model.saveVideoDuration(media.id, duration)
        }
    }

    LaunchedEffect(vlcPlayer) {
        val player = vlcPlayer ?: return@LaunchedEffect
        while (true) {
            val time = player.time
            if (time >= 0) position = time
            mergeVlcDuration(
                duration,
                durationTrusted,
                player.length,
                media.size,
            )?.let { (next, trusted) ->
                duration = next
                durationTrusted = trusted
            }
            delay(200.milliseconds)
        }
    }

    // Если так и не стартовали — покажем ошибку вместо вечного спиннера
    LaunchedEffect(media.id, firstFrameRendered, playbackError, mediaReady) {
        if (playbackError != null || firstFrameRendered) return@LaunchedEffect
        delay(20_000.milliseconds)
        if (!firstFrameRendered && playbackError == null) {
            playbackError = "Timeout loading video"
            buffering = false
            Log.w(TAG, "Timeout media=${media.id} ready=$mediaReady")
        }
    }

    val frameRatio = when {
        opening -> thumbnailRatio
        videoRatio > 0f -> videoRatio
        else -> thumbnailRatio
    }

    Box(modifier = modifier.fillMaxSize()) {
        ZoomableMediaBox(
            aspectRatio = frameRatio,
            frameModifier = Modifier
                .dismissDragTransform(LocalDismissDrag.current)
                .mediaSharedElement(media.id),
            onTap = onTap,
            onDoubleTapSeekBackward = {
                val player = vlcPlayer ?: return@ZoomableMediaBox
                val target = (player.time - 5_000L).coerceAtLeast(0L)
                player.time = target
                position = target
            },
            onDoubleTapSeekForward = {
                val player = vlcPlayer ?: return@ZoomableMediaBox
                val lim = duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                val target = (player.time + 5_000L).coerceAtMost(lim)
                player.time = target
                position = target
            },
        ) {
            // Surface сразу — не ждём конец transition (иначе media не к чему attach).
            AndroidView(
                factory = { ctx ->
                    VLCVideoLayout(ctx).also { layout ->
                        layout.layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        videoLayout = layout
                    }
                },
                update = { layout ->
                    if (videoLayout !== layout) videoLayout = layout
                },
                modifier = Modifier.fillMaxSize(),
            )

            if (thumbnailAlpha > 0f) {
                thumbnail?.let {
                    Image(
                        painter = it,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alpha = thumbnailAlpha,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        if (showProgress && playbackError == null) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        playbackError?.let { message ->
            Text(
                text = message,
                color = Color.Red,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
            )
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 12.dp, end = 12.dp, bottom = controlsBottomPadding),
        ) {
            VideoControls(
                isPlaying = isPlaying,
                position = position,
                duration = duration,
                onPlayPause = { paused = vlcPlayer?.isPlaying == true },
                onSeek = { target ->
                    position = target
                    vlcPlayer?.time = target
                },
            )
        }
    }
}

/**
 * Local/content — как есть. Remote http(s) — скачать OkHttp (с auth) во временный файл,
 * иначе LibVLC не протаскивает Bearer на редиректы Drive.
 */
private suspend fun resolvePlayableUri(
    app: GalleryApp,
    media: Media,
    uri: android.net.Uri,
    headers: Map<String, String>,
): String = withContext(Dispatchers.IO) {
    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") {
        return@withContext uri.toString()
    }
    if (media.size > MAX_DOWNLOAD_BYTES) {
        error("File too large for software player (${media.size} bytes)")
    }
    val out = VlcMediaCache.fileFor(app, media.id, media.filename)
    if (out.exists() && out.length() > 0 &&
        (media.size <= 0 || out.length() == media.size)
    ) {
        VlcMediaCache.touch(out)
        Log.i(TAG, "Using cached ${out.length()} bytes")
        return@withContext out.absolutePath
    }
    // Место под новый файл: вытесняем старые до (лимит − размер).
    VlcMediaCache.trim(app)
    val tmp = File(out.parentFile, "${out.name}.part")
    tmp.delete()
    val request = Request.Builder().url(uri.toString()).get().apply {
        headers.forEach { (k, v) -> header(k, v) }
    }.build()
    Log.i(TAG, "Downloading $uri → ${out.name}")
    app.httpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            error("Download HTTP ${response.code}")
        }
        val body = response.body ?: error("Empty body")
        FileOutputStream(tmp).use { output ->
            body.byteStream().use { input -> input.copyTo(output) }
        }
    }
    if (out.exists()) out.delete()
    if (!tmp.renameTo(out)) {
        tmp.copyTo(out, overwrite = true)
        tmp.delete()
    }
    VlcMediaCache.touch(out)
    VlcMediaCache.trim(app, keep = out)
    Log.i(TAG, "Downloaded ${out.length()} bytes")
    out.absolutePath
}

/**
 * Local/file duration via MediaMetadataRetriever (для .MPG надёжнее, чем VLC length).
 */
private fun probeFileDuration(pathOrUri: String, sizeBytes: Long): Long? {
    val retriever = MediaMetadataRetriever()
    return try {
        when {
            pathOrUri.startsWith("/") -> retriever.setDataSource(pathOrUri)
            pathOrUri.startsWith("file:") ->
                retriever.setDataSource(pathOrUri.removePrefix("file://"))

            else -> return null
        }
        val raw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
            ?: return null
        plausibleVideoDurationMs(sizeBytes, raw)
    } catch (_: Throwable) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

/** @return Pair(durationMs, trusted) или null если не менять. */
private fun mergeVlcDuration(
    current: Long,
    trusted: Boolean,
    vlcLen: Long,
    sizeBytes: Long,
): Pair<Long, Boolean>? {
    val sane = plausibleVideoDurationMs(sizeBytes, vlcLen) ?: return null
    if (trusted && current > 0) {
        // VLC завысил в разы (типично для MPEG-PS) — игнор.
        if (sane > current * DURATION_INFLATE_FACTOR) return null
        if (sane < current) return sane to true
        return null
    }
    return sane to false
}

private const val TAG = "LibVlcVideo"
private const val MAX_DOWNLOAD_BYTES = VlcMediaCache.MAX_BYTES

/** Во сколько раз VLC length может быть больше доверенной, прежде чем отбросить. */
private const val DURATION_INFLATE_FACTOR = 1.5
