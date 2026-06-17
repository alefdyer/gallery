package com.asinosoft.gallery.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import com.asinosoft.gallery.R
import com.asinosoft.gallery.data.Application
import com.asinosoft.gallery.data.Filter
import com.asinosoft.gallery.ui.theme.GalleryTheme

@Composable
fun FilterDialog(
    filters: List<Filter>,
    onChangeFilter: (Filter, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card {
            Text(
                text = stringResource(R.string.filter),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(8.dp),
                style = MaterialTheme.typography.titleLarge
            )

            filters.forEach { filter ->
                Row(
                    modifier = Modifier
                        .padding(8.dp)
                        .clickable { onChangeFilter(filter, !filter.enabled) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    filter.application.icon?.let { icon ->
                        Image(
                            bitmap = icon.toBitmap().asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(32.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = filter.application.name,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = filter.enabled,
                        onCheckedChange = { onChangeFilter(filter, it) }
                    )
                }
            }

            TextButton(onClick = onDismiss, Modifier
                .align(Alignment.End)
                .padding(8.dp)) {
                Text(stringResource(R.string.close))
            }
        }
    }
}

@Preview(showSystemUi = true)
@Composable
private fun Preview() {
    GalleryTheme {
        FilterDialog(
            filters = listOf(
                Filter(Application("Alpha", "", null)),
                Filter(Application("Beta", "", null)),
                Filter(Application("Gamma", "", null)),
                Filter(Application("Delta", "", null)),
            ),
            onChangeFilter = { _, _ -> },
            onDismiss = {}
        )
    }
}
