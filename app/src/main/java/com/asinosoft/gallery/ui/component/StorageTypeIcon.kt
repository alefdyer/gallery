package com.asinosoft.gallery.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.asinosoft.gallery.R
import com.asinosoft.gallery.data.storage.StorageType

@Composable
fun StorageTypeIcon(type: StorageType, modifier: Modifier = Modifier) {
    val size = modifier.size(32.dp)
    when (type) {
        StorageType.LOCAL -> Image(
            painterResource(R.drawable.ic_launcher_background),
            contentDescription = null,
            modifier = size
        )

        StorageType.DROPBOX -> Image(
            painterResource(R.drawable.dropbox),
            contentDescription = null,
            modifier = size
        )

        StorageType.NEXTCLOUD -> Image(
            painterResource(R.drawable.nextcloud),
            contentDescription = null,
            modifier = size
        )

        StorageType.WEBDAV -> Image(
            painterResource(R.drawable.webdav),
            contentDescription = null,
            modifier = size
        )

        StorageType.YANDEX -> Image(
            painterResource(R.drawable.yandex_disk),
            contentDescription = null,
            modifier = size
        )
    }
}
