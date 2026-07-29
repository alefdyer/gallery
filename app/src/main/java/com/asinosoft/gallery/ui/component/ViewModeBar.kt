package com.asinosoft.gallery.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.asinosoft.gallery.R

@Composable
fun BoxScope.ViewModeBar(
    visible: Boolean,
    pagerState: PagerState,
    onPhotos: () -> Unit,
    onAlbums: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        Surface(
            modifier = modifier.padding(16.dp),
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surface.copy(alpha = .95f),
        ) {
            val size = Modifier.width(80.dp)
            Row(Modifier.padding(2.dp), verticalAlignment = Alignment.CenterVertically) {
                EllipseButton(
                    onClick = onPhotos,
                    selected = 0 == pagerState.currentPage,
                    icon = painterResource(R.drawable.photo),
                    label = stringResource(R.string.photos),
                    modifier = size
                )

                EllipseButton(
                    onClick = onAlbums,
                    selected = 1 == pagerState.currentPage,
                    icon = painterResource(R.drawable.album),
                    label = stringResource(R.string.albums),
                    modifier = size
                )

                Box {
                    EllipseButton(
                        onClick = { menuExpanded = true },
                        selected = false,
                        icon = painterResource(R.drawable.menu),
                        label = stringResource(R.string.menu),
                        modifier = size
                    )

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings)) },
                            onClick = {
                                menuExpanded = false
                                onSettings()
                            },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.settings),
                                    contentDescription = null
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}