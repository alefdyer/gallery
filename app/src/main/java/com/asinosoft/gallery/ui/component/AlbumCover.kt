package com.asinosoft.gallery.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.asinosoft.gallery.R
import com.asinosoft.gallery.data.AlbumWithCover
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.data.ThumbnailCache
import kotlinx.coroutines.launch

@Composable
fun AlbumCover(
    album: AlbumWithCover,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        AlbumThumbnail(album.cover, Modifier.fillMaxSize())

        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0.5f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.7f)
                    )
                )
        )

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text(
                text = album.album.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${album.album.count}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AlbumThumbnail(
    cover: Media?,
    modifier: Modifier = Modifier,
) {
    if (null == cover) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.album),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp)
            )
        }
    } else {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        val cacheKey = remember(cover.id) { "media-${cover.id}" }
        val request = remember(cover, context, cacheKey) {
            val file = ThumbnailCache.getFile(context, cover.id)
            val data = if (file.exists() && file.length() > 0) file else cover.uri

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
                            ThumbnailCache.save(context, cover.id, result.image)
                        }
                    }
                })
                .build()
        }

        Image(
            painter = rememberAsyncImagePainter(model = request),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    }
}
