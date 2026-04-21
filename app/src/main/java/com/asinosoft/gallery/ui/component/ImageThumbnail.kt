package com.asinosoft.gallery.ui.component

import android.graphics.Bitmap.CompressFormat
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.compose.rememberConstraintsSizeResolver
import coil3.request.ImageRequest
import coil3.toBitmap
import com.asinosoft.gallery.data.Media
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun ImageThumbnail(media: Media, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val size = rememberConstraintsSizeResolver()
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
                .size(size)
                .listener(onSuccess = { _, result ->
                    if (!media.filename.endsWith(".gif", ignoreCase = true)) {
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

    AsyncImage(
        model = request,
        contentDescription = "",
        contentScale = ContentScale.Crop,
        modifier = modifier.aspectRatio(1f).then(size)
    )
}
