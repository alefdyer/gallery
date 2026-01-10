package com.asinosoft.gallery.ui.component

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import com.asinosoft.gallery.data.HeaderItem

@Composable
fun GroupHeader(
    header: HeaderItem
) {
    Text(
        text = header.label,
        fontSize = 24.sp
    )
}
