package com.asinosoft.gallery.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun VideoView(
    uri: Uri,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        Text(uri.toString())
    }
}
