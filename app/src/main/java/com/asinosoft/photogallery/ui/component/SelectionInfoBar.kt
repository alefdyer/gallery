package com.asinosoft.photogallery.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.asinosoft.photogallery.R
import com.asinosoft.photogallery.ui.theme.GalleryTheme
import com.asinosoft.photogallery.ui.theme.floatingPanelColor

@Composable
fun SelectionInfoBar(
    modifier: Modifier = Modifier,
    selection: Set<Long> = setOf(),
    onCancel: () -> Unit = {},
    onSelectAll: (() -> Unit)? = null,
) {
    Surface(modifier, RoundedCornerShape(50), color = floatingPanelColor()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onCancel) {
                Icon(
                    painter = painterResource(R.drawable.close),
                    contentDescription = null
                )
            }

            Text(
                text = "${selection.size}",
                modifier = Modifier.padding(end = if (onSelectAll != null) 4.dp else 16.dp)
            )

            if (onSelectAll != null) {
                TextButton(
                    onClick = onSelectAll,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(stringResource(R.string.select_all))
                }
            }
        }
    }
}

@Preview
@Composable
private fun PreviewSelectionInfoBar() {
    GalleryTheme {
        SelectionInfoBar(
            selection = (1L..23L).toSet(),
            onCancel = {},
            onSelectAll = {},
        )
    }
}
