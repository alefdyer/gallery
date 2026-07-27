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
import kotlinx.coroutines.flow.distinctUntilChanged
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
        // Sync active page when carousel settles
        launch {
            snapshotFlow { carouselState.currentPage }
                .distinctUntilChanged()
                .collect { page ->
                    if (carouselState.isScrollInProgress && !pagerState.isScrollInProgress) {
                        if (pagerState.currentPage != page) {
                            pagerState.scrollToPage(page)
                        }
                    }
                }
        }

        launch {
            snapshotFlow { pagerState.currentPage }
                .distinctUntilChanged()
                .collect { page ->
                    if (pagerState.isScrollInProgress && !carouselState.isScrollInProgress) {
                        if (carouselState.currentPage != page) {
                            carouselState.scrollToPage(page)
                        }
                    }
                }
        }

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

    // Ensure settling position is fully synchronized when scroll stops
    LaunchedEffect(carouselState.isScrollInProgress) {
        if (!carouselState.isScrollInProgress) {
            if (pagerState.currentPage != carouselState.currentPage) {
                pagerState.animateScrollToPage(carouselState.currentPage)
            }
        }
    }

    LaunchedEffect(pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress) {
            if (carouselState.currentPage != pagerState.currentPage) {
                carouselState.animateScrollToPage(pagerState.currentPage)
            }
        }
    }

    HorizontalPager(
        state = carouselState,
        pageSize = PageSize.Fixed(28.dp),
        pageSpacing = 6.dp,
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier,
        snapPosition = SnapPosition.Center
    ) { page ->
        val media = items[page]
        val isSelected = page == carouselState.currentPage

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
