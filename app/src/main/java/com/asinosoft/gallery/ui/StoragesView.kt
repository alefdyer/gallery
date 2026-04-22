package com.asinosoft.gallery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.asinosoft.gallery.data.storage.Storage
import com.asinosoft.gallery.data.storage.StorageType

@Composable
fun StoragesView(
    storages: List<Storage>,
    onSave: (Storage) -> Unit,
    onDelete: (Storage) -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by remember { mutableStateOf<Storage?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StorageAccountForm(account = editing, onSave = onSave) { editing = null }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(storages, key = { it.id }) { account ->
                Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("${account.name} (${account.type.name})")
                        account.url?.takeIf { it.isNotBlank() }?.let { Text(it) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { editing = account }) { Text("Edit") }
                            Button(onClick = { onDelete(account) }) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageAccountForm(
    account: Storage?,
    onSave: (Storage) -> Unit,
    afterSave: () -> Unit
) {
    var displayName by remember(account?.id) { mutableStateOf(account?.name ?: "") }
    var baseUrl by remember(account?.id) { mutableStateOf(account?.url ?: "") }
    var username by remember(account?.id) { mutableStateOf(account?.username ?: "") }
    var secret by remember(account?.id) { mutableStateOf(account?.secret ?: "") }
    var rootPath by remember(account?.id) { mutableStateOf(account?.rootPath ?: "") }
    var type by remember(account?.id) { mutableStateOf(account?.type ?: StorageType.WEBDAV) }
    val remoteTypes = StorageType.entries.filterNot { it == StorageType.LOCAL }

    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(if (account == null) "Add storage account" else "Edit storage account")
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
                                id = account?.id ?: 0,
                                type = type,
                                name = displayName.trim(),
                                url = baseUrl.trim().ifBlank { null },
                                username = username.trim().ifBlank { null },
                                secret = secret.ifBlank { null },
                                rootPath = rootPath.trim().ifBlank { null }
                            )
                        )
                        afterSave()
                    }
                ) { Text(if (account == null) "Add" else "Save") }
            }
        }
    }
}
