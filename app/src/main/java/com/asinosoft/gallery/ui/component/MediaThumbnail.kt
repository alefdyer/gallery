package com.asinosoft.gallery.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.asinosoft.gallery.R
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.data.storage.StorageType

@Composable
fun MediaThumbnail(
    media: Media,
    modifier: Modifier = Modifier,
    selected: Set<Long> = setOf(),
    selectionMode: Boolean = false,
    onClick: (Media) -> Unit = {},
    onSelect: (Media) -> Unit = {}
) {
    Box(
        modifier = modifier.clickable { if (selectionMode) onSelect(media) else onClick(media) }
    ) {
        if (media.image != null) {
            ImageThumbnail(media)
        } else if (media.video != null) {
            VideoThumbnail(media)
            Icon(
                painterResource(R.drawable.play_circle),
                contentDescription = null,
                modifier = Modifier.size(16.dp).align(Alignment.BottomEnd),
                tint = Color.White
            )
        }

        StorageIcon(media.storageType)

        if (selectionMode) {
            Checkbox(
                checked = selected.contains(media.id),
                onCheckedChange = { onSelect(media) },
                modifier = Modifier.align(Alignment.TopStart)
            )
        }
    }
}

@Composable
private fun BoxScope.StorageIcon(storageType: StorageType) {
    val icon = when (storageType) {
        StorageType.DROPBOX -> R.drawable.dropbox_icon
        StorageType.LOCAL -> R.drawable.mobile
        StorageType.NEXTCLOUD -> R.drawable.nextcloud_icon
        StorageType.WEBDAV -> R.drawable.webdav
        StorageType.YANDEX -> R.drawable.yandex_icon
    }

    Icon(
        painterResource(icon),
        contentDescription = null,
        modifier = Modifier.size(16.dp).align(Alignment.BottomStart),
        tint = Color.White
    )
}
