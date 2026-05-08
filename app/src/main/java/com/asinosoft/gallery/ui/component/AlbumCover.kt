package com.asinosoft.gallery.ui.component

import android.graphics.Bitmap.CompressFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.compose.rememberConstraintsSizeResolver
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.asinosoft.gallery.R
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.model.GalleryViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun AlbumCover(
    cover: Media?,
    modifier: Modifier = Modifier,
    model: GalleryViewModel = hiltViewModel()
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
