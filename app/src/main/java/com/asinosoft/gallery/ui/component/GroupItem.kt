package com.asinosoft.gallery.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.asinosoft.gallery.data.Media

@Composable
fun GroupItem(
    media: Media,
    modifier: Modifier = Modifier,
    selected: Set<Long> = setOf(),
    selectionMode: Boolean = false,
    onClick: (Media) -> Unit = {},
    onSelect: (Media) -> Unit = {}
) {
    Box(
        modifier = modifier.clickable { if (selectionMode) onSelect(media) else onClick(media) },
        contentAlignment = Alignment.BottomEnd
    ) {
        if (media.image != null) {
            ImageThumbnail(media)
        } else if (media.video != null) {
            VideoThumbnail(media)
        }

        if (selectionMode) {
            Checkbox(
                checked = selected.contains(media.id),
                onCheckedChange = { onSelect(media) }
            )
        }
    }
}
