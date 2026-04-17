package com.asinosoft.gallery.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.asinosoft.gallery.data.HeaderItem

@Composable
fun GroupHeader(
    header: HeaderItem,
    modifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    allSelected: Boolean = false,
    onSelectGroup: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = selectionMode, onClick = onSelectGroup)
    ) {
        Text(
            text = header.label,
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.align(Alignment.CenterStart)
        )

        if (selectionMode) {
            Checkbox(
                checked = allSelected,
                onCheckedChange = { onSelectGroup() },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(32.dp)
                    .padding(end = 8.dp)
            )
        }
    }
}
