package com.asinosoft.gallery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.asinosoft.gallery.ui.component.FloatingDropdownMenu
import com.asinosoft.gallery.ui.component.FloatingMenuItem
import com.asinosoft.gallery.ui.theme.floatingPanelColor

@Composable
fun PagerViewBar(
    onBack: () -> Unit,
    onShowInfo: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        FloatingCircle {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back),
                    contentDescription = stringResource(R.string.back)
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            actions()

            FloatingCircle {
                MenuButton(onShowInfo)
            }
        }
    }
}

@Composable
internal fun FloatingCircle(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = floatingPanelColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        content = content
    )
}

@Composable
fun MenuButton(onShowInfo: () -> Unit, modifier: Modifier = Modifier) {
    var showMenu by remember { mutableStateOf(false) }

    IconButton({ showMenu = true }, modifier) {
        Icon(
            painter = painterResource(R.drawable.menu),
            contentDescription = null
        )
        FloatingDropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            FloatingMenuItem(
                text = stringResource(R.string.info),
                onClick = {
                    onShowInfo()
                    showMenu = false
                }
            )
        }
    }
}
