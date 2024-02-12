package com.asinosoft.gallery.ui

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.asinosoft.gallery.data.ImageRepository

@Composable
fun MainView(repo: ImageRepository) {
    val images = remember { repo.findAll() }

    LazyVerticalGrid(columns = GridCells.Fixed(4)) {
        items(images) {
            AsyncImage(
                model = it.url,
                contentDescription = "",
                contentScale = ContentScale.Crop,
                modifier = Modifier.aspectRatio(1f)
            )
        }
    }
}
