package com.asinosoft.photogallery.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.asinosoft.photogallery.R
import com.asinosoft.photogallery.data.storage.StorageType

fun StorageType.drawableRes(): Int = when (this) {
    StorageType.LOCAL -> R.drawable.smartphone
    StorageType.DROPBOX -> R.drawable.dropbox
    StorageType.NEXTCLOUD -> R.drawable.nextcloud
    StorageType.WEBDAV -> R.drawable.webdav
    StorageType.YANDEX -> R.drawable.yandex_disk
    StorageType.GOOGLE_DRIVE -> R.drawable.google_drive
    StorageType.ONEDRIVE -> R.drawable.onedrive
}

@Composable
fun StorageTypeIcon(type: StorageType, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(type.drawableRes()),
        contentDescription = null,
        modifier = modifier.size(32.dp)
    )
}
