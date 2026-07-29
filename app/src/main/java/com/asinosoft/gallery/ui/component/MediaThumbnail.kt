package com.asinosoft.gallery.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.asinosoft.gallery.R
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.data.ThumbnailCache
import kotlinx.coroutines.launch

@Composable
fun MediaThumbnail(
    media: Media,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 1f,
    selected: Set<Long> = setOf(),
    selectionMode: Boolean = false,
    onClick: (Media) -> Unit = {},
    onSelect: (Media) -> Unit = {}
) {
    Box(
        modifier = modifier.clickable { if (selectionMode) onSelect(media) else onClick(media) }
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        val cacheKey = remember(media.id) { "media-${media.id}" }
        val request = remember(media, context, cacheKey) {
            val file = ThumbnailCache.getFile(context, media.id)
            val data = if (file.exists() && file.length() > 0) file else media.uri

            ImageRequest.Builder(context)
                .data(data)
                .size(300, 300)
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

        val painter = rememberAsyncImagePainter(model = request)

        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.aspectRatio(aspectRatio)
        )

        if (null != media.video) {
            Icon(
                painterResource(R.drawable.play_circle),
                contentDescription = null,
                modifier = Modifier.size(16.dp).align(Alignment.BottomEnd),
                tint = Color.White
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
