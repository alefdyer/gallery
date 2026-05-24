package com.asinosoft.gallery.ui.component

import android.graphics.Bitmap.CompressFormat
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.compose.rememberConstraintsSizeResolver
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.asinosoft.gallery.R
import com.asinosoft.gallery.data.Album
import com.asinosoft.gallery.data.AlbumWithCover
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.model.MediaViewModel
import com.asinosoft.gallery.ui.theme.Typography
import java.io.File
import kotlinx.coroutines.Dispatchers
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
    model: MediaViewModel = hiltViewModel()
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
        val size = rememberConstraintsSizeResolver()

        var request by remember { mutableStateOf<ImageRequest?>(null) }

        LaunchedEffect(cover) {
            scope.launch {
                val thumbnail = File(context.cacheDir, cover.id.toString())
                request = if (thumbnail.exists()) {
                    val key = "thumbnail-${cover.id}"
                    ImageRequest
                        .Builder(context)
                        .memoryCacheKey(key)
                        .data(thumbnail)
                        .build()
                } else {
                    val uri = model.getThumbnailUri(cover)
                    val key = "media-${cover.id}"
                    ImageRequest
                        .Builder(context)
                        .data(uri)
                        .diskCacheKey(key)
                        .memoryCacheKey(key)
                        .size(size)
                        .allowHardware(true)
                        .listener(onSuccess = { _, result ->
                            if (!cover.filename.endsWith(".gif", ignoreCase = true)) {
                                scope.launch(Dispatchers.IO) {
                                    thumbnail.outputStream().use {
                                        result.image
                                            .toBitmap()
                                            .compress(CompressFormat.WEBP, 90, it)
                                    }
                                }
                            }
                        })
                        .build()
                }
            }
        }

        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.aspectRatio(1f).then(size)
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
