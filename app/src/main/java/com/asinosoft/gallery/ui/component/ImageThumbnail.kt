package com.asinosoft.gallery.ui.component

import android.graphics.Bitmap.CompressFormat
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.toBitmap
import com.asinosoft.gallery.data.Media
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ImageThumbnail(
    media: Media,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val thumbnail = File(LocalContext.current.cacheDir, media.id.toString())

    val request =
        if (thumbnail.exists()) {
            ImageRequest
                .Builder(LocalContext.current)
                .data(thumbnail)
                .build()
        } else {
            ImageRequest
                .Builder(LocalContext.current)
                .data(media.uri)
                .listener(onSuccess = { _, result ->
                    val imageSize = result.image.size
                    if (!media.filename.endsWith(".gif", ignoreCase = true) &&
                        imageSize > 0 && media.size / imageSize >= 4
                    ) {
                        scope.launch(Dispatchers.IO) {
                            thumbnail.outputStream().use {
                                result.image
                                    .toBitmap()
                                    .compress(CompressFormat.WEBP, 100, it)
                            }
                        }
                    }
                })
                .build()
        }

    AsyncImage(
        model = request,
        contentDescription = "",
        contentScale = ContentScale.Crop,
        modifier =
            modifier
                .aspectRatio(1f)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
    )
}
