package com.asinosoft.gallery.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.asinosoft.gallery.data.Image
import com.asinosoft.gallery.data.ImageGroup
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroupView(
    group: ImageGroup,
    columns: Int = 3,
    selectedImages: Set<Image> = setOf(),
    selectionMode: Boolean = false,
    onImageClick: (Image) -> Unit = {},
    onImageSelect: (Image) -> Unit = {},
) {
    Column {
        Text(
            text = group.label,
            fontSize = 24.sp
        )

        Layout(
            content = {
                group.images.forEach { image ->
                    Box(contentAlignment = Alignment.BottomEnd) {
                        AsyncImage(
                            model = image.path,
                            contentDescription = "",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .combinedClickable(
                                    onClick = {
                                        if (selectionMode) {
                                            onImageSelect(image)
                                        } else {
                                            onImageClick(image)
                                        }
                                    },
                                    onLongClick = { onImageSelect(image) }
                                )
                        )

                        if (selectionMode) {
                            Checkbox(
                                checked = selectedImages.contains(image),
                                onCheckedChange = { onImageSelect(image) })
                        }
                    }
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
            listOf(image, image, image, image, image),
            "Another Group"
        ),
        selectionMode = true,
        selectedImages = setOf(image)
    ) {

    }
}
