package com.asinosoft.photogallery.ui

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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.asinosoft.photogallery.R
import com.asinosoft.photogallery.data.Album
import com.asinosoft.photogallery.data.Media
import com.asinosoft.photogallery.model.PagerViewModel
import com.asinosoft.photogallery.ui.component.ActiveSharedMedia
import com.asinosoft.photogallery.ui.component.AddToAlbumDialog
import com.asinosoft.photogallery.ui.component.Carousel
import com.asinosoft.photogallery.ui.component.DismissDragState
import com.asinosoft.photogallery.ui.component.LocalDismissDrag
import com.asinosoft.photogallery.ui.component.MoveToPrivateDialog
import com.asinosoft.photogallery.ui.component.dragToDismiss
import com.asinosoft.photogallery.ui.component.isOpeningTransition
import com.asinosoft.photogallery.ui.component.rememberAutoplayVideo
import com.asinosoft.photogallery.ui.component.rememberTrashIntro
import com.asinosoft.photogallery.ui.component.rememberVideoSound

/** Нажатое фото: просмотр показывает его сразу, пока из базы грузится весь список */
internal object OpenedMedia {
    var media: Media? = null
        set(value) {
            field = value
            ActiveSharedMedia.id = value?.id
        }
}

@Composable
fun PagerView(
    onAlbumClick: (Album) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onCurrentMediaChange: (Long) -> Unit = {},
    model: PagerViewModel = hiltViewModel()
) {
    val loadedItems by model.images.collectAsState(listOf())
    val offset by model.offset.collectAsState(0)
    val params by model.params.collectAsState()

    val opening = remember(params) { OpenedMedia.media?.takeIf { it.id == params.imageId } }
    var listReady by remember { mutableStateOf(null == opening) }
    val openingTransition = isOpeningTransition()
    LaunchedEffect(loadedItems, offset, openingTransition) {
        if (openingTransition || loadedItems.isEmpty()) return@LaunchedEffect
        val openingId = opening?.id
        if (loadedItems.getOrNull(offset)?.id == openingId || loadedItems.none { it.id == openingId }) listReady =
            true
    }
    val items = if (listReady || null == opening) loadedItems else listOf(opening)
    val pagerState: PagerState = key(items, offset) {
        rememberPagerState(if (listReady) offset else 0) { items.size }
    }

    LaunchedEffect(pagerState, items) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            items.getOrNull(page)?.let { onCurrentMediaChange(it.id) }
        }
    }

    val dismissDrag = remember { DismissDragState() }
    var showControls by remember { mutableStateOf(true) }
    val dragging by remember { derivedStateOf { dismissDrag.isDragging } }
    val controlsVisible = showControls && !dragging && !openingTransition
    val lightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val lightBackground = lightTheme && showControls
    val backgroundColor by animateColorAsState(
        targetValue = if (lightBackground) MaterialTheme.colorScheme.background else Color.Black,
        label = "pagerBackground"
    )

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val videoSound = rememberVideoSound()
    val autoplayVideo = rememberAutoplayVideo()
    var isMuted by remember(videoSound) { mutableStateOf(!videoSound) }
    val currentIsVideo = null != items.getOrNull(pagerState.currentPage)?.video

    var showAddToAlbum by remember { mutableStateOf(false) }
    var pendingFavorite by remember { mutableStateOf<Pair<Media, Boolean>?>(null) }
    val favoriteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val (media, favorite) = pendingFavorite ?: return@rememberLauncherForActivityResult
        pendingFavorite = null
        if (Activity.RESULT_OK == result.resultCode) {
            model.setFavorite(media, favorite)
            if (model.params.value.favoritesOnly && !favorite) onClose()
        }
    }

    val deleteCurrent = rememberTrashIntro {
        items.getOrNull(pagerState.currentPage)?.let { media ->
            model.delete(media) {
                if (1 == items.count()) {
                    onClose()
                }
            }
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
                } else if (model.params.value.favoritesOnly && !favorite) {
                    onClose()
                }
            },
            onAddToAlbum = { showAddToAlbum = true },
            onShare = { model.share(items[pagerState.currentPage]) },
            onEdit = { model.edit(items[pagerState.currentPage]) },
            onDelete = deleteCurrent
        )
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .drawBehind { drawRect(backgroundColor.copy(alpha = 1f - dismissDrag.progress)) }
    ) {
        var showInfo by remember { mutableStateOf(false) }
        var confirmMoveToPrivate by remember { mutableStateOf<Media?>(null) }
        confirmMoveToPrivate?.let { media ->
            MoveToPrivateDialog(
                onConfirm = {
                    confirmMoveToPrivate = null
                    model.moveToPrivate(media) {
                        if (1 == items.size) onClose()
                    }
                },
                onDismiss = { confirmMoveToPrivate = null }
            )
        }
        val density = LocalDensity.current
        val layoutDirection = LocalLayoutDirection.current
        val bottomGestureInset = WindowInsets.systemGestures.getBottom(density)
        val leftGestureInset = WindowInsets.systemGestures.getLeft(density, layoutDirection)
        val rightGestureInset = WindowInsets.systemGestures.getRight(density, layoutDirection)

        if (showInfo) {
            items.getOrNull(pagerState.currentPage)?.let {media ->
                MediaInfoSheet(
                    media = media,
                    model = model,
                    onAlbumClick = onAlbumClick,
                    onDismissRequest = { showInfo = false }
                )
            }
        }

        if (showAddToAlbum) {
            items.getOrNull(pagerState.currentPage)?.let { media ->
                AddToAlbumDialog(
                    onPickAlbum = { album -> model.addToAlbum(media.id, album.id) },
                    onCreateAlbum = { name, category ->
                        model.addToNewAlbum(media.id, name, category)
                    },
                    onCreateAlbumInNewCategory = { name, categoryName ->
                        model.addToNewAlbumInNewCategory(media.id, name, categoryName)
                    },
                    onDismiss = { showAddToAlbum = false }
                )
            }
        }

        val pageContent: @Composable (Media, Boolean) -> Unit = { item, active ->
            if (null !== item.image) {
                ImageView(item) {
                    if (!pagerState.isScrollInProgress) {
                        showControls = !showControls
                    }
                }
            } else if (null != item.video) {
                VideoView(
                    media = item,
                    isActive = active,
                    controlsVisible = controlsVisible,
                    controlsBottomPadding = if (isLandscape) 96.dp else 160.dp,
                    muted = isMuted,
                    autoplay = autoplayVideo,
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

        CompositionLocalProvider(LocalDismissDrag provides dismissDrag) {
            HorizontalPager(
                state = pagerState,
                pageSpacing = 16.dp,
                key = { items.getOrNull(it)?.id ?: -1 },
                modifier =
                    Modifier
                        .pointerInput(leftGestureInset, rightGestureInset) {
                            awaitEachGesture {
                                val down = awaitFirstDown(
                                    requireUnconsumed = false,
                                    pass = PointerEventPass.Initial
                                )
                                val x = down.position.x
                                if (x >= leftGestureInset && x <= size.width - rightGestureInset) return@awaitEachGesture
                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                } while (event.changes.any { it.pressed })
                            }
                        }
                        .dragToDismiss(
                            state = dismissDrag,
                            onDismiss = onClose,
                            onSwipeUp = { showInfo = true },
                            ignoreBottom = bottomGestureInset
                        )
            ) { n ->
                pageContent(items[n], n == pagerState.currentPage)
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = slideInVertically(tween(easing = LinearEasing)),
            exit = slideOutVertically(tween(easing = LinearEasing))
        ) {
            PagerViewBar(
                onBack = onClose,
                onShowInfo = { showInfo = true },
                onAddToAlbum = { showAddToAlbum = true },
                onMoveToPrivate = { confirmMoveToPrivate = items.getOrNull(pagerState.currentPage) },
                actions = { if (isLandscape) actionsBar() }
            )
        }

        AnimatedVisibility(
            visible = controlsVisible && currentIsVideo,
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
                        contentDescription = stringResource(
                            if (isMuted) R.string.unmute else R.string.mute
                        )
                    )
                }
            }
        }

        AnimatedVisibility(
            modifier = Modifier
                .height(64.dp)
                .align(Alignment.BottomCenter)
                .offset(y = if (isLandscape) (-16).dp else (-85).dp),
            visible = controlsVisible,
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
            visible = controlsVisible && !isLandscape,
            enter = slideInVertically(tween(easing = LinearEasing)) { it / 2 },
            exit = slideOutVertically(tween(easing = LinearEasing)) { it / 2 }
        ) {
            actionsBar()
        }

        BackHandler(onBack = onClose)
    }
}
