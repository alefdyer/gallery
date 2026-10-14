package com.asinosoft.gallery.ui

import android.app.Activity
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.asinosoft.gallery.R
import com.asinosoft.gallery.data.Album
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.model.PagerViewModel
import com.asinosoft.gallery.ui.component.Carousel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PagerView(
    onAlbumClick: (Album) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onCurrentMediaChange: (Long) -> Unit = {},
    model: PagerViewModel = hiltViewModel()
) {
    val items by model.images.collectAsState(listOf())
    val offset by model.offset.collectAsState(0)
    val pagerState: PagerState = key(offset) { rememberPagerState(offset) { items.size } }

    LaunchedEffect(pagerState, items) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            items.getOrNull(page)?.let { onCurrentMediaChange(it.id) }
        }
    }

    var showControls by remember { mutableStateOf(true) }
    val lightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val lightBackground = lightTheme && showControls
    val backgroundColor by animateColorAsState(
        targetValue = if (lightBackground) MaterialTheme.colorScheme.background else Color.Black,
        label = "pagerBackground"
    )

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var isMuted by remember { mutableStateOf(true) }
    val currentIsVideo = null != items.getOrNull(pagerState.currentPage)?.video

    var pendingFavorite by remember { mutableStateOf<Pair<Media, Boolean>?>(null) }
    val favoriteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val (media, favorite) = pendingFavorite ?: return@rememberLauncherForActivityResult
        pendingFavorite = null
        if (Activity.RESULT_OK == result.resultCode) {
            model.setFavorite(media, favorite)
            if (model.favoritesOnly && !favorite && 1 == items.size) onClose()
        }
    }

    val actionsBar = @Composable {
        val current = items.getOrNull(pagerState.currentPage)
        PagerBottomBar(
            favorite = true == current?.favorite,
            onFavorite = {
                val media = current ?: return@PagerBottomBar
                val favorite = !media.favorite
                val request = model.favoriteRequest(media, favorite)
                if (null != request) {
                    pendingFavorite = media to favorite
                    favoriteLauncher.launch(IntentSenderRequest.Builder(request).build())
                } else if (model.favoritesOnly && !favorite && 1 == items.size) {
                    onClose()
                }
            },
            onShare = { model.share(items[pagerState.currentPage]) },
            onEdit = { model.edit(items[pagerState.currentPage]) },
            onSearch = {},
            onDelete = {
                model.delete(items[pagerState.currentPage]) {
                    if (1 == items.count()) {
                        onClose()
                    }
                }
            }
        )
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(backgroundColor)
    ) {
        var showInfo by remember { mutableStateOf(false) }
        val bottomGestureInset = WindowInsets.systemGestures.getBottom(LocalDensity.current)

        if (showInfo) {
            MediaInfoSheet(
                media = items[pagerState.currentPage],
                onAlbumClick = onAlbumClick,
                onDismissRequest = { showInfo = false }
            )
        }

        HorizontalPager(
            state = pagerState,
            pageSpacing = 16.dp,
            modifier =
                Modifier
                    .pointerInput(bottomGestureInset) {
                        var fromSystemZone = false
                        detectVerticalDragGestures(
                            onDragStart = { fromSystemZone = it.y >= size.height - bottomGestureInset }
                        ) { _, amount ->
                            if (fromSystemZone) return@detectVerticalDragGestures
                            if (amount > 10 && !showInfo) onClose()
                            if (amount < -10) showInfo = true
                        }
                    }
        ) { n ->
            val item = items[n]
            if (null !== item.image) {
                ImageView(item) {
                    if (!pagerState.isScrollInProgress) {
                        showControls = !showControls
                    }
                }
            } else if (null != item.video) {
                VideoView(
                    media = item,
                    isActive = n == pagerState.currentPage,
                    controlsVisible = showControls,
                    controlsBottomPadding = if (isLandscape) 96.dp else 160.dp,
                    muted = isMuted,
                    onTap = {
                        if (!pagerState.isScrollInProgress) {
                            showControls = !showControls
                        }
                    }
                )
            } else {
                DummyView()
            }
        }

        AnimatedVisibility(
            visible = showControls,
            enter = slideInVertically(tween(easing = LinearEasing)),
            exit = slideOutVertically(tween(easing = LinearEasing))
        ) {
            PagerViewBar(
                onBack = onClose,
                onShowInfo = { showInfo = true },
                actions = { if (isLandscape) actionsBar() }
            )
        }

        AnimatedVisibility(
            visible = showControls && currentIsVideo,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 64.dp, end = 12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.45f),
                contentColor = Color.White
            ) {
                IconButton(onClick = { isMuted = !isMuted }) {
                    Icon(
                        painter = painterResource(if (isMuted) R.drawable.volume_off else R.drawable.volume_up),
                        contentDescription = if (isMuted) "Unmute" else "Mute"
                    )
                }
            }
        }

        AnimatedVisibility(
            modifier = Modifier
                .height(64.dp)
                .align(Alignment.BottomCenter)
                .offset(y = if (isLandscape) (-16).dp else (-85).dp),
            visible = showControls,
            enter = slideInVertically(tween(easing = LinearEasing)) { it / 2 },
            exit = slideOutVertically(tween(easing = LinearEasing)) { it / 2 }
        ) {
            Carousel(
                items = items,
                pagerState = pagerState,
            )
        }

        AnimatedVisibility(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-8).dp)
                .padding(16.dp),
            visible = showControls && !isLandscape,
            enter = slideInVertically(tween(easing = LinearEasing)) { it / 2 },
            exit = slideOutVertically(tween(easing = LinearEasing)) { it / 2 }
        ) {
            actionsBar()
        }

        BackHandler(onBack = onClose)
    }
}
