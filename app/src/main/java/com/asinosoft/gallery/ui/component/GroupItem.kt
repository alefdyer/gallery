package com.asinosoft.gallery.ui.component

import android.graphics.Bitmap.CompressFormat
import android.util.Log
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.toBitmap
import com.asinosoft.gallery.data.Image
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun GroupItem(
    image: Image,
    modifier: Modifier = Modifier,
    selectedImages: Set<Image> = setOf(),
    selectionMode: Boolean = false,
    onImageClick: (Image) -> Unit = {},
    onImageSelect: (Image) -> Unit = {},
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomEnd,
    ) {
        val scope = rememberCoroutineScope()
        val thumbnail = File(LocalContext.current.cacheDir, image.id.toString())

        val request =
            if (thumbnail.exists()) {
                ImageRequest
                    .Builder(LocalContext.current)
                    .data(thumbnail)
                    .build()
            } else {
                ImageRequest
                    .Builder(LocalContext.current)
                    .data(image.path)
                    .listener(onSuccess = { request, result ->
                        if (image.size / result.image.size >= 4) {
                            scope.launch(Dispatchers.IO) {
                                Log.d("GroupItem", "Cache ${request.data}")

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
                Modifier
                    .aspectRatio(1f)
                    .combinedClickable(
                        onClick = {
                            if (selectionMode) {
                                onImageSelect(image)
                            } else {
                                onImageClick(image)
                            }
                        },
                        onLongClick = { onImageSelect(image) },
                    ),
        )

        if (selectionMode) {
            Checkbox(
                checked = selectedImages.contains(image),
                onCheckedChange = { onImageSelect(image) },
            )
        }
    }
}
