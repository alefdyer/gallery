package com.asinosoft.gallery.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.asinosoft.gallery.R
import com.asinosoft.gallery.model.BackupUiEvent
import com.asinosoft.gallery.model.SettingsBackupViewModel
import com.asinosoft.gallery.ui.component.BackupProgressBanner
import com.asinosoft.gallery.ui.component.GallerySettings
import com.asinosoft.gallery.ui.component.SettingsGroup
import com.asinosoft.gallery.ui.component.SettingsGroupDivider
import com.asinosoft.gallery.ui.component.SettingsPage
import com.asinosoft.gallery.ui.component.SettingsSectionTitle
import com.asinosoft.gallery.ui.component.rememberAutoplayVideo
import com.asinosoft.gallery.ui.component.rememberVideoSound

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
    var showRestoreConfirm by remember { mutableStateOf(false) }

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

                BackupUiEvent.Saved ->
                    Toast.makeText(
                        context,
                        context.getString(R.string.backup_saved),
                        Toast.LENGTH_SHORT
                    ).show()

                is BackupUiEvent.Restored ->
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

                is BackupUiEvent.Error ->
                    Toast.makeText(
                        context,
                        event.message.ifBlank { context.getString(R.string.backup_error) },
                        Toast.LENGTH_LONG
                    ).show()
            }
        }
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text(stringResource(R.string.backup_restore_title)) },
            text = { Text(stringResource(R.string.backup_restore_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreConfirm = false
                    backupModel.restore()
                }) { Text(stringResource(R.string.backup_restore)) }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false }) {
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
                    onClick = { backupModel.backup() }
                )
                SettingsGroupDivider()
                SettingsActionRow(
                    title = stringResource(R.string.backup_restore),
                    subtitle = stringResource(R.string.backup_restore_hint),
                    enabled = backupState.connected && !backupState.busy,
                    onClick = { showRestoreConfirm = true }
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

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
