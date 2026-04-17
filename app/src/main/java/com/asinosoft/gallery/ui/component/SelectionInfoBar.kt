package com.asinosoft.gallery.ui.component

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.asinosoft.gallery.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionInfoBar(
    modifier: Modifier = Modifier,
    selected: Set<Long> = setOf(),
    onBack: () -> Unit = {},
    onShare: (media: Set<Long>) -> Unit = {},
    onDelete: (media: Set<Long>) -> Unit = {},
    onAddTag: (media: Set<Long>) -> Unit = {},
    onRemoveTag: ((media: Set<Long>) -> Unit)? = null
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Text(text = selected.count().toString())
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.close),
                    contentDescription = null
                )
            }
        },
        actions = {
            IconButton(onClick = { onAddTag(selected) }) {
                Icon(
                    painter = painterResource(R.drawable.add_tag),
                    contentDescription = null
                )
            }
            if (onRemoveTag != null) {
                IconButton(onClick = { onRemoveTag(selected) }) {
                    Icon(
                        painter = painterResource(R.drawable.remove_tag),
                        contentDescription = null
                    )
                }
            }
            IconButton(onClick = { onShare(selected) }) {
                Icon(
                    painter = painterResource(R.drawable.share),
                    contentDescription = null
                )
            }
            IconButton(onClick = { onDelete(selected) }) {
                Icon(
                    painter = painterResource(R.drawable.delete),
                    contentDescription = null
                )
            }
        }
    )
}
