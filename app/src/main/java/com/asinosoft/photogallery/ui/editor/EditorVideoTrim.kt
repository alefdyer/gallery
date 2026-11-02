package com.asinosoft.photogallery.ui.editor

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.scale
import com.asinosoft.photogallery.R
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val STRIP_FRAMES = 8
private const val MIN_RANGE_MS = 1_000L
private val HANDLE_WIDTH = 18.dp
private val HANDLE_TOUCH_WIDTH = 36.dp

data class VideoStrip(val durationMs: Long, val frames: List<ImageBitmap>)

suspend fun loadVideoStrip(
    context: Context,
    uri: Uri,
    frameCount: Int,
    frameHeightPx: Int,
): VideoStrip? = withContext(Dispatchers.IO) {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: return@withContext null
        val frames = (0 until frameCount).mapNotNull { index ->
            val timeUs = duration * 1000L * index / frameCount.coerceAtLeast(1)
            retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?.let { frame ->
                    val width = (frame.width * frameHeightPx / frame.height.toFloat())
                        .roundToInt()
                        .coerceAtLeast(1)
                    val scaled = frame.scale(width, frameHeightPx)
                    if (scaled !== frame) frame.recycle()
                    scaled.asImageBitmap()
                }
        }
        VideoStrip(duration, frames)
    } catch (_: Exception) {
        null
    } finally {
        retriever.release()
    }
}

/**
 * Как в Dialer/Samsung: лента кадров, ручки start/end, playhead, play/pause и время.
 */
@Composable
fun EditorVideoTrimPanel(
    uri: Uri,
    range: Pair<Long, Long>,
    positionMs: Long,
    playing: Boolean,
    onPlayingChange: (Boolean) -> Unit,
    onRangeChange: (Pair<Long, Long>) -> Unit,
    onRangeChangeFinished: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val frameHeightPx = with(LocalDensity.current) { 56.dp.roundToPx() }
    var strip by remember(uri) { mutableStateOf<VideoStrip?>(null) }
    LaunchedEffect(uri) {
        strip = loadVideoStrip(context, uri, STRIP_FRAMES, frameHeightPx)
        val duration = strip?.durationMs ?: return@LaunchedEffect
        if (range.second !in 1..duration) {
            onRangeChange(range.first.coerceAtLeast(0L) to duration)
            onRangeChangeFinished?.invoke()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val current = strip
        if (current != null && range.second > 0L) {
            TrimStrip(current, range, positionMs, onRangeChange, onRangeChangeFinished)
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.08f))
            )
        }

        Spacer(Modifier.height(14.dp))
        Surface(
            shape = RoundedCornerShape(50),
            color = Color.White.copy(alpha = 0.12f),
            onClick = { onPlayingChange(!playing) },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(
                        if (playing) R.drawable.pause else R.drawable.play_arrow
                    ),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                val elapsed = (positionMs - range.first)
                    .coerceIn(0L, (range.second - range.first).coerceAtLeast(0L))
                Text(
                    text = "${formatTrimTime(elapsed)} / ${formatTrimTime(range.second - range.first)}",
                    color = Color.White,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun TrimStrip(
    strip: VideoStrip,
    range: Pair<Long, Long>,
    positionMs: Long,
    onRangeChange: (Pair<Long, Long>) -> Unit,
    onRangeChangeFinished: (() -> Unit)?,
) {
    val currentRange by rememberUpdatedState(range)
    val density = LocalDensity.current
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            // Иначе у краёв экрана жест «назад» перехватывает ручки trim.
            .systemGestureExclusion()
    ) {
        val handlePx = with(density) { HANDLE_WIDTH.toPx() }
        val trackPx = (constraints.maxWidth - handlePx * 2).coerceAtLeast(1f)
        val duration = strip.durationMs.coerceAtLeast(1L)
        fun msToPx(ms: Long) = handlePx + ms.toFloat() / duration * trackPx
        val pxPerMs = trackPx / duration

        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = HANDLE_WIDTH)
                .clip(RoundedCornerShape(4.dp))
        ) {
            strip.frames.forEach { frame ->
                Image(
                    bitmap = frame,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }

        val startPx = msToPx(range.first)
        val endPx = msToPx(range.second)
        Box(
            Modifier
                .offset { IntOffset(handlePx.roundToInt(), 0) }
                .width(with(density) { (startPx - handlePx).coerceAtLeast(0f).toDp() })
                .fillMaxHeight()
                .background(Color.Black.copy(alpha = 0.6f))
        )
        Box(
            Modifier
                .offset { IntOffset(endPx.roundToInt(), 0) }
                .width(with(density) { (handlePx + trackPx - endPx).coerceAtLeast(0f).toDp() })
                .fillMaxHeight()
                .background(Color.Black.copy(alpha = 0.6f))
        )
        Box(
            Modifier
                .offset { IntOffset(startPx.roundToInt(), 0) }
                .width(with(density) { (endPx - startPx).coerceAtLeast(0f).toDp() })
                .fillMaxHeight()
                .border(2.dp, Color.White.copy(alpha = 0.9f))
        )

        if (positionMs in range.first..range.second) {
            Box(
                Modifier
                    .offset {
                        IntOffset((msToPx(positionMs) - 1.5f * density.density).roundToInt(), 0)
                    }
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(Color.White)
            )
        }

        TrimHandle(
            offsetPx = startPx - handlePx,
            onDrag = { dx ->
                val (start, end) = currentRange
                val newStart = (start + (dx / pxPerMs).toLong()).coerceIn(0L, end - MIN_RANGE_MS)
                onRangeChange(newStart to end)
            },
            onDragEnd = { onRangeChangeFinished?.invoke() },
        )
        TrimHandle(
            offsetPx = endPx,
            onDrag = { dx ->
                val (start, end) = currentRange
                val newEnd =
                    (end + (dx / pxPerMs).toLong()).coerceIn(start + MIN_RANGE_MS, duration)
                onRangeChange(start to newEnd)
            },
            onDragEnd = { onRangeChangeFinished?.invoke() },
        )
    }
}

@Composable
private fun TrimHandle(
    offsetPx: Float,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val drag by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onDragEnd)
    val density = LocalDensity.current
    val touchExtraPx = with(density) { ((HANDLE_TOUCH_WIDTH - HANDLE_WIDTH) / 2).toPx() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .offset { IntOffset((offsetPx - touchExtraPx).roundToInt(), 0) }
            .width(HANDLE_TOUCH_WIDTH)
            .fillMaxHeight()
            .systemGestureExclusion()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { end() },
                    onDragCancel = { end() },
                    onHorizontalDrag = { change, dx ->
                        change.consume()
                        drag(dx)
                    },
                )
            },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(HANDLE_WIDTH)
                .fillMaxHeight()
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.9f)),
        ) {
            Box(
                Modifier
                    .size(width = 2.dp, height = 18.dp)
                    .background(Color(0xFF1E1E1E))
            )
        }
    }
}

private fun formatTrimTime(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) + 500L) / 1000L
    return String.format(Locale.ROOT, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}
