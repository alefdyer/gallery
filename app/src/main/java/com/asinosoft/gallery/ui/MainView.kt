package com.asinosoft.gallery.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import com.asinosoft.gallery.data.Image
import com.asinosoft.gallery.model.GalleryViewModel
import com.asinosoft.gallery.util.groupByDate

@Composable
fun MainView(model: GalleryViewModel = hiltViewModel()) {
    val images by model.images.collectAsState(initial = listOf())

    val image = remember {
        mutableStateOf<Image?>(null)
    }

    if (image.value == null) {
        GroupView(groups = groupByDate(images)) {
            image.value = it
        }
    } else {
        PagerView(images = images, position = images.indexOf(image.value)) {
            image.value = null
        }
    }
}
