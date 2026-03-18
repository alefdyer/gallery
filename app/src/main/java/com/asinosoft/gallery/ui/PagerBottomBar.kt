package com.asinosoft.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.asinosoft.gallery.R

@Composable
fun PagerBottomBar(
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onSearch: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors =
        NavigationBarItemDefaults.colors(
            unselectedIconColor = Color.White,
            unselectedTextColor = Color.White,
        )

    NavigationBar(
        containerColor = Color.Transparent,
        contentColor = Color.White,
        modifier =
            modifier.background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent.copy(0.5f))),
            ),
    ) {
        NavigationBarItem(
            selected = false,
            onClick = onShare,
            icon = { Icon(painterResource(R.drawable.share), stringResource(id = R.string.share)) },
            label = { Text(stringResource(id = R.string.share)) },
            colors = colors,
        )
        NavigationBarItem(
            selected = false,
            onClick = onEdit,
            icon = { Icon(painterResource(R.drawable.brush), stringResource(id = R.string.edit)) },
            label = { Text(stringResource(id = R.string.edit)) },
            colors = colors,
        )
        NavigationBarItem(
            selected = false,
            onClick = onSearch,
            icon = {
                Icon(painterResource(R.drawable.image_search), stringResource(id = R.string.search))
            },
            label = { Text(stringResource(id = R.string.search)) },
            colors = colors,
        )
        NavigationBarItem(
            selected = false,
            onClick = onDelete,
            icon = {
                Icon(
                    painterResource(R.drawable.delete),
                    stringResource(id = R.string.delete),
                )
            },
            label = { Text(stringResource(id = R.string.delete)) },
            colors = colors,
        )
    }
}
