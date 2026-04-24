package com.asinosoft.gallery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.asinosoft.gallery.R
import com.asinosoft.gallery.data.storage.Storage
import com.asinosoft.gallery.data.storage.StorageType

@Composable
fun StoragesView(
    storages: List<Storage>,
    onAddStorage: (Storage) -> Unit,
    onDeleteStorage: (Storage) -> Unit,
    modifier: Modifier = Modifier
) {
    val editing = remember { mutableStateOf<Storage?>(null) }

    if (null == editing.value) {
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
                        Text("[${storage.id}] ${storage.name} (${storage.type.name})")
                        storage.url?.takeIf { it.isNotBlank() }?.let { Text(it) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { editing.value = storage }) { Text("Edit") }
                            Button(onClick = { onDeleteStorage(storage) }) { Text("Delete") }
                        }
                    }
                }
            }

            item {
                IconButton(onClick = { editing.value = Storage(name = "Ноаое") }) {
                    Icon(painterResource(R.drawable.add), contentDescription = null)
                }
            }
        }
    } else {
        StorageAccountForm(
            storage = editing.value,
            onSave = { storage ->
                onAddStorage(storage)
                editing.value = null
            }
        )
    }
}

@Composable
private fun StorageAccountForm(
    storage: Storage?,
    onSave: (Storage) -> Unit,
    modifier: Modifier = Modifier
) {
    var displayName by remember(storage?.id) { mutableStateOf(storage?.name ?: "") }
    var baseUrl by remember(storage?.id) { mutableStateOf(storage?.url ?: "") }
    var username by remember(storage?.id) { mutableStateOf(storage?.username ?: "") }
    var secret by remember(storage?.id) { mutableStateOf(storage?.secret ?: "") }
    var rootPath by remember(storage?.id) { mutableStateOf(storage?.rootPath ?: "") }
    var type by remember(storage?.id) { mutableStateOf(storage?.type ?: StorageType.WEBDAV) }
    val remoteTypes = StorageType.entries.filterNot { it == StorageType.LOCAL }

    Card(modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(if (storage == null) "Add storage account" else "Edit storage account")
            OutlinedTextField(value = displayName, onValueChange = {
                displayName = it
            }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
            Text("Storage type: ${type.name}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val current = remoteTypes.indexOf(type).coerceAtLeast(0)
                        type = remoteTypes[(current + 1) % remoteTypes.size]
                    }
                ) { Text("Next type") }
            }
            OutlinedTextField(value = baseUrl, onValueChange = {
                baseUrl = it
            }, label = { Text("Base URL") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = username, onValueChange = {
                username = it
            }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = secret, onValueChange = {
                secret = it
            }, label = { Text("Password/Token") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = rootPath, onValueChange = {
                rootPath = it
            }, label = { Text("Root path") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (displayName.isBlank()) {
                            return@Button
                        }
                        onSave(
                            Storage(
                                id = storage?.id ?: 0,
                                type = type,
                                name = displayName.trim(),
                                url = baseUrl.trim().ifBlank { null },
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
}
