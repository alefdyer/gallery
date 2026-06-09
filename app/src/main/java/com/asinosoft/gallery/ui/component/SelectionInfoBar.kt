package com.asinosoft.gallery.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.asinosoft.gallery.R
import com.asinosoft.gallery.ui.theme.GalleryTheme

@Composable
fun SelectionInfoBar(
    modifier: Modifier = Modifier,
    selection: Set<Long> = setOf(),
    onCancel: () -> Unit = {},
) {
    Surface(modifier, RoundedCornerShape(50)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onCancel) {
                Icon(
                    painter = painterResource(R.drawable.close),
                    contentDescription = null
                )
            }

            Text(
                text = "${selection.size}",
                modifier = Modifier.padding(end = 16.dp)
            )
        }
    }
}

@Preview
@Composable
private fun PreviewSelectionInfoBar() {
    GalleryTheme {
        SelectionInfoBar(selection = (1L..23L).toSet()) { }
    }
}
