package com.asinosoft.gallery.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.asinosoft.gallery.data.Album
import com.asinosoft.gallery.data.AlbumWithCover
import com.asinosoft.gallery.ui.AlbumListView

@Composable
fun AddToAlbumDialog(
    albums: List<AlbumWithCover>,
    onPickAlbum: (Album) -> Unit,
    onCreateAlbum: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newAlbumMode by remember { mutableStateOf(false) }
    val newAlbumName = rememberTextFieldState()
    val focus = remember { FocusRequester() }

    BackHandler(newAlbumMode) { newAlbumMode = false }

    LaunchedEffect(newAlbumMode) {
        if (newAlbumMode) {
            focus.requestFocus()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to album") },
        text = {
            if (newAlbumMode) {
                OutlinedTextField(
                    state = newAlbumName,
                    label = { Text("Album Name") },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    onKeyboardAction = {
                        if (newAlbumName.text.isNotBlank()) {
                            onCreateAlbum(newAlbumName.text.toString())
                            onDismiss()
                        }
                    },
                    modifier = Modifier.focusRequester(focus)
                )
            } else {
                AlbumListView(
                    albums = albums,
                    onAlbumClick = { album ->
                        onPickAlbum(album)
                        onDismiss()
                    },
                    onNewAlbumClick = { newAlbumMode = true },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            if (newAlbumMode) {
                TextButton(
                    enabled = newAlbumName.text.isNotBlank(),
                    onClick = {
                        onCreateAlbum(newAlbumName.text.toString())
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
