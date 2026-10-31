package com.asinosoft.photogallery.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.asinosoft.photogallery.R

@Composable
fun TermsOfUseView(
    onClose: () -> Unit,
) {
    RemoteHtmlDocView(
        title = stringResource(R.string.storage_terms_of_use),
        url = stringResource(R.string.storage_terms_of_use_url),
        onClose = onClose,
    )
}
