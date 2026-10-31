package com.asinosoft.photogallery.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.asinosoft.photogallery.R

@Composable
fun PrivacyPolicyView(
    onClose: () -> Unit,
) {
    RemoteHtmlDocView(
        title = stringResource(R.string.privacy_policy),
        url = stringResource(R.string.privacy_policy_url),
        onClose = onClose,
    )
}
