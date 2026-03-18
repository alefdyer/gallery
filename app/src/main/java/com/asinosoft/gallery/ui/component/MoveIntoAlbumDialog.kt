package com.asinosoft.gallery.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.asinosoft.gallery.model.GalleryViewModel
import com.asinosoft.gallery.ui.AlbumListView

@Composable
fun MoveIntoAlbumDialog(
    model: GalleryViewModel = hiltViewModel(),
    onAlbumNameSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val albums by model.albums.collectAsState(listOf())
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
        title = { Text("Move to album") },
        text = {
            if (newAlbumMode) {
                OutlinedTextField(
                    state = newAlbumName,
                    label = { Text("Album Name") },
                    modifier = Modifier.focusRequester(focus),
                )
            } else {
                AlbumListView(
                    albums = albums,
                    onAlbumClick = { album ->
                        onAlbumNameSelect(album.name)
                        onDismiss()
                    },
                    onNewAlbumClick = { newAlbumMode = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            if (newAlbumMode) {
                TextButton(
                    enabled = newAlbumName.text.isNotEmpty(),
                    onClick = {
                        onAlbumNameSelect(newAlbumName.text.toString())
                    },
                ) {
                    Text("Move")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
