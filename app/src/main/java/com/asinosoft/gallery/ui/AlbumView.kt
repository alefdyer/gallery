package com.asinosoft.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.asinosoft.gallery.R
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.model.ImageListViewModel

@Composable
fun AlbumView(
    onMediaClick: (Media) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier.fillMaxSize(),
        topBar = { TopBar(onClose) }
    ) {
        ImageListView(
            onMediaClick,
            onClose,
            Modifier.padding(it)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    onBack: () -> Unit,
    model: ImageListViewModel = hiltViewModel()
) {
    val album by model.album.collectAsState()

    TopAppBar(
        title = {
            Text(
                "${album?.name}",
                style = MaterialTheme.typography.titleLarge,
            )
        },
        modifier =
            Modifier.background(
                Brush.verticalGradient(listOf(Color.Transparent.copy(0.5f), Color.Transparent))
            ),
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                actionIconContentColor = Color.White,
                navigationIconContentColor = Color.White
            ),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back),
                    contentDescription = null
                )
            }
        }
    )
}
