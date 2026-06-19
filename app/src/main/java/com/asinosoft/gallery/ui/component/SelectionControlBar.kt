package com.asinosoft.gallery.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(50)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            LabeledIconButton(
                label = stringResource(R.string.add_to_album),
                icon = painterResource(R.drawable.playlist_add),
                onClick = { onAddTag() }
            )
            if (onRemoveTag != null) {
                LabeledIconButton(
                    label = stringResource(R.string.remove_from_album),
                    icon = painterResource(R.drawable.playlist_remove),
                    onClick = { onRemoveTag() }
                )
            }
            LabeledIconButton(
                label = stringResource(R.string.share),
                icon = painterResource(R.drawable.share),
                onClick = { onShare() }
            )
            LabeledIconButton(
                label = stringResource(R.string.delete),
                icon = painterResource(R.drawable.delete),
                onClick = { onDelete() }
            )
        }
    }
}

@Preview
@Composable
private fun PreviewSelectionControlBar() {
    GalleryTheme {
        Box(Modifier.size(800.dp)) {
            SelectionControlBar(
                Modifier.align(Alignment.BottomCenter),
                onRemoveTag = {}
            )
        }
    }
}
