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
import com.asinosoft.gallery.data.Media

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionInfoBar(
    modifier: Modifier = Modifier,
    selected: Set<Media> = setOf(),
    onBack: () -> Unit = {},
    onShare: (media: Set<Media>) -> Unit = {},
    onDelete: (media: Set<Media>) -> Unit = {},
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Text(text = selected.count().toString())
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                )
            }
        },
        actions = {
            IconButton(onClick = { onShare(selected) }) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = null,
                )
            }
            IconButton(onClick = { onDelete(selected) }) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null,
                )
            }
        },
    )
}
