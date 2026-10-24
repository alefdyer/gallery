package com.asinosoft.photogallery.ui.component

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import com.asinosoft.photogallery.R

@Composable
fun NewAlbumCategoryDialog(
    onCreateCategory: (String) -> Unit,
    onDismiss: () -> Unit,
    title: String = stringResource(R.string.new_category),
    initialName: String = "",
    confirmText: String = stringResource(R.string.add),
) {
    val newCategoryName = rememberTextFieldState(initialName)
    val focus = remember { FocusRequester() }
    val canSubmit = newCategoryName.text.isNotBlank()

    val submit = {
        if (canSubmit) {
            onCreateCategory(newCategoryName.text.toString())
            onDismiss()
        }
    }

    LaunchedEffect(Unit) {
        focus.requestFocus()
    }

    OneUiDialog(
        title = title,
        onDismissRequest = onDismiss,
        dismissText = stringResource(R.string.cancel),
        confirmText = confirmText,
        confirmEnabled = canSubmit,
        onConfirm = submit
    ) {
        OneUiTextField(
            state = newCategoryName,
            label = stringResource(R.string.name),
            onKeyboardAction = { submit() },
            modifier = Modifier.focusRequester(focus)
        )
    }
}
