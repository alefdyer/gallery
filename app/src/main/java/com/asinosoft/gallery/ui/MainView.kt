package com.asinosoft.gallery.ui

import androidx.compose.runtime.Composable
import com.asinosoft.gallery.data.Image
import com.asinosoft.gallery.util.groupByDate

@Composable
fun MainView(
    images: List<Image>,
    onImageClick: (Image) -> Unit,
) {
    GroupView(
        groups = groupByDate(images),
        onClick = { image -> onImageClick(image) }
    )
}
