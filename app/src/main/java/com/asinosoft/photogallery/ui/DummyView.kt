package com.asinosoft.photogallery.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.asinosoft.photogallery.R

@Composable
fun DummyView(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        Icon(
            painterResource(R.drawable.ic_launcher_background),
            contentDescription = stringResource(R.string.video_player)
        )
    }
}
