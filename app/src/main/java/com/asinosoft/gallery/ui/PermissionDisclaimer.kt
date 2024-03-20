package com.asinosoft.gallery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.asinosoft.gallery.R
import com.asinosoft.gallery.ui.theme.Typography
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.shouldShowRationale

@Composable
@OptIn(ExperimentalPermissionsApi::class)
fun PermissionDisclaimer(permission: PermissionState) {
    Column(
        modifier = Modifier.fillMaxSize(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val text =
            if (permission.status.shouldShowRationale) R.string.permission_disclaimer
            else R.string.permission_denied

        Text(
            text = stringResource(id = text),
            style = Typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(16.dp, 16.dp)
                .width(200.dp),
        )
        Button(onClick = { permission.launchPermissionRequest() }) {
            Text(text = stringResource(id = R.string.grant))
        }
    }
}

@Preview(showSystemUi = true)
@Composable
@OptIn(ExperimentalPermissionsApi::class)
fun Preview() {
    val state = remember {
        object : PermissionState {
            override val permission = ""
            override val status = PermissionStatus.Denied(true)
            override fun launchPermissionRequest() {}
        }
    }

    PermissionDisclaimer(state)
}
