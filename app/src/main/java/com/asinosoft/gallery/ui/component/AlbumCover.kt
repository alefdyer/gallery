package com.asinosoft.gallery.ui.component

import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.asinosoft.gallery.R
import com.asinosoft.gallery.data.Album
import com.asinosoft.gallery.data.AlbumWithCover
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.data.ThumbnailCache
import com.asinosoft.gallery.ui.theme.Typography
import kotlinx.coroutines.launch

@Composable
fun AlbumCover(
    album: AlbumWithCover,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier.padding(1.dp)
                .clip(RoundedCornerShape(12.dp))
    ) {
        AlbumThumbnail(album.cover)

        AlbumImages(album.album)

        AlbumInfo(album.album)
    }
}

@Composable
private fun AlbumThumbnail(
    cover: Media?,
    modifier: Modifier = Modifier,
) {
    if (null == cover) {
        Image(
            painter = painterResource(R.drawable.album),
            contentDescription = null,
            modifier = modifier.aspectRatio(1f)
        )
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

        val painter = rememberAsyncImagePainter(model = request)

        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.aspectRatio(1f)
        )
    }
}

@Composable
private fun BoxScope.AlbumImages(album: Album, modifier: Modifier = Modifier) {
    Text(
        text = " ${album.count} ",
        color = Color.White,
        modifier =
            modifier
                .align(Alignment.TopEnd)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent.copy(0.5f),
                            Color.Transparent
                        )
                    )
                )
    )
}

@Composable
private fun BoxScope.AlbumInfo(album: Album, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .background(Color.Black.copy(alpha = 0.4f))
                .align(Alignment.BottomCenter)
    ) {
        val size = Formatter.formatShortFileSize(LocalContext.current, album.size)

        Text(
            text = album.name,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            color = Color.White,
            modifier =
                Modifier
                    .padding(start = 8.dp)
                    .fillMaxWidth()
                    .weight(1f)
        )

        Text(
            text = size,
            style = Typography.bodySmall,
            color = Color.White,
            maxLines = 1,
            modifier =
                Modifier
                    .padding(end = 8.dp)
        )
    }
}
