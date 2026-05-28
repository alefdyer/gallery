package com.asinosoft.gallery.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.asinosoft.gallery.R

@Composable
fun MediaPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier, Alignment.Center) {
        Image(
            painterResource(R.drawable.photo),
            contentDescription = null,
            modifier.aspectRatio(1f)
        )
    }
}
