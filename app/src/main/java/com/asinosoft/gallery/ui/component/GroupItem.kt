package com.asinosoft.gallery.ui.component

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.asinosoft.gallery.data.Image

@Composable
fun GroupItem(
    image: Image,
    selectedImages: Set<Image> = setOf(),
    selectionMode: Boolean = false,
    onImageClick: (Image) -> Unit = {},
    onImageSelect: (Image) -> Unit = {},
) {
    Box(contentAlignment = Alignment.BottomEnd) {
        AsyncImage(
            model = image.path,
            contentDescription = "",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .aspectRatio(1f)
                .combinedClickable(
                    onClick = {
                        if (selectionMode) {
                            onImageSelect(image)
                        } else {
                            onImageClick(image)
                        }
                    },
                    onLongClick = { onImageSelect(image) }
                )
        )

        if (selectionMode) {
            Checkbox(
                checked = selectedImages.contains(image),
                onCheckedChange = { onImageSelect(image) })
        }
    }
}