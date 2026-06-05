package com.asinosoft.gallery.ui.component

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.asinosoft.gallery.R

@Composable
fun NewAlbumCategoryDialog(
    onCreateCategory: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val newCategoryName = rememberTextFieldState()
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focus.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_category)) },
        text = {
            OutlinedTextField(
                state = newCategoryName,
                label = { Text(stringResource(R.string.name)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                onKeyboardAction = {
                    if (newCategoryName.text.isNotBlank()) {
                        onCreateCategory(newCategoryName.text.toString())
                        onDismiss()
                    }
                },
                modifier = Modifier.focusRequester(focus)
            )
        },
        confirmButton = {
            TextButton(
                enabled = newCategoryName.text.isNotBlank(),
                onClick = {
                    onCreateCategory(newCategoryName.text.toString())
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
