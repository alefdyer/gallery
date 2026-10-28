package com.asinosoft.photogallery.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.rememberConstraintsSizeResolver
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.video.videoFrameMillis
import com.asinosoft.photogallery.R
import com.asinosoft.photogallery.data.EXTERNAL_STORAGE_ID
import com.asinosoft.photogallery.data.Media
import com.asinosoft.photogallery.data.ThumbnailCache
import com.asinosoft.photogallery.model.MediaViewModel
import com.asinosoft.photogallery.ui.theme.FavoriteRed
import com.asinosoft.photogallery.ui.theme.isDarkColorScheme
import kotlinx.coroutines.launch

private const val LOCAL_STORAGE_ID = 1L

/**
 * Подложка клетки до загрузки миниатюры: мягкий вертикальный градиент в цвет темы,
 * как заглушки в Google Фото. Один градиент на клетку, перерисовка дешёвая и
 * быстрой перемотке не мешает.
 */
@Composable
internal fun rememberPlaceholderBrush(): Brush {
    val dark = isDarkColorScheme()
    return remember(dark) {
        Brush.verticalGradient(
            if (dark) {
                listOf(Color(0xFF32323A), Color(0xFF202027))
            } else {
                listOf(Color(0xFFF5F6F8), Color(0xFFE1E4E9))
            }
        )
    }
}

/** Плитка-заглушка без иконки — для «дальних» клеток при прыжке ползунком. */
@Composable
fun ThumbnailPlaceholderTile(modifier: Modifier = Modifier) {
    Box(modifier.background(rememberPlaceholderBrush()))
}

@Composable
fun MediaThumbnail(
    media: Media,
    model: MediaViewModel,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 1f,
    selected: Set<Long> = setOf(),
    selectionMode: Boolean = false,
    playPreview: Boolean = false,
    showVideoBadge: Boolean = true,
    persistentCache: Boolean = true,
    sharedElement: Boolean = false,
    onClick: (Media) -> Unit = {},
    onSelect: (Media) -> Unit = {},
    onPreviewEnded: (Media) -> Unit = {}
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clipToBounds()
            .background(rememberPlaceholderBrush())
            .clickable { if (selectionMode) onSelect(media) else onClick(media) }
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val size = rememberConstraintsSizeResolver()

        val cacheKey = remember(media.id) { "media-${media.id}" }
        val cacheFile = remember(media.id) { ThumbnailCache.getFile(context, media.id) }
        val cached = remember(media.id) { persistentCache && cacheFile.exists() && cacheFile.length() > 0 }
        val isRemoteVideo = media.video != null &&
                LOCAL_STORAGE_ID != media.storageId &&
                EXTERNAL_STORAGE_ID != media.storageId
        val immediateData: Any? = remember(media, cached) {
            when {
                cached -> cacheFile
                // Удалённое видео без кэша: не кормим Coil http-URL — VideoFrameDecoder
                // скачивает ролик целиком и ловит OOM на десятках ГБ.
                isRemoteVideo -> null
                LOCAL_STORAGE_ID == media.storageId || EXTERNAL_STORAGE_ID == media.storageId -> media.uri
                else -> null
            }
        }
        val remoteData by produceState<Any?>(null, media, immediateData, isRemoteVideo) {
            if (null != immediateData || isRemoteVideo) {
                value = null
                return@produceState
            }
            value = model.getThumbnailUri(media)
        }
        val data = immediateData ?: remoteData

        var loaded by remember(media.id) { mutableStateOf(cached) }
        val request = remember(data, cacheKey, media.video != null) {
            if (null == data) return@remember null
            ImageRequest.Builder(context)
                .data(data)
                .size(size)
                .apply {
                    // Кадр из файла — только для локальных/кэшированных источников.
                    if (media.video != null) videoFrameMillis(0)
                }
                .memoryCacheKey(cacheKey)
                .diskCacheKey(cacheKey)
                .placeholderMemoryCacheKey(cacheKey)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(if (persistentCache) CachePolicy.ENABLED else CachePolicy.DISABLED)
                .allowHardware(true)
                .listener(onSuccess = { _, result ->
                    loaded = true
                    if (persistentCache && !cached) {
                        scope.launch {
                            ThumbnailCache.save(context, media.id, result.image)
                        }
                    }
                })
                .build()
        }

        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .aspectRatio(aspectRatio)
                .then(size)
                .then(if (sharedElement) Modifier.mediaSharedElement(media.id) else Modifier)
        )

        // Приглушённая иконка по центру подложки, пока миниатюра не появилась.
        if (!loaded) {
            Icon(
                painter = painterResource(
                    if (null != media.video) R.drawable.videocam else R.drawable.photo
                ),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.size(28.dp)
            )
        }

        if (playPreview) {
            VideoPreview(media, Modifier.matchParentSize(), onEnded = { onPreviewEnded(media) })
        }

        media.video?.takeIf { showVideoBadge }?.let { video ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.45f)
                            )
                        )
                    )
                    .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 3.dp)
            ) {
                Icon(
                    painterResource(R.drawable.play_arrow),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = Color.White
                )
                if (video.duration > 0) {
                    Text(
                        text = formatDuration(video.duration),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }

        if (media.favorite) {
            Icon(
                painterResource(R.drawable.favorite),
                contentDescription = null,
                tint = FavoriteRed,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(16.dp)
            )
        }

        if (selectionMode) {
            Checkbox(
                checked = selected.contains(media.id),
                onCheckedChange = { onSelect(media) },
                modifier = Modifier.align(Alignment.TopStart)
            )
        }
    }
}

private fun formatDuration(millis: Long): String {
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
