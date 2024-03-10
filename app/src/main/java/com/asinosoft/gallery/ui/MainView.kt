package com.asinosoft.gallery.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.asinosoft.gallery.model.GalleryViewModel
import com.asinosoft.gallery.util.groupByDate

@Composable
fun MainView(
    model: GalleryViewModel,
    onImageClick: (Int) -> Unit,
) {
    val images by model.images.collectAsState(initial = listOf())

    GroupView(
        groups = groupByDate(images),
        onClick = { image ->
            val position = images.indexOf(image)

            onImageClick(position)
        }
    )
}
