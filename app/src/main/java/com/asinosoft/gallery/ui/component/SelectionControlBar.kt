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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.asinosoft.gallery.R
import com.asinosoft.gallery.ui.theme.GalleryTheme
import com.asinosoft.gallery.ui.theme.floatingPanelColor

@Composable
fun SelectionControlBar(
    modifier: Modifier = Modifier,
    onShare: () -> Unit = {},
    onDelete: () -> Unit = {},
    onAddTag: () -> Unit = {},
    onRemoveTag: (() -> Unit)? = null,
    onAddToFavorites: () -> Unit = {},
    onMoveToPrivate: () -> Unit = {}
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(50),
        color = floatingPanelColor()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            LabeledIconButton(
                label = stringResource(R.string.add_to_album),
                icon = painterResource(R.drawable.playlist_add),
                onClick = { onAddTag() },
                modifier = Modifier.weight(1f)
            )
            LabeledIconButton(
                label = stringResource(R.string.share),
                icon = painterResource(R.drawable.share),
                onClick = { onShare() },
                modifier = Modifier.weight(1f)
            )
            LabeledIconButton(
                label = stringResource(R.string.delete),
                icon = painterResource(R.drawable.delete),
                onClick = { onDelete() },
                modifier = Modifier.weight(1f)
            )
            Box(modifier = Modifier.weight(1f)) {
                var showOptions by remember { mutableStateOf(false) }
                LabeledIconButton(
                    label = stringResource(R.string.options),
                    icon = painterResource(R.drawable.more_vert),
                    onClick = { showOptions = true },
                    modifier = Modifier.fillMaxWidth()
                )
                FloatingDropdownMenu(
                    expanded = showOptions,
                    onDismissRequest = { showOptions = false },
                    offset = DpOffset(0.dp, (-8).dp)
                ) {
                    if (onRemoveTag != null) {
                        FloatingMenuItem(
                            text = stringResource(R.string.remove_from_album),
                            onClick = {
                                showOptions = false
                                onRemoveTag()
                            }
                        )
                    }
                    FloatingMenuItem(
                        text = stringResource(R.string.add_to_favorites),
                        onClick = {
                            showOptions = false
                            onAddToFavorites()
                        }
                    )
                    FloatingMenuItem(
                        text = stringResource(R.string.move_to_private),
                        onClick = {
                            showOptions = false
                            onMoveToPrivate()
                        }
                    )
                }
            }
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
