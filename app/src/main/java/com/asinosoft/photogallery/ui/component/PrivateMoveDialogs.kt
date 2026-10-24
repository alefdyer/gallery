package com.asinosoft.photogallery.ui.component

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.edit
import androidx.core.net.toUri
import com.asinosoft.photogallery.R

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

/**
 * Перед первым удалением в корзину объясняет, сколько там хранятся файлы.
 * Возвращает действие, которое при необходимости сначала показывает это окно.
 */
@Composable
fun rememberTrashIntro(onDelete: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnDelete by rememberUpdatedState(onDelete)
    var showIntro by remember { mutableStateOf(false) }

    if (showIntro) {
        AlertDialog(
            onDismissRequest = { showIntro = false },
            title = {
                Text(
                    text = stringResource(R.string.trash_intro_title),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = { Text(stringResource(R.string.trash_intro_text)) },
            confirmButton = {
                TextButton(onClick = {
                    showIntro = false
                    trashPrefs(context).edit { putBoolean(TRASH_INTRO_SHOWN, true) }
                    currentOnDelete()
                }) { Text(stringResource(R.string.trash_intro_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showIntro = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    return remember(context) {
        {
            val needsIntro = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                !trashPrefs(context).getBoolean(TRASH_INTRO_SHOWN, false)
            if (needsIntro) showIntro = true else currentOnDelete()
        }
    }
}

private fun trashPrefs(context: Context) = context.getSharedPreferences(TRASH_PREFS, Context.MODE_PRIVATE)

private const val TRASH_PREFS = "trash"
private const val TRASH_INTRO_SHOWN = "intro_shown"

/** Разрешение ещё не выдано и с последнего предложения прошло [MANAGE_MEDIA_ASK_INTERVAL_MS] */
fun canAskManageMedia(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || MediaStore.canManageMedia(context)) return false
    val prefs = context.getSharedPreferences(MANAGE_MEDIA_PREFS, Context.MODE_PRIVATE)
    val now = System.currentTimeMillis()
    if (now - prefs.getLong(MANAGE_MEDIA_LAST_ASKED, 0L) < MANAGE_MEDIA_ASK_INTERVAL_MS) return false
    prefs.edit { putLong(MANAGE_MEDIA_LAST_ASKED, now) }
    return true
}

private const val MANAGE_MEDIA_PREFS = "manage_media"
private const val MANAGE_MEDIA_LAST_ASKED = "last_asked"
private const val MANAGE_MEDIA_ASK_INTERVAL_MS = 3L * 24 * 60 * 60 * 1000

fun openManageMediaSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val intent = Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA, "package:${context.packageName}".toUri())
    runCatching { context.startActivity(intent) }
}
