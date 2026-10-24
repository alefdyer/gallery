package com.asinosoft.photogallery.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.clickable
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
import com.asinosoft.photogallery.data.storage.yandex.YandexStorageProvider
import com.asinosoft.photogallery.model.StoragesViewModel
import com.asinosoft.photogallery.ui.component.SettingsGroup
import com.asinosoft.photogallery.ui.component.SettingsGroupDivider
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
            onCheckStorageConnection = model::checkStorage,
            onSave = { storage ->
                model.addStorage(storage)
                showStorageEditor.value = false
            },
            onCancel = { showStorageEditor.value = false },
            modifier = modifier
        )
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            item {
                SettingsSectionTitle(stringResource(R.string.network_section))
                SettingsGroup {
                    storages.forEachIndexed { index, storage ->
                        if (index > 0) SettingsGroupDivider()
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            StorageTypeIcon(storage.type)
                            Text(
                                text = storage.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (storage.type.isEditable) {
                                IconButton(onClick = {
                                    account.value = storage
                                    showStorageEditor.value = true
                                }) {
                                    Icon(
                                        painterResource(R.drawable.edit),
                                        contentDescription = stringResource(R.string.edit),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (storage.type.isDeletable) {
                                IconButton(onClick = { model.deleteStorage(storage) }) {
                                    Icon(
                                        painterResource(R.drawable.delete),
                                        contentDescription = stringResource(R.string.delete),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
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
                        Text(
                            text = stringResource(R.string.storage_add_account),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageEditor(
    storage: Storage?,
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

    Card(modifier.fillMaxSize()) {
        when (type) {
            null -> StorageTypeSelector { type = it }

            StorageType.NEXTCLOUD -> NextCloudStorageForm(
                storage = storage,
                onSave = ::submitStorage,
                checkError = checkError,
                isChecking = isChecking,
                onInputChange = ::clearCheckError
            )

            StorageType.WEBDAV -> WebDavStorageForm(
                storage = storage,
                onSave = ::submitStorage,
                checkError = checkError,
                isChecking = isChecking,
                onInputChange = ::clearCheckError
            )

            StorageType.DROPBOX -> DropboxStorageForm(
                onSave = ::submitStorage,
                onCancel = onCancel
            )

            StorageType.YANDEX -> YandexStorageForm(
                onSave = ::submitStorage,
                onCancel = onCancel
            )

            else -> Text(stringResource(R.string.storage_not_implemented))
        }
    }
}

@Composable
private fun ColumnScope.StorageTypeSelector(onSelect: (StorageType) -> Unit) {
    Text(
        text = stringResource(R.string.storage_add_account),
        modifier = Modifier
            .padding(top = 100.dp)
            .align(Alignment.CenterHorizontally),
        style = MaterialTheme.typography.titleLarge
    )

    Column(
        verticalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .width(IntrinsicSize.Max)
            .fillMaxHeight()
            .padding(12.dp)
    ) {
        Button({ onSelect(StorageType.DROPBOX) }, Modifier.fillMaxWidth()) {
            Image(
                painterResource(R.drawable.dropbox),
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.storage_type_dropbox))
        }
        Button({ onSelect(StorageType.NEXTCLOUD) }, Modifier.fillMaxWidth()) {
            Image(
                painterResource(R.drawable.nextcloud),
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.storage_type_nextcloud))
        }
        Button({ onSelect(StorageType.WEBDAV) }, Modifier.fillMaxWidth()) {
            Image(
                painterResource(R.drawable.webdav),
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.storage_type_webdav))
        }
        Button({ onSelect(StorageType.YANDEX) }, Modifier.fillMaxWidth()) {
            Image(
                painterResource(R.drawable.yandex_disk),
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.storage_type_yandex_disk), softWrap = false)
        }
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
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StorageTypeIcon(StorageType.WEBDAV)
            Text(
                if (storage == null) {
                    stringResource(R.string.storage_webdav_connect)
                } else {
                    stringResource(R.string.storage_webdav_edit)
                }
            )
        }
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
                color = Color.Red
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !isChecking,
                onClick = {
                    if (url.isBlank()) {
                        return@Button
                    }
                    onSave(
                        Storage(
                            id = storage?.id ?: 0,
                            type = StorageType.WEBDAV,
                            url = url.toUri(),
                            login = login.trim().ifBlank { null },
                            password = password.ifBlank { null }
                        )
                    )
                }
            ) {
                if (isChecking) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
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
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StorageTypeIcon(StorageType.NEXTCLOUD)
            Text(
                if (storage == null) {
                    stringResource(R.string.storage_nextcloud_connect)
                } else {
                    stringResource(R.string.storage_nextcloud_edit)
                }
            )
        }
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

        Row(horizontalArrangement = Arrangement.End) {
            Button(
                enabled = !isChecking,
                onClick = save
            ) {
                if (isChecking) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
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

        if (null != checkError) {
            Text(text = checkError, color = Color.Red)
        }
    }
}

@Composable
private fun DropboxStorageForm(onSave: (Storage) -> Unit, onCancel: () -> Unit) {
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

    error?.let { error ->
        Column {
            Text(error, color = Color.Red, style = MaterialTheme.typography.headlineMedium)

            Button(onClick = onCancel) {
                Text(stringResource(android.R.string.cancel))
            }
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
