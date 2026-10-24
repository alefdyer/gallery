package com.asinosoft.photogallery.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.asinosoft.photogallery.R
import com.asinosoft.photogallery.model.TrashViewModel

@Composable
fun TrashView(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    model: TrashViewModel = hiltViewModel()
) {
    MediaFolderView(
        title = stringResource(R.string.trash),
        emptyText = stringResource(if (model.isSupported) R.string.trash_empty else R.string.trash_unsupported),
        restoreLabel = stringResource(R.string.restore_from_trash),
        onClose = onClose,
        model = model,
        modifier = modifier
    )
}
