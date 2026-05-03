package com.asinosoft.gallery.ui

import androidx.activity.compose.BackHandler
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import com.asinosoft.gallery.R
import com.asinosoft.gallery.data.storage.Storage
import com.asinosoft.gallery.data.storage.StorageCheckResult
import com.asinosoft.gallery.data.storage.StorageType
import com.asinosoft.gallery.data.storage.yandex.YandexOAuth
import com.asinosoft.gallery.data.storage.yandex.YandexOAuthBus
import com.asinosoft.gallery.data.storage.yandex.YandexOAuthEvent
import com.asinosoft.gallery.ui.component.StorageTypeIcon
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.launch

@Composable
fun StoragesView(
    storages: List<Storage>,
    onCheckStorageConnection: suspend (Storage) -> StorageCheckResult,
    onAddStorage: (Storage) -> Unit,
    onDeleteStorage: (Storage) -> Unit,
    modifier: Modifier = Modifier
) {
    val showStorageEditor = remember { mutableStateOf(false) }
    val account = remember { mutableStateOf<Storage?>(null) }

    val showEditor = {
        account.value = null
        showStorageEditor.value = true
    }

    if (showStorageEditor.value) {
        StorageEditor(
            storage = account.value,
            onCheckStorageConnection = onCheckStorageConnection,
            onSave = { storage ->
                onAddStorage(storage)
                showStorageEditor.value = false
            },
            onCancel = { showStorageEditor.value = false }
        )
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(storages, key = { it.id }) { storage ->
                Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StorageTypeIcon(storage.type)

                            Text(
                                text = storage.url?.toString() ?: "",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(2f)
                            )

                            if (storage.type.isEditable) {
                                IconButton(onClick = {
                                    account.value = storage
                                    showStorageEditor.value = true
                                }) {
                                    Icon(
                                        painterResource(R.drawable.edit),
                                        contentDescription = null
                                    )
                                }
                            }

                            if (storage.type.isDeletable) {
                                IconButton(onClick = {
                                    onDeleteStorage(storage)
                                }) {
                                    Icon(
                                        painterResource(R.drawable.delete),
                                        contentDescription = null
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Box(
                    modifier = Modifier.fillParentMaxHeight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Button(onClick = showEditor) {
                        Icon(painterResource(R.drawable.add), contentDescription = null)
                        Text(stringResource(R.string.storage_add_account))
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

            StorageType.YANDEX -> YandexStorageForm(
                onSave = ::submitStorage,
                onCancel = { onCancel() }
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
            modifier = Modifier.fillMaxWidth().focusRequester(focus)
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
            modifier = Modifier.fillMaxWidth().focusRequester(focus)
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
private fun YandexStorageForm(
    onSave: (Storage) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val onSaveUpdated by rememberUpdatedState(onSave)
    val delivered = remember { AtomicBoolean(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose { YandexOAuth.cancelPending() }
    }

    LaunchedEffect(Unit) {
        YandexOAuthBus.events.collect { event ->
            when (event) {
                is YandexOAuthEvent.Success -> {
                    if (delivered.compareAndSet(false, true)) {
                        onSaveUpdated(
                            Storage(
                                type = StorageType.YANDEX,
                                password = event.accessToken
                            )
                        )
                    }
                }

                is YandexOAuthEvent.Failure -> {
                    authError = event.message
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier.fillMaxSize().padding(8.dp)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
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
                Text(
                    text = stringResource(R.string.storage_yandex_oauth_hint),
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(
                    onClick = {
                        authError = null
                        runCatching {
                            val uri = YandexOAuth.startAuthorization()
                            CustomTabsIntent.Builder().build().launchUrl(context, uri)
                        }.onFailure { e ->
                            authError = e.message ?: e.toString()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.storage_yandex_sign_in))
                }
                authError?.let { err ->
                    Text(text = err, color = Color.Red, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
