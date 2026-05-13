package com.asinosoft.gallery.ui.component

import android.graphics.Bitmap.CompressFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.compose.rememberConstraintsSizeResolver
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.asinosoft.gallery.R
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.data.storage.StorageType
import com.asinosoft.gallery.model.MediaViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun MediaThumbnail(
    media: Media,
    modifier: Modifier = Modifier,
    selected: Set<Long> = setOf(),
    selectionMode: Boolean = false,
    onClick: (Media) -> Unit = {},
    onSelect: (Media) -> Unit = {},
    model: MediaViewModel = hiltViewModel()
) {
    Box(
        modifier = modifier.clickable { if (selectionMode) onSelect(media) else onClick(media) }
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val size = rememberConstraintsSizeResolver()

        var request by remember { mutableStateOf<ImageRequest?>(null) }

        LaunchedEffect(media) {
            scope.launch {
                val thumbnail = File(context.cacheDir, media.id.toString())
                request = if (thumbnail.exists()) {
                    val key = "thumbnail-${media.id}"
                    ImageRequest
                        .Builder(context)
                        .memoryCacheKey(key)
                        .data(thumbnail)
                        .build()
                } else {
                    val uri = model.getThumbnailUri(media)
                    val key = "media-${media.id}"
                    ImageRequest
                        .Builder(context)
                        .data(uri)
                        .diskCacheKey(key)
                        .memoryCacheKey(key)
                        .size(size)
                        .allowHardware(true)
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
            }
        }

        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.aspectRatio(1f).then(size)
        )

        if (null != media.video) {
            Icon(
                painterResource(R.drawable.play_circle),
                contentDescription = null,
                modifier = Modifier.size(16.dp).align(Alignment.BottomEnd),
                tint = Color.White
            )
        }

        StorageIcon(media.storageType)

        if (selectionMode) {
            Checkbox(
                checked = selected.contains(media.id),
                onCheckedChange = { onSelect(media) },
                modifier = Modifier.align(Alignment.TopStart)
            )
        }
    }
}

@Composable
private fun BoxScope.StorageIcon(storageType: StorageType) {
    val icon = when (storageType) {
        StorageType.DROPBOX -> R.drawable.dropbox_icon
        StorageType.LOCAL -> R.drawable.mobile
        StorageType.NEXTCLOUD -> R.drawable.nextcloud_icon
        StorageType.WEBDAV -> R.drawable.webdav
        StorageType.YANDEX -> R.drawable.yandex_icon
    }

    Icon(
        painterResource(icon),
        contentDescription = null,
        modifier = Modifier.size(16.dp).align(Alignment.BottomStart),
        tint = Color.White
    )
}
