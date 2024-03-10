package com.asinosoft.gallery.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.asinosoft.gallery.data.Image
import com.asinosoft.gallery.data.ImageGroup
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun GroupView(
    groups: List<ImageGroup>,
    onClick: (Image) -> Unit
) {
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    LazyVerticalGrid(columns = GridCells.Fixed(3)) {
        groups.forEach { group ->
            item(span = { GridItemSpan(3) }) {
                Text(
                    text = group.date.format(dateFormat),
                    fontSize = 24.sp
                )
            }

            items(group.images) { image ->
                AsyncImage(
                    model = image.path,
                    contentDescription = "",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clickable { onClick(image) }
                )
            }
        }
    }
}
