package com.asinosoft.photogallery.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.asinosoft.photogallery.R
import com.asinosoft.photogallery.data.backup.DriveBackupFile
import com.asinosoft.photogallery.data.backup.SettingsRestoreMode
import com.asinosoft.photogallery.model.BackupUiEvent
import com.asinosoft.photogallery.model.BackupUiState
import com.asinosoft.photogallery.model.SettingsBackupViewModel
import com.asinosoft.photogallery.ui.component.BackupProgressBanner
import com.asinosoft.photogallery.ui.component.GallerySettings
import com.asinosoft.photogallery.ui.component.SettingsGroup
import com.asinosoft.photogallery.ui.component.SettingsGroupDivider
import com.asinosoft.photogallery.ui.component.SettingsPage
import com.asinosoft.photogallery.ui.component.SettingsSectionTitle
import com.asinosoft.photogallery.ui.component.rememberAutoplayVideo
import com.asinosoft.photogallery.ui.component.rememberVideoSound

@Composable
fun GallerySettingsTab(
    modifier: Modifier = Modifier,
    backupModel: SettingsBackupViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val autoplay = rememberAutoplayVideo()
    val sound = rememberVideoSound()
    val backupState by backupModel.state.collectAsState()
    var showSaveDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var newBackupName by remember { mutableStateOf("") }
    var pendingReplace by remember { mutableStateOf<DriveBackupFile?>(null) }
    var pendingDelete by remember { mutableStateOf<DriveBackupFile?>(null) }
    var pendingRestore by remember { mutableStateOf<DriveBackupFile?>(null) }

    LaunchedEffect(backupState.connected) {
        if (backupState.connected) backupModel.loadBackups()
    }

    val authLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val host = activity
        if (null != host) {
            backupModel.onAuthResult(host, result)
        }
    }

    LaunchedEffect(Unit) {
        backupModel.refreshConnection()
    }

    LaunchedEffect(backupModel) {
        backupModel.events.collect { event ->
            when (event) {
                is BackupUiEvent.RequestAuth -> {
                    authLauncher.launch(
                        IntentSenderRequest.Builder(event.pendingIntent).build()
                    )
                }

                BackupUiEvent.Connected ->
                    Toast.makeText(
                        context,
                        context.getString(R.string.backup_drive_connected),
                        Toast.LENGTH_SHORT
                    ).show()

                BackupUiEvent.AuthCancelled ->
                    Toast.makeText(
                        context,
                        context.getString(R.string.backup_drive_auth_cancelled),
                        Toast.LENGTH_SHORT
                    ).show()

                BackupUiEvent.Saved -> {
                    showSaveDialog = false
                    Toast.makeText(
                        context,
                        context.getString(R.string.backup_saved),
                        Toast.LENGTH_SHORT
                    ).show()
                }

                BackupUiEvent.Deleted ->
                    Toast.makeText(
                        context,
                        context.getString(R.string.backup_deleted),
                        Toast.LENGTH_SHORT
                    ).show()

                is BackupUiEvent.Restored -> {
                    showRestoreDialog = false
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.backup_restored,
                            event.result.albumsRestored,
                            event.result.mediaLinked,
                            event.result.favoritesRestored,
                            event.result.mediaNotFound
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                }

                is BackupUiEvent.Error ->
                    Toast.makeText(
                        context,
                        event.message.ifBlank { context.getString(R.string.backup_error) },
                        Toast.LENGTH_LONG
                    ).show()
            }
        }
    }

    if (showSaveDialog) {
        BackupSaveDialog(
            state = backupState,
            name = newBackupName,
            onNameChange = { newBackupName = it },
            onSaveNew = {
                val name = newBackupName.trim()
                val existing = backupState.backups.firstOrNull {
                    it.displayName.equals(name, ignoreCase = true)
                }
                // Имя уже занято — это уже не новая копия, а замена: подтверждаем.
                if (null != existing) {
                    pendingReplace = existing
                } else {
                    backupModel.saveNew(name)
                }
            },
            onReplace = { pendingReplace = it },
            onDelete = { pendingDelete = it },
            onDismiss = { showSaveDialog = false }
        )
    }

    if (showRestoreDialog) {
        BackupRestoreDialog(
            state = backupState,
            onPick = { pendingRestore = it },
            onDismiss = { showRestoreDialog = false }
        )
    }

    pendingReplace?.let { file ->
        AlertDialog(
            onDismissRequest = { pendingReplace = null },
            title = { Text(stringResource(R.string.backup_replace_title)) },
            text = { Text(stringResource(R.string.backup_replace_confirm, file.displayName)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingReplace = null
                    backupModel.replaceBackup(file)
                }) { Text(stringResource(R.string.backup_replace)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingReplace = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    pendingDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.backup_delete_title)) },
            text = { Text(stringResource(R.string.backup_delete_confirm, file.displayName)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    backupModel.deleteBackup(file)
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    pendingRestore?.let { file ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(stringResource(R.string.backup_restore_title)) },
            text = { Text(stringResource(R.string.backup_restore_confirm, file.displayName)) },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        pendingRestore = null
                        backupModel.restore(file, SettingsRestoreMode.REPLACE)
                    }) { Text(stringResource(R.string.backup_overwrite)) }
                    TextButton(onClick = {
                        pendingRestore = null
                        backupModel.restore(file, SettingsRestoreMode.MERGE)
                    }) { Text(stringResource(R.string.backup_merge)) }
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        SettingsPage(Modifier.fillMaxSize()) {
            SettingsSectionTitle(stringResource(R.string.viewing_section))
            SettingsGroup {
                SettingsSwitchRow(
                    title = stringResource(R.string.autoplay_video),
                    subtitle = stringResource(R.string.autoplay_video_hint),
                    checked = autoplay,
                    onCheckedChange = { GallerySettings.setAutoplayVideo(context, it) }
                )
                SettingsGroupDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.video_sound),
                    subtitle = stringResource(R.string.video_sound_hint),
                    checked = sound,
                    onCheckedChange = { GallerySettings.setVideoSound(context, it) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            SettingsSectionTitle(stringResource(R.string.backup_section))
            SettingsGroup {
                SettingsActionRow(
                    title = stringResource(
                        if (backupState.connected) R.string.backup_drive_disconnect
                        else R.string.backup_drive_connect
                    ),
                    subtitle = stringResource(
                        if (backupState.connected) R.string.backup_drive_connected_hint
                        else R.string.backup_drive_connect_hint
                    ),
                    enabled = !backupState.busy,
                    trailing = {
                        if (backupState.busy && null == backupState.job) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    },
                    onClick = {
                        if (backupState.connected) {
                            backupModel.disconnect()
                        } else if (null != activity) {
                            backupModel.connect(activity)
                        }
                    }
                )
                SettingsGroupDivider()
                SettingsActionRow(
                    title = stringResource(R.string.backup_save),
                    subtitle = stringResource(R.string.backup_save_hint),
                    enabled = backupState.connected && !backupState.busy,
                    onClick = {
                        newBackupName = context.getString(
                            R.string.backup_default_name,
                            java.time.LocalDateTime.now().format(
                                java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                            )
                        )
                        backupModel.loadBackups()
                        showSaveDialog = true
                    }
                )
                SettingsGroupDivider()
                SettingsActionRow(
                    title = stringResource(R.string.backup_restore),
                    subtitle = stringResource(R.string.backup_restore_hint),
                    enabled = backupState.connected && !backupState.busy,
                    onClick = {
                        backupModel.loadBackups()
                        showRestoreDialog = true
                    }
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        BackupProgressBanner(state = backupState)
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                }
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (enabled) 1f else 0.5f
                )
            )
        }
        if (null != trailing) {
            Spacer(modifier = Modifier.width(12.dp))
            trailing()
        } else {
            Icon(
                painter = painterResource(R.drawable.chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (enabled) 1f else 0.4f
                ),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun BackupSaveDialog(
    state: BackupUiState,
    name: String,
    onNameChange: (String) -> Unit,
    onSaveNew: () -> Unit,
    onReplace: (DriveBackupFile) -> Unit,
    onDelete: (DriveBackupFile) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_save_dialog_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.backup_name_label)) },
                    singleLine = true,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.backup_saved_list),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                BackupList(
                    state = state,
                    modifier = Modifier.heightIn(max = 260.dp)
                ) { file ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = { onReplace(file) },
                            enabled = !state.busy
                        ) { Text(stringResource(R.string.backup_replace)) }
                        TextButton(
                            onClick = { onDelete(file) },
                            enabled = !state.busy
                        ) { Text(stringResource(R.string.delete)) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSaveNew,
                enabled = !state.busy && name.isNotBlank()
            ) { Text(stringResource(R.string.backup_save_new)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun BackupRestoreDialog(
    state: BackupUiState,
    onPick: (DriveBackupFile) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_restore_dialog_title)) },
        text = {
            BackupList(
                state = state,
                modifier = Modifier.heightIn(max = 320.dp)
            ) { file ->
                TextButton(
                    onClick = { onPick(file) },
                    enabled = !state.busy
                ) { Text(stringResource(R.string.backup_restore)) }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/**
 * Список сохранённых копий. Запись — в два этажа: сверху чёрным название и дата,
 * снизу синим действия; между записями разделитель. При крупном шрифте ничего
 * не сжимается в узкую колонку и не режется.
 */
@Composable
private fun BackupList(
    state: BackupUiState,
    modifier: Modifier = Modifier,
    action: @Composable (DriveBackupFile) -> Unit,
) {
    when {
        state.backupsLoading && state.backups.isEmpty() -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }

        state.backups.isEmpty() -> {
            Text(
                text = stringResource(R.string.backup_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = modifier.padding(vertical = 8.dp)
            )
        }

        else -> {
            Column(modifier.verticalScroll(rememberScrollState())) {
                state.backups.forEachIndexed { index, file ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = file.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        formatBackupDate(file.modifiedTime)?.let { date ->
                            Text(
                                text = stringResource(R.string.backup_modified, date),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row {
                            action(file)
                        }
                    }
                }
            }
        }
    }
}

private fun formatBackupDate(modifiedTime: String?): String? {
    if (modifiedTime.isNullOrBlank()) return null
    return runCatching {
        java.time.LocalDateTime.ofInstant(
            java.time.Instant.parse(modifiedTime),
            java.time.ZoneId.systemDefault()
        ).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
    }.getOrNull()
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
