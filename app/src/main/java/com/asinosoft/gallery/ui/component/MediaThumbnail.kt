package com.asinosoft.gallery.ui.component

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.compose.rememberConstraintsSizeResolver
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.asinosoft.gallery.R
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.data.ThumbnailCache
import com.asinosoft.gallery.model.MediaViewModel
import kotlinx.coroutines.launch

@Composable
fun MediaThumbnail(
    media: Media,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 1f,
    selected: Set<Long> = setOf(),
    selectionMode: Boolean = false,
    playPreview: Boolean = false,
    showVideoBadge: Boolean = true,
    onClick: (Media) -> Unit = {},
    onSelect: (Media) -> Unit = {},
    model: MediaViewModel = hiltViewModel()
) {
    Box(
        modifier = modifier
            .clipToBounds()
            .clickable { if (selectionMode) onSelect(media) else onClick(media) }
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val size = rememberConstraintsSizeResolver()

        val cacheKey = remember(media.id) { "media-${media.id}" }
        var request by remember { mutableStateOf<ImageRequest?>(null) }

        LaunchedEffect (media, context, cacheKey) {
            val file = ThumbnailCache.getFile(context, media.id)
            val data = if (file.exists() && file.length() > 0) file else model.getThumbnailUri(media)

            request = ImageRequest.Builder(context)
                .data(data)
                .size(size)
                .memoryCacheKey(cacheKey)
                .diskCacheKey(cacheKey)
                .placeholderMemoryCacheKey(cacheKey)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .allowHardware(true)
                .listener(onSuccess = { _, result ->
                    if (!file.exists()) {
                        scope.launch {
                            ThumbnailCache.save(context, media.id, result.image)
                        }
                    }
                })
                .build()
        }

        AsyncImage(
            model = request,
            placeholder = painterResource(R.drawable.photo),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.aspectRatio(aspectRatio).then(size)
        )

        if (playPreview) {
            VideoPreview(media, Modifier.matchParentSize())
        }

        media.video?.takeIf { showVideoBadge }?.let { video ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f))))
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
