package com.asinosoft.gallery.ui.component

import android.graphics.Bitmap.CompressFormat
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.toBitmap
import coil3.video.VideoFrameDecoder
import com.asinosoft.gallery.data.Media
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun VideoThumbnail(
    media: Media,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val thumbnail = File(LocalContext.current.cacheDir, media.id.toString())

    val loader =
        ImageLoader
            .Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()

    val request =
        if (thumbnail.exists()) {
            ImageRequest
                .Builder(LocalContext.current)
                .data(thumbnail)
                .build()
        } else {
            ImageRequest
                .Builder(context)
                .data(media.uri)
                .listener(onSuccess = { request, result ->
                    scope.launch(Dispatchers.IO) {
                        thumbnail.outputStream().use {
                            result.image
                                .toBitmap()
                                .compress(CompressFormat.WEBP, 100, it)
                        }
                    }
                })
                .build()
        }

    AsyncImage(
        model = request,
        imageLoader = loader,
        contentDescription = null,
        modifier =
            modifier
                .fillMaxSize()
                .aspectRatio(1f)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
    )
}
