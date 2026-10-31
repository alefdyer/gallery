package com.asinosoft.photogallery.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.asinosoft.photogallery.BuildConfig
import com.asinosoft.photogallery.OAuthRedirectBus
import com.asinosoft.photogallery.R
import com.asinosoft.photogallery.data.storage.Storage
import com.asinosoft.photogallery.data.storage.StorageCheckResult
import com.asinosoft.photogallery.data.storage.StorageType
import com.asinosoft.photogallery.data.storage.dropbox.DropboxApi
import com.asinosoft.photogallery.data.storage.gdrive.GoogleDriveGalleryAuth
import com.asinosoft.photogallery.data.storage.gdrive.GoogleDriveStorageProvider
import com.asinosoft.photogallery.data.storage.onedrive.OneDriveApi
import com.asinosoft.photogallery.data.storage.yandex.YandexStorageProvider
import com.asinosoft.photogallery.model.StoragesViewModel
import com.asinosoft.photogallery.ui.component.SettingsGroup
import com.asinosoft.photogallery.ui.component.SettingsGroupDivider
import com.asinosoft.photogallery.ui.component.SettingsPage
import com.asinosoft.photogallery.ui.component.SettingsSectionTitle
import com.asinosoft.photogallery.ui.component.StorageTypeIcon
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern
import kotlinx.coroutines.launch

@Composable
fun StoragesView(modifier: Modifier = Modifier, model: StoragesViewModel = hiltViewModel()) {
    val storages by model.storages.collectAsState(initial = listOf())

    val showStorageEditor = remember { mutableStateOf(false) }
    val account = remember { mutableStateOf<Storage?>(null) }

    val showEditor = {
        account.value = null
        showStorageEditor.value = true
    }

    if (showStorageEditor.value) {
        StorageEditor(
            storage = account.value,
            existingGoogleDriveEmails = storages
                .filter { it.type == StorageType.GOOGLE_DRIVE }
                .mapNotNull { it.login?.lowercase()?.takeIf(String::isNotBlank) }
                .toSet(),
            hasGoogleDrive = storages.any { it.type == StorageType.GOOGLE_DRIVE },
            onCheckStorageConnection = model::checkStorage,
            onSave = { storage ->
                model.addStorage(storage)
                showStorageEditor.value = false
            },
            onCancel = { showStorageEditor.value = false },
            modifier = modifier
        )
    } else {
        SettingsPage(modifier) {
            SettingsSectionTitle(stringResource(R.string.network_section))
            if (storages.isEmpty()) {
                Text(
                    text = stringResource(R.string.storage_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 12.dp)
                )
            }
            SettingsGroup {
                storages.forEachIndexed { index, storage ->
                    if (index > 0) SettingsGroupDivider()
                    ConnectedStorageRow(
                        storage = storage,
                        onEdit = {
                            account.value = storage
                            showStorageEditor.value = true
                        },
                        onDelete = { model.deleteStorage(storage) }
                    )
                }
                if (storages.isNotEmpty()) SettingsGroupDivider()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = showEditor)
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.add),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.storage_add_account),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.storage_add_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        painter = painterResource(R.drawable.chevron_right),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ConnectedStorageRow(
    storage: Storage,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.size(48.dp)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                StorageTypeIcon(storage.type)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = storage.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.storage_connected),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Icon(
            painter = painterResource(R.drawable.check),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        if (storage.type.isEditable) {
            IconButton(onClick = onEdit) {
                Icon(
                    painterResource(R.drawable.edit),
                    contentDescription = stringResource(R.string.edit),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (storage.type.isDeletable) {
            IconButton(onClick = onDelete) {
                Icon(
                    painterResource(R.drawable.delete),
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StorageEditor(
    storage: Storage?,
    existingGoogleDriveEmails: Set<String> = emptySet(),
    hasGoogleDrive: Boolean = false,
    onCheckStorageConnection: suspend (Storage) -> StorageCheckResult,
    onSave: (Storage) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var type by remember(storage) { mutableStateOf(storage?.type) }
    var checkError by remember(storage) { mutableStateOf<String?>(null) }
    var isChecking by remember(storage) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val serverNotFoundText = stringResource(R.string.storage_check_server_not_found)
    val authorizationFailedText = stringResource(R.string.storage_check_authorization_failed)
    val unknownErrorTemplate = stringResource(R.string.storage_check_unknown_error)

    BackHandler { onCancel() }

    fun clearCheckError() {
        checkError = null
    }

    fun submitStorage(nextStorage: Storage) = scope.launch {
        isChecking = true
        checkError = when (val error = onCheckStorageConnection(nextStorage)) {
            StorageCheckResult.Success -> {
                onSave(nextStorage)
                null
            }

            StorageCheckResult.ServerNotFound -> serverNotFoundText

            StorageCheckResult.AuthorizationFailed -> authorizationFailedText

            is StorageCheckResult.UnknownError ->
                unknownErrorTemplate.format(error.message ?: "")
        }
        isChecking = false
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                IconButton(onClick = {
                    if (type != null && storage == null) type = null else onCancel()
                }) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = stringResource(R.string.back)
                    )
                }
                Text(
                    text = when {
                        type == null -> stringResource(R.string.storage_add_title)
                        type == StorageType.WEBDAV && storage == null ->
                            stringResource(R.string.storage_webdav_connect)

                        type == StorageType.WEBDAV -> stringResource(R.string.storage_webdav_edit)
                        type == StorageType.NEXTCLOUD && storage == null ->
                            stringResource(R.string.storage_nextcloud_connect)

                        type == StorageType.NEXTCLOUD ->
                            stringResource(R.string.storage_nextcloud_edit)

                        else -> storageTypeTitle(type!!)
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            when (type) {
                null -> StorageTypeSelector(
                    modifier = Modifier.weight(1f),
                    onSelect = { type = it }
                )

                StorageType.NEXTCLOUD -> NextCloudStorageForm(
                    storage = storage,
                    onSave = ::submitStorage,
                    checkError = checkError,
                    isChecking = isChecking,
                    onInputChange = ::clearCheckError,
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                )

                StorageType.WEBDAV -> WebDavStorageForm(
                    storage = storage,
                    onSave = ::submitStorage,
                    checkError = checkError,
                    isChecking = isChecking,
                    onInputChange = ::clearCheckError,
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                )

                StorageType.DROPBOX -> DropboxStorageForm(
                    onSave = ::submitStorage,
                    onCancel = onCancel,
                    modifier = Modifier.weight(1f)
                )

                StorageType.YANDEX -> YandexStorageForm(
                    onSave = ::submitStorage,
                    onCancel = onCancel,
                    modifier = Modifier.weight(1f)
                )

                StorageType.GOOGLE_DRIVE -> GoogleDriveStorageForm(
                    existingEmails = existingGoogleDriveEmails,
                    forceAccountPicker = hasGoogleDrive,
                    onSave = ::submitStorage,
                    onCancel = onCancel,
                    modifier = Modifier.weight(1f)
                )

                StorageType.ONEDRIVE -> OneDriveStorageForm(
                    onSave = ::submitStorage,
                    onCancel = onCancel,
                    modifier = Modifier.weight(1f)
                )

                else -> Text(
                    stringResource(R.string.storage_not_implemented),
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun storageTypeTitle(type: StorageType): String = when (type) {
    StorageType.DROPBOX -> stringResource(R.string.storage_type_dropbox)
    StorageType.GOOGLE_DRIVE -> stringResource(R.string.storage_type_google_drive)
    StorageType.ONEDRIVE -> stringResource(R.string.storage_type_onedrive)
    StorageType.NEXTCLOUD -> stringResource(R.string.storage_type_nextcloud)
    StorageType.WEBDAV -> stringResource(R.string.storage_type_webdav)
    StorageType.YANDEX -> stringResource(R.string.storage_type_yandex_disk)
    StorageType.LOCAL -> stringResource(R.string.phone)
}

@Composable
private fun StorageTypeSelector(
    onSelect: (StorageType) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPage(modifier) {
        Text(
            text = stringResource(R.string.storage_add_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 12.dp)
        )
        SettingsGroup {
            StorageProviderOption(
                type = StorageType.GOOGLE_DRIVE,
                title = stringResource(R.string.storage_type_google_drive),
                subtitle = stringResource(R.string.storage_type_google_drive_hint),
                onClick = { onSelect(StorageType.GOOGLE_DRIVE) }
            )
            SettingsGroupDivider()
            StorageProviderOption(
                type = StorageType.ONEDRIVE,
                title = stringResource(R.string.storage_type_onedrive),
                subtitle = stringResource(R.string.storage_type_onedrive_hint),
                onClick = { onSelect(StorageType.ONEDRIVE) }
            )
            SettingsGroupDivider()
            StorageProviderOption(
                type = StorageType.DROPBOX,
                title = stringResource(R.string.storage_type_dropbox),
                subtitle = stringResource(R.string.storage_type_dropbox_hint),
                onClick = { onSelect(StorageType.DROPBOX) }
            )
            SettingsGroupDivider()
            StorageProviderOption(
                type = StorageType.YANDEX,
                title = stringResource(R.string.storage_type_yandex_disk),
                subtitle = stringResource(R.string.storage_type_yandex_hint),
                onClick = { onSelect(StorageType.YANDEX) }
            )
            SettingsGroupDivider()
            StorageProviderOption(
                type = StorageType.NEXTCLOUD,
                title = stringResource(R.string.storage_type_nextcloud),
                subtitle = stringResource(R.string.storage_type_nextcloud_hint),
                onClick = { onSelect(StorageType.NEXTCLOUD) }
            )
            SettingsGroupDivider()
            StorageProviderOption(
                type = StorageType.WEBDAV,
                title = stringResource(R.string.storage_type_webdav),
                subtitle = stringResource(R.string.storage_type_webdav_hint),
                onClick = { onSelect(StorageType.WEBDAV) }
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun StorageProviderOption(
    type: StorageType,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.size(48.dp)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                StorageTypeIcon(type)
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
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
        Icon(
            painter = painterResource(R.drawable.chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun WebDavStorageForm(
    storage: Storage?,
    onSave: (Storage) -> Unit,
    checkError: String?,
    isChecking: Boolean,
    onInputChange: () -> Unit,
    modifier: Modifier = Modifier
) {
    var url by remember(storage) { mutableStateOf(storage?.url?.toString() ?: "") }
    var login by remember(storage) { mutableStateOf(storage?.login ?: "") }
    var password by remember(storage) { mutableStateOf(storage?.password ?: "") }

    val focus = remember { FocusRequester() }
    LaunchedEffect(storage) {
        if (storage == null) {
            focus.requestFocus()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = url,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Next,
                keyboardType = KeyboardType.Uri
            ),
            onValueChange = {
                url = it
                onInputChange()
            },
            label = { Text(stringResource(R.string.storage_field_host)) },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus)
        )
        OutlinedTextField(
            value = login,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            onValueChange = {
                login = it
                onInputChange()
            },
            label = { Text(stringResource(R.string.storage_field_login)) },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = password,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Next,
                keyboardType = KeyboardType.Password
            ),
            onValueChange = {
                password = it
                onInputChange()
            },
            label = { Text(stringResource(R.string.storage_field_password)) },
            modifier = Modifier.fillMaxWidth()
        )
        if (checkError != null) {
            Text(
                text = checkError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Button(
            enabled = !isChecking && url.isNotBlank(),
            onClick = {
                onSave(
                    Storage(
                        id = storage?.id ?: 0,
                        type = StorageType.WEBDAV,
                        url = url.toUri(),
                        login = login.trim().ifBlank { null },
                        password = password.ifBlank { null }
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isChecking) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text(
                    if (storage == null) {
                        stringResource(R.string.storage_action_add)
                    } else {
                        stringResource(R.string.save)
                    }
                )
            }
        }
    }
}

@Composable
private fun NextCloudStorageForm(
    storage: Storage?,
    onSave: (Storage) -> Unit,
    checkError: String?,
    isChecking: Boolean,
    onInputChange: () -> Unit,
    modifier: Modifier = Modifier
) {
    var host by remember(storage) { mutableStateOf(storage?.url?.toString() ?: "") }
    var login by remember(storage) { mutableStateOf(storage?.login ?: "") }
    var password by remember(storage) { mutableStateOf(storage?.password ?: "") }

    val focus = remember { FocusRequester() }
    LaunchedEffect(storage) {
        if (storage == null) {
            focus.requestFocus()
        }
    }

    val save = {
        if (host.isNotEmpty() && login.isNotEmpty() && password.isNotEmpty()) {
            val url = if (host.startsWith("http://") or host.startsWith("https://")) {
                host.toUri()
            } else {
                "https://$host".toUri()
            }
            val login = login.trim().ifBlank { null }
            val password = password.trim().ifBlank { null }

            onSave(
                Storage(
                    id = storage?.id ?: 0,
                    type = StorageType.NEXTCLOUD,
                    url = url,
                    login = login,
                    password = password
                )
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = host,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Next,
                keyboardType = KeyboardType.Uri
            ),
            onValueChange = {
                host = it
                onInputChange()
            },
            label = { Text(stringResource(R.string.storage_field_host)) },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus)
        )
        OutlinedTextField(
            value = login,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            onValueChange = {
                login = it
                onInputChange()
            },
            label = { Text(stringResource(R.string.storage_field_login)) },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = password,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done,
                keyboardType = KeyboardType.Password
            ),
            keyboardActions = KeyboardActions(onDone = { save() }),
            onValueChange = {
                password = it
                onInputChange()
            },
            label = { Text(stringResource(R.string.storage_field_password)) },
            modifier = Modifier.fillMaxWidth()
        )

        if (null != checkError) {
            Text(
                text = checkError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Button(
            enabled = !isChecking && host.isNotEmpty() && login.isNotEmpty() && password.isNotEmpty(),
            onClick = save,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isChecking) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text(
                    if (storage == null) {
                        stringResource(R.string.storage_action_add)
                    } else {
                        stringResource(R.string.save)
                    }
                )
            }
        }
    }
}

@Composable
private fun DropboxStorageForm(
    onSave: (Storage) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val onSave by rememberUpdatedState(onSave)
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        OAuthRedirectBus.events.collect { uri ->
            DropboxApi.getTokenFromUrl(uri)
                .onSuccess {
                    onSave(
                        Storage(
                            type = StorageType.DROPBOX,
                            url = DropboxApi.BASE_URL.toUri(),
                            password = it
                        )
                    )
                }
                .onFailure { error = it.message ?: "Can't get token" }
        }
    }

    LaunchedEffect(Unit) {
        runCatching {
            val intent = CustomTabsIntent.Builder().build()
            intent.intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            intent.launchUrl(context, DropboxApi.startAuthorization())
        }.onFailure { error = it.message ?: "Authorization failed" }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (error == null) {
            CircularProgressIndicator(strokeWidth = 2.dp)
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.storage_type_dropbox),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.storage_type_dropbox_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                error!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onCancel) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}

@Composable
private fun GoogleDriveStorageForm(
    existingEmails: Set<String> = emptySet(),
    forceAccountPicker: Boolean = false,
    onSave: (Storage) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val onSave by rememberUpdatedState(onSave)
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var selectedAccount by remember { mutableStateOf<android.accounts.Account?>(null) }
    val alreadyConnectedText = stringResource(R.string.storage_gdrive_already_connected)

    fun finishWithToken(token: String, preferredEmail: String? = null) {
        scope.launch {
            busy = true
            val email = preferredEmail?.takeIf { it.isNotBlank() }
                ?: withContext(Dispatchers.IO) {
                    GoogleDriveStorageProvider.fetchEmail(token)
                }
            if (!email.isNullOrBlank() && email.lowercase() in existingEmails) {
                busy = false
                error = alreadyConnectedText
                return@launch
            }
            // Уже есть Drive, а email не узнали — не добавляем «безликий» дубликат.
            if (forceAccountPicker && email.isNullOrBlank()) {
                busy = false
                error = alreadyConnectedText
                return@launch
            }
            onSave(
                Storage(
                    type = StorageType.GOOGLE_DRIVE,
                    url = "https://www.googleapis.com/".toUri(),
                    login = email,
                    password = token
                )
            )
            busy = false
        }
    }

    val authLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val host = activity ?: return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            when (
                val outcome = GoogleDriveGalleryAuth.handleAuthorizeResult(
                    host,
                    result,
                    selectedAccount
                )
            ) {
                is GoogleDriveGalleryAuth.AuthOutcome.Success ->
                    finishWithToken(outcome.token, selectedAccount?.name)

                GoogleDriveGalleryAuth.AuthOutcome.Cancelled -> {
                    busy = false
                    onCancel()
                }

                is GoogleDriveGalleryAuth.AuthOutcome.Failed -> {
                    busy = false
                    error = outcome.message
                }
            }
        }
    }

    val accountPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val host = activity ?: return@rememberLauncherForActivityResult
        val account = GoogleDriveGalleryAuth.accountFromChooserResult(result)
        if (account == null) {
            onCancel()
            return@rememberLauncherForActivityResult
        }
        if (account.name.lowercase() in existingEmails) {
            error = alreadyConnectedText
            busy = false
            return@rememberLauncherForActivityResult
        }
        selectedAccount = account
        scope.launch {
            busy = true
            runCatching {
                when (val start = GoogleDriveGalleryAuth.beginAuthorize(host, account)) {
                    is GoogleDriveGalleryAuth.AuthStart.Success ->
                        finishWithToken(start.token, account.name)

                    is GoogleDriveGalleryAuth.AuthStart.NeedsUi -> {
                        busy = false
                        authLauncher.launch(
                            IntentSenderRequest.Builder(start.pendingIntent.intentSender).build()
                        )
                    }
                }
            }.onFailure {
                busy = false
                error = it.message ?: "Authorization failed"
            }
        }
    }

    LaunchedEffect(Unit) {
        val host = activity
        if (host == null) {
            error = "Activity required"
            return@LaunchedEffect
        }
        if (forceAccountPicker) {
            busy = false
            accountPickerLauncher.launch(GoogleDriveGalleryAuth.accountChooserIntent())
            return@LaunchedEffect
        }
        busy = true
        runCatching {
            when (val start = GoogleDriveGalleryAuth.beginAuthorize(host)) {
                is GoogleDriveGalleryAuth.AuthStart.Success -> finishWithToken(start.token)
                is GoogleDriveGalleryAuth.AuthStart.NeedsUi -> {
                    busy = false
                    authLauncher.launch(
                        IntentSenderRequest.Builder(start.pendingIntent.intentSender).build()
                    )
                }
            }
        }.onFailure {
            busy = false
            error = it.message ?: "Authorization failed"
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (error == null) {
            if (busy) {
                CircularProgressIndicator(strokeWidth = 2.dp)
                Spacer(Modifier.height(16.dp))
            }
            Text(
                stringResource(R.string.storage_gdrive_auth_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.storage_type_google_drive_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(error!!, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        }
    }
}

@Composable
private fun OneDriveStorageForm(
    onSave: (Storage) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val onSave by rememberUpdatedState(onSave)
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        OAuthRedirectBus.events.collect { uri ->
            OneDriveApi.getTokenFromUrl(uri)
                .onSuccess { token ->
                    onSave(
                        Storage(
                            type = StorageType.ONEDRIVE,
                            url = "https://graph.microsoft.com/".toUri(),
                            login = token.email,
                            password = token.accessToken
                        )
                    )
                }
                .onFailure { error = it.message ?: "Can't get token" }
        }
    }

    LaunchedEffect(Unit) {
        if (BuildConfig.ONEDRIVE_CLIENT_ID.isBlank()) {
            error = "Set onedrive.clientId in local.properties"
            return@LaunchedEffect
        }
        runCatching {
            val intent = CustomTabsIntent.Builder().build()
            intent.intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            intent.launchUrl(context, OneDriveApi.startAuthorization())
        }.onFailure { error = it.message ?: "Authorization failed" }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (error == null) {
            CircularProgressIndicator(strokeWidth = 2.dp)
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.storage_onedrive_auth_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.storage_type_onedrive_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(error!!, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        }
    }
}

@Composable
private fun YandexStorageForm(
    onSave: (Storage) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val onSave by rememberUpdatedState(onSave)
    val delivered = remember { AtomicBoolean(false) }
    val pattern = Pattern.compile("access_token=([-_a-zA-Z0-9]+)")
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        OAuthRedirectBus.events.collect { uri ->
            if (!uri.toString().startsWith(BuildConfig.YANDEX_REDIRECT_URI)) return@collect
            val url = uri.toString()
            val matcher = pattern.matcher(url)
            if (!matcher.find()) return@collect

            val token: String = matcher.group(1) ?: return@collect
            if (delivered.compareAndSet(false, true)) {
                onSave(
                    Storage(
                        type = StorageType.YANDEX,
                        password = token
                    )
                )
            }
        }
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.storage_yandex_auth_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    TextButton(onClick = onCancel) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
                Spacer(Modifier.heightIn(min = 16.dp))
                Button(
                    onClick = {
                        val intent = CustomTabsIntent.Builder().build()
                        intent.intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                        intent.launchUrl(context, YandexStorageProvider.AUTHORIZATION_URL.toUri())
                    },
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(8.dp)
                ) {
                    Text(text = stringResource(R.string.storage_yandex_sign_in))
                }
            }
        }
    }
}
