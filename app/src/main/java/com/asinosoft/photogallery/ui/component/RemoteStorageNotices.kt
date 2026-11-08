package com.asinosoft.photogallery.ui.component

import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.asinosoft.photogallery.R
import com.asinosoft.photogallery.data.Media
import com.asinosoft.photogallery.data.storage.StorageType

fun Media.isRemoteStorage(): Boolean = storageType != StorageType.LOCAL

fun Context.storageTypeLabel(type: StorageType): String = when (type) {
    StorageType.LOCAL -> getString(R.string.phone)
    StorageType.DROPBOX -> getString(R.string.dropbox)
    StorageType.NEXTCLOUD -> getString(R.string.nextcloud)
    StorageType.WEBDAV -> getString(R.string.webdav)
    StorageType.YANDEX -> getString(R.string.yandex)
    StorageType.GOOGLE_DRIVE -> getString(R.string.google_drive)
    StorageType.ONEDRIVE -> getString(R.string.onedrive)
}

fun Context.remoteDeleteBlockedMessage(type: StorageType): String =
    getString(R.string.storage_delete_impossible, storageTypeLabel(type))

@Composable
fun RemoteStorageNoticeDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
    )
}
