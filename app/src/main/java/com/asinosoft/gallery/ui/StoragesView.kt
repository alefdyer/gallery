package com.asinosoft.gallery.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.asinosoft.gallery.R
import com.asinosoft.gallery.data.storage.Storage
import com.asinosoft.gallery.data.storage.StorageType
import com.asinosoft.gallery.ui.component.StorageTypeIcon

@Composable
fun StoragesView(
    storages: List<Storage>,
    onAddStorage: (Storage) -> Unit,
    onDeleteStorage: (Storage) -> Unit,
    modifier: Modifier = Modifier
) {
    val showStorageEditor = remember { mutableStateOf(false) }
    val account = remember { mutableStateOf<Storage?>(null) }

    if (showStorageEditor.value) {
        StorageEditor(
            storage = account.value,
            onSave = { storage ->
                onAddStorage(storage)
                showStorageEditor.value = false
            },
            onCancel = { showStorageEditor.value = false }
        )
    } else {
        LazyColumn(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp)
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

                            IconButton(onClick = {
                                account.value = storage
                                showStorageEditor.value = true
                            }) {
                                Icon(painterResource(R.drawable.edit), contentDescription = null)
                            }

                            IconButton(onClick = {
                                onDeleteStorage(storage)
                            }) {
                                Icon(painterResource(R.drawable.delete), contentDescription = null)
                            }
                        }
                    }
                }
            }

            item {
                IconButton(onClick = {
                    account.value = null
                    showStorageEditor.value = true
                }) {
                    Icon(painterResource(R.drawable.add), contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun StorageEditor(
    storage: Storage?,
    onSave: (Storage) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var type by remember(storage) { mutableStateOf(storage?.type) }

    BackHandler { onCancel() }

    Card(modifier.fillMaxSize()) {
        when (type) {
            null -> StorageTypeSelector { type = it }
            StorageType.NEXTCLOUD -> NextCloudStorageForm(storage, onSave)
            StorageType.WEBDAV -> WebDavStorageForm(storage, onSave)
            else -> Text("Not realized")
        }
    }
}

@Composable
private fun ColumnScope.StorageTypeSelector(onSelect: (StorageType) -> Unit) {
    Text(
        text = "Add storage account",
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
            Text("Dropbox")
        }
        Button({ onSelect(StorageType.NEXTCLOUD) }, Modifier.fillMaxWidth()) {
            Image(
                painterResource(R.drawable.nextcloud),
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("NextCloud")
        }
        Button({ onSelect(StorageType.WEBDAV) }, Modifier.fillMaxWidth()) {
            Image(
                painterResource(R.drawable.webdav),
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("WebDAV")
        }
        Button({ onSelect(StorageType.YANDEX) }, Modifier.fillMaxWidth()) {
            Image(
                painterResource(R.drawable.yandex_disk),
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("Yandex Disk", softWrap = false)
        }
    }
}

@Composable
private fun WebDavStorageForm(
    storage: Storage?,
    onSave: (Storage) -> Unit,
    modifier: Modifier = Modifier
) {
    var url by remember(storage) { mutableStateOf(storage?.url?.toString() ?: "") }
    var username by remember(storage) { mutableStateOf(storage?.username ?: "") }
    var secret by remember(storage) { mutableStateOf(storage?.secret ?: "") }
    var rootPath by remember(storage) { mutableStateOf(storage?.rootPath ?: "") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(if (storage == null) "Add storage account" else "Edit storage account")
        OutlinedTextField(
            value = url,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Next,
                keyboardType = KeyboardType.Uri
            ),
            onValueChange = { url = it },
            label = { Text("Base URL") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = username,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            onValueChange = { username = it },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = secret,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Next,
                keyboardType = KeyboardType.Password
            ),
            onValueChange = { secret = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = rootPath,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            onValueChange = { rootPath = it },
            label = { Text("Root path") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (url.isBlank()) {
                        return@Button
                    }
                    onSave(
                        Storage(
                            id = storage?.id ?: 0,
                            type = StorageType.WEBDAV,
                            url = url.toUri(),
                            username = username.trim().ifBlank { null },
                            secret = secret.ifBlank { null },
                            rootPath = rootPath.trim().ifBlank { null }
                        )
                    )
                }
            ) { Text(if (storage == null) "Add" else "Save") }
        }
    }
}

@Composable
private fun NextCloudStorageForm(
    storage: Storage?,
    onSave: (Storage) -> Unit,
    modifier: Modifier = Modifier
) {
    var url by remember(storage) { mutableStateOf(storage?.url?.toString() ?: "") }
    var username by remember(storage) { mutableStateOf(storage?.username ?: "") }
    var secret by remember(storage) { mutableStateOf(storage?.secret ?: "") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(if (storage == null) "Add storage account" else "Edit storage account")
        OutlinedTextField(
            value = url,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Next,
                keyboardType = KeyboardType.Uri
            ),
            onValueChange = { url = it },
            label = { Text("Base URL") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = username,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            onValueChange = { username = it },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = secret,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done,
                keyboardType = KeyboardType.Password
            ),
            onValueChange = { secret = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (url.isBlank()) {
                        return@Button
                    }
                    onSave(
                        Storage(
                            id = storage?.id ?: 0,
                            type = StorageType.NEXTCLOUD,
                            url = url.toUri(),
                            username = username.trim().ifBlank { null },
                            secret = secret.ifBlank { null }
                        )
                    )
                }
            ) { Text(if (storage == null) "Add" else "Save") }
        }
    }
}
