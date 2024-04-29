package com.asinosoft.gallery.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.sp
import com.asinosoft.gallery.data.Image
import com.asinosoft.gallery.util.groupByDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ImageListView(
    images: List<Image>,
    onImageClick: (Image) -> Unit,
) {
    val groups by remember(images) { mutableStateOf(groupByDate(images)) }
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    LazyVerticalGrid(columns = GridCells.Fixed(1)) {
        items(groups) { group ->
            Column {
                Text(
                    text = group.date.format(dateFormat),
                    fontSize = 24.sp
                )

                GroupView(
                    group = group,
                    onImageClick = onImageClick
                )
            }
        }
    }
}
