package com.asinosoft.gallery.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.asinosoft.gallery.R
import com.asinosoft.gallery.ui.theme.GalleryTheme

@Composable
fun SelectionControlBar(
    modifier: Modifier = Modifier,
    onShare: () -> Unit = {},
    onDelete: () -> Unit = {},
    onAddTag: () -> Unit = {},
    onRemoveTag: (() -> Unit)? = null
) {
    Surface(modifier, RoundedCornerShape(50)) {
        Row(Modifier.padding(8.dp)) {
            IconButton(onClick = { onAddTag() }) {
                Icon(
                    painter = painterResource(R.drawable.add_tag),
                    contentDescription = null
                )
            }
            if (onRemoveTag != null) {
                IconButton(onClick = { onRemoveTag() }) {
                    Icon(
                        painter = painterResource(R.drawable.remove_tag),
                        contentDescription = null
                    )
                }
            }
            IconButton(onClick = { onShare() }) {
                Icon(
                    painter = painterResource(R.drawable.share),
                    contentDescription = null
                )
            }
            IconButton(onClick = { onDelete() }) {
                Icon(
                    painter = painterResource(R.drawable.delete),
                    contentDescription = null
                )
            }
        }
    }
}

@Preview
@Composable
private fun PreviewSelectionControlBar() {
    GalleryTheme {
        Box(Modifier.size(400.dp)) {
            SelectionControlBar(
                Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
