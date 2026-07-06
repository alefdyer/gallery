package com.asinosoft.gallery.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.asinosoft.gallery.R
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.model.ImageListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumView(
    onMediaClick: (Media) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    model: ImageListViewModel = hiltViewModel()
) {
    val album by model.album.collectAsState()
    val topScroll = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val density = LocalDensity.current
    val topBarPadding = 8.dp

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(topScroll.nestedScrollConnection),
    ) { paddingValues ->
        Box(Modifier.fillMaxSize()) {
            ImageListView(
                onMediaClick,
                onClose,
                scrollBehavior = topScroll,
                contentPadding = PaddingValues(
                    top = 72.dp + paddingValues.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding()
                )
            )

            AnimatedVisibility(
                visible = true,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = topBarPadding + paddingValues.calculateTopPadding())
                    .onGloballyPositioned {
                        topScroll.state.heightOffsetLimit =
                            -it.size.height.toFloat() - with(density) { (topBarPadding + paddingValues.calculateTopPadding()).toPx() }
                    }
                    .offset { IntOffset(0, topScroll.state.heightOffset.toInt()) }
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    tonalElevation = 4.dp
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        IconButton(onClick = onClose) {
                            Icon(
                                painter = painterResource(R.drawable.arrow_back),
                                contentDescription = null
                            )
                        }

                        Text(
                            "${album?.name}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }
    }
}
