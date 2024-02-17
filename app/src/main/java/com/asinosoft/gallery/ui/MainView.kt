package com.asinosoft.gallery.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.asinosoft.gallery.data.Image
import com.asinosoft.gallery.data.ImageRepository
import com.asinosoft.gallery.util.groupByDate

@Composable
fun MainView(repo: ImageRepository) {
    val images = remember { repo.findAll() }
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
