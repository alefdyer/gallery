package com.asinosoft.photogallery.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
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
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.asinosoft.photogallery.R
import com.asinosoft.photogallery.data.Album
import com.asinosoft.photogallery.data.AlbumCategory
import com.asinosoft.photogallery.data.name
import com.asinosoft.photogallery.model.ImageListViewModel

@Composable
fun AddToAlbumDialog(
    onPickAlbum: (Album) -> Unit,
    onCreateAlbum: (String, AlbumCategory) -> Unit,
    onCreateAlbumInNewCategory: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var mode by remember { mutableStateOf(AddToAlbumMode.SELECT) }
    val newAlbumName = rememberTextFieldState()
    val newAlbumCategory = remember { mutableStateOf(AlbumCategory.OTHER) }
    val newCategoryName = rememberTextFieldState()

    val canSubmit = when (mode) {
        AddToAlbumMode.SELECT -> false
        AddToAlbumMode.NEW_ALBUM -> newAlbumName.text.isNotBlank()
        AddToAlbumMode.NEW_CATEGORY ->
            newAlbumName.text.isNotBlank() && newCategoryName.text.isNotBlank()
    }

    val submit = {
        when (mode) {
            AddToAlbumMode.SELECT -> Unit
            AddToAlbumMode.NEW_ALBUM ->
                onCreateAlbum(newAlbumName.text.toString(), newAlbumCategory.value)

            AddToAlbumMode.NEW_CATEGORY ->
                onCreateAlbumInNewCategory(
                    newAlbumName.text.toString(),
                    newCategoryName.text.toString()
                )
        }
        onDismiss()
    }

    BackHandler(mode != AddToAlbumMode.SELECT) { mode = AddToAlbumMode.SELECT }

    val maxContentHeight = LocalWindowInfo.current.containerDpSize.height * 0.6f

    OneUiDialog(
        title = stringResource(
            when (mode) {
                AddToAlbumMode.SELECT -> R.string.add_to_album
                AddToAlbumMode.NEW_ALBUM -> R.string.new_album
                AddToAlbumMode.NEW_CATEGORY -> R.string.new_category
            }
        ),
        onDismissRequest = onDismiss,
        dismissText = stringResource(R.string.cancel),
        confirmText = if (mode != AddToAlbumMode.SELECT) stringResource(R.string.add) else null,
        confirmEnabled = canSubmit,
        onConfirm = { if (canSubmit) submit() }
    ) {
        when (mode) {
            AddToAlbumMode.SELECT -> AlbumSelector(
                onAlbumClick = { album ->
                    onPickAlbum(album)
                    onDismiss()
                },
                onNewAlbumClick = {
                    newAlbumCategory.value = it
                    mode = AddToAlbumMode.NEW_ALBUM
                },
                onNewCategoryClick = { mode = AddToAlbumMode.NEW_CATEGORY },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxContentHeight)
            )

            AddToAlbumMode.NEW_ALBUM -> NewAlbumDialog(
                newAlbumName,
                newAlbumCategory,
                onSubmit = { if (canSubmit) submit() },
            )

            AddToAlbumMode.NEW_CATEGORY -> NewCategoryAlbumDialog(
                newAlbumName,
                newCategoryName,
                onSubmit = { if (canSubmit) submit() },
            )
        }
    }
}

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
        OneUiTextField(
            state = newAlbumName,
            label = stringResource(R.string.name),
            onKeyboardAction = { onSubmit() },
            modifier = Modifier.focusRequester(focus)
        )

        Spacer(Modifier.height(12.dp))

        OneUiSelectField(
            label = stringResource(R.string.category),
            value = newAlbumCategory.value.name(),
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            categories.forEach { category ->
                FloatingMenuItem(
                    text = category.name(),
                    onClick = {
                        newAlbumCategory.value = category
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun NewCategoryAlbumDialog(
    newAlbumName: TextFieldState,
    newCategoryName: TextFieldState,
    onSubmit: () -> Unit,
) {
    val albumFocus = remember { FocusRequester() }
    val categoryFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        albumFocus.requestFocus()
    }

    Column {
        OneUiTextField(
            state = newAlbumName,
            label = stringResource(R.string.name),
            imeAction = ImeAction.Next,
            onKeyboardAction = { categoryFocus.requestFocus() },
            modifier = Modifier.focusRequester(albumFocus)
        )

        Spacer(Modifier.height(12.dp))

        OneUiTextField(
            state = newCategoryName,
            label = stringResource(R.string.category),
            onKeyboardAction = { onSubmit() },
            modifier = Modifier.focusRequester(categoryFocus)
        )
    }
}

private enum class AddToAlbumMode { SELECT, NEW_ALBUM, NEW_CATEGORY }
