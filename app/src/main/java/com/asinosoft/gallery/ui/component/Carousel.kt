package com.asinosoft.gallery.ui.component

import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.asinosoft.gallery.data.Media
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Carousel(
    items: List<Media>,
    pagerState: PagerState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val carouselState: PagerState = key(items, pagerState) { rememberPagerState(pagerState.currentPage) { items.size } }

    // Synchronize pagers' state during scroll and settle
    LaunchedEffect(pagerState, carouselState) {
        snapshotFlow {
            val (scrollingState, followingState) = if (pagerState.isScrollInProgress) {
                pagerState to carouselState
            } else if (carouselState.isScrollInProgress) {
                carouselState to pagerState
            } else {
                return@snapshotFlow null
            }

            Triple(
                followingState,
                scrollingState.currentPage,
                scrollingState.currentPageOffsetFraction
            )
        }
            .filterNotNull()
            .collect { (followingState, currentPage, currentPageOffsetFraction) ->
                followingState.scrollToPage(
                    page = currentPage,
                    pageOffsetFraction = currentPageOffsetFraction
                )
            }
    }

    HorizontalPager(
        state = carouselState,
        pageSize = PageSize.Fixed(28.dp),
        pageSpacing = 6.dp,
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier,
        snapPosition = CarouselSnapPosition
    ) { page ->
        val media = items[page]
        val isSelected = page == pagerState.currentPage

        val scale = if (isSelected) 1.25f else 1.0f

        Surface(
            shape = RoundedCornerShape(2.dp),
            color = Color.Transparent,
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
        ) {
            MediaThumbnail(
                media = media,
                modifier = Modifier.fillMaxWidth(),
                aspectRatio = 0.75f,
                onClick = { scope.launch { pagerState.animateScrollToPage(page) } },
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
