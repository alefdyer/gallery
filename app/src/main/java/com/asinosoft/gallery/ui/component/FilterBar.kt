package com.asinosoft.gallery.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.asinosoft.gallery.data.Filter

@Composable
fun FilterBar(
    visible: Boolean,
    filters: List<Filter>,
    onToggleFilter: (Filter) -> Unit,
    modifier: Modifier = Modifier,
    onMeasuredHeight: (Float) -> Unit = {}
) {
    val lazyListState = rememberLazyListState()

    LaunchedEffect(filters) {
        if (filters.isNotEmpty()) {
            lazyListState.animateScrollToItem(0)
        }
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier.onGloballyPositioned {
            onMeasuredHeight(it.size.height.toFloat())
        }
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (filters.isNotEmpty()) {
                    LazyRow(
                        state = lazyListState,
                        modifier = Modifier.widthIn(max = 285.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        items(filters, key = { it.application.pkg }) { filter ->
                            filter.application.icon?.let { icon ->
                                Image(
                                    bitmap = icon.toBitmap().asImageBitmap(),
                                    contentDescription = filter.application.name,
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .size(32.dp)
                                        .alpha(if (filter.enabled) 1f else 0.3f)
                                        .clickable { onToggleFilter(filter) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
