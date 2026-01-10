package com.asinosoft.gallery.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.asinosoft.gallery.data.Image

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionInfoBar(
    modifier: Modifier = Modifier,
    selectedImages: Set<Image> = setOf(),
    onBack: () -> Unit = {},
    onShare: (images: Set<Image>) -> Unit = {},
    onDelete: (images: Set<Image>) -> Unit = {},
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Text(text = selectedImages.count().toString())
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null
                )
            }
        },
        actions = {
            IconButton(onClick = { onShare(selectedImages) }) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = null
                )
            }
            IconButton(onClick = { onDelete(selectedImages) }) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null
                )
            }
        },
    )

}