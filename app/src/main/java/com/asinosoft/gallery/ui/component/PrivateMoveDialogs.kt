package com.asinosoft.gallery.ui.component

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.asinosoft.gallery.R

@Composable
fun MoveToPrivateDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.move_to_private_title)) },
        text = { Text(stringResource(R.string.move_to_private_text)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.move)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/** Предлагает выдать разрешение «Управление медиафайлами», чтобы система не спрашивала подтверждение удаления */
@Composable
fun ManageMediaDialog(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manage_media_title)) },
        text = { Text(stringResource(R.string.manage_media_text)) },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.open_settings)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
            }
        }
    )
}

fun canAskManageMedia(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !MediaStore.canManageMedia(context)

fun openManageMediaSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val intent = Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA, "package:${context.packageName}".toUri())
    runCatching { context.startActivity(intent) }
}
