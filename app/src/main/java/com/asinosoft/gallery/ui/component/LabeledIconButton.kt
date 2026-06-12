package com.asinosoft.gallery.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.asinosoft.gallery.R
import com.asinosoft.gallery.ui.theme.GalleryTheme

@Composable
fun LabeledIconButton(
    label: String,
    icon: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(),
        contentPadding = ButtonDefaults.TextButtonWithIconContentPadding
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = icon,
                contentDescription = label
            )
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Preview
@Composable
private fun Preview() {
    GalleryTheme {
        LabeledIconButton(
            label = "Up and down",
            icon = painterResource(R.drawable.add_tag),
            onClick = {}
        )
    }
}
