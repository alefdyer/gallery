package com.asinosoft.gallery.ui.component

import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.asinosoft.gallery.data.Media
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Carousel(
    items: List<Media>,
    pagerState: PagerState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    HorizontalPager(
        state = pagerState,
        pageSize = PageSize.Fixed(40.dp),
        pageSpacing = 4.dp,
        contentPadding = PaddingValues(8.dp),
        modifier = modifier,
        snapPosition = CarouselSnapPosition
    ) { page ->
        val media = items[page]

        Surface(shape = RoundedCornerShape(4.dp)) {
            MediaThumbnail(
                media = media,
                aspectRatio = if (page == pagerState.currentPage) 0.4f else 0.5f,
                onClick = { scope.launch { pagerState.scrollToPage(page) } },
            )
        }
    }
}

private object CarouselSnapPosition : SnapPosition {
    override fun position(
        layoutSize: Int,
        itemSize: Int,
        beforeContentPadding: Int,
        afterContentPadding: Int,
        itemIndex: Int,
        itemCount: Int
    ): Int {
        val availableLayoutSpace = layoutSize - beforeContentPadding - afterContentPadding
        val center = availableLayoutSpace / 2 - itemSize / 2

        if (itemIndex !in 0..<itemCount) {
            return center
        }

        val start = itemIndex * itemSize / 3
        val end = availableLayoutSpace - itemSize - (itemCount - itemIndex - 1) * itemSize / 3

        return center.coerceAtLeast(end).coerceAtMost(start)
    }
}
