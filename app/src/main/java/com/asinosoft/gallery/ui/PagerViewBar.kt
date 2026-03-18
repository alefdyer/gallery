package com.asinosoft.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.asinosoft.gallery.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PagerViewBar(
    onBack: () -> Unit,
    onShowInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        title = { },
        modifier =
            modifier.background(
                Brush.verticalGradient(listOf(Color.Transparent.copy(0.5f), Color.Transparent)),
            ),
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                actionIconContentColor = Color.White,
                navigationIconContentColor = Color.White,
            ),
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        },
        actions = {
            MenuButton(onShowInfo)
        },
    )
}

@Composable
fun MenuButton(
    onShowInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }

    IconButton({ showMenu = true }, modifier) {
        Icon(
            painter = painterResource(R.drawable.menu),
            contentDescription = null,
        )
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.info)) },
                onClick = {
                    onShowInfo()
                    showMenu = false
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.info),
                        contentDescription = stringResource(R.string.info),
                    )
                },
            )
        }
    }
}
