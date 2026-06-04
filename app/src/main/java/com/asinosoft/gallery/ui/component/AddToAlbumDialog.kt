package com.asinosoft.gallery.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.asinosoft.gallery.data.Album
import com.asinosoft.gallery.data.AlbumCategory
import com.asinosoft.gallery.data.name
import com.asinosoft.gallery.model.ImageListViewModel

@Composable
fun AddToAlbumDialog(
    onPickAlbum: (Album) -> Unit,
    onCreateAlbum: (String, AlbumCategory) -> Unit,
    onDismiss: () -> Unit,
) {
    var newAlbumMode by remember { mutableStateOf(false) }
    val newAlbumName = rememberTextFieldState()
    val newAlbumCategory = remember { mutableStateOf(AlbumCategory.OTHER) }


    BackHandler(newAlbumMode) { newAlbumMode = false }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to album") },
        text = {
            if (newAlbumMode) {
                NewAlbumDialog(
                    newAlbumName,
                    newAlbumCategory,
                    onSubmit = {
                        onCreateAlbum(newAlbumName.text.toString(), newAlbumCategory.value)
                        onDismiss()
                    },
                )
            } else {
                AlbumSelector(
                    onAlbumClick = { album ->
                        onPickAlbum(album)
                        onDismiss()
                    },
                    onNewAlbumClick = {
                        newAlbumCategory.value = it
                        newAlbumMode = true
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            if (newAlbumMode) {
                TextButton(
                    enabled = newAlbumName.text.isNotBlank(),
                    onClick = {
                        onCreateAlbum(newAlbumName.text.toString(), newAlbumCategory.value)
                        onDismiss()
                    }
                ) {
                    Text("Add")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewAlbumDialog(
    newAlbumName: TextFieldState,
    newAlbumCategory: MutableState<AlbumCategory>,
    onSubmit: () -> Unit,
    model: ImageListViewModel = hiltViewModel()
) {
    val focus = remember { FocusRequester() }
    val categories by model.categories.collectAsState()
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        focus.requestFocus()
    }

    Column {
        OutlinedTextField(
            label = { Text("Name") },
            state = newAlbumName,
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done
            ),
            onKeyboardAction = {
                if (newAlbumName.text.isNotBlank()) {
                    onSubmit()
                }
            },
            modifier = Modifier.focusRequester(focus)
        )

        Spacer(Modifier.height(4.dp))

        OutlinedTextField(
            label = { Text("Category") },
            value = newAlbumCategory.value.name(),
            onValueChange = { },
            readOnly = true,
            enabled = false, // to make clickable work
            modifier = Modifier.clickable { expanded = true },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            colors = OutlinedTextFieldDefaults.colors().copy(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledIndicatorColor = MaterialTheme.colorScheme.outline,
                disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.name()) },
                    onClick = {
                        newAlbumCategory.value = category
                        expanded = false
                    }
                )
            }
        }
    }
}