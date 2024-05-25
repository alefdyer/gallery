package com.asinosoft.gallery.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import coil.compose.AsyncImage
import com.asinosoft.gallery.data.Image
import com.asinosoft.gallery.data.ImageGroup
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun GroupView(
    columns: Int = 3,
    group: ImageGroup,
    onImageClick: (Image) -> Unit,
) {
    Layout(
        content = {
            group.images.forEach { image ->
                AsyncImage(
                    model = image.path,
                    contentDescription = "",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clickable { onImageClick(image) }
                )
            }
        }
    ) { measurables, constraints ->
        val size = constraints.maxWidth / columns
        val childConstraints = Constraints(size, size)

        val rows = (measurables.count() + columns - 1) / columns
        val height = rows * size

        layout(constraints.maxWidth, height) {
            measurables.map {
                it.measure(childConstraints)
            }.forEachIndexed { index, placeable ->
                val x = size * (index % columns)
                val y = size * (index / columns)

                placeable.placeRelative(x, y)
            }
        }
    }
}

@Preview
@Composable
fun PreviewGroupView() {
    val image = Image(
        "android.resource://com.asinosoft.gallery/drawable/foxy",
        LocalDate.now(),
        LocalTime.now(),
        950,
        950,
        0,
        null,
        12345,
        "ssd/DCIM/foxy.JPEG"
    )

    GroupView(
        group = ImageGroup(
            LocalDate.now(),
            listOf(image, image, image, image, image)
        )
    ) {

    }
}
