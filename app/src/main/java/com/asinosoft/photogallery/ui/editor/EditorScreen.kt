package com.asinosoft.photogallery.ui.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.asinosoft.photogallery.R
import com.asinosoft.photogallery.data.editor.EditorExport
import com.asinosoft.photogallery.data.editor.StickerCategory
import com.asinosoft.photogallery.data.editor.StickerItem
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun EditorScreen(
    onClose: () -> Unit,
    onOpenStickerOrder: () -> Unit,
    model: EditorViewModel = hiltViewModel(),
) {
    val media by model.media.collectAsState()
    val tab by model.tab.collectAsState()
    val edit by model.edit.collectAsState()
    val canUndo by model.canUndo.collectAsState()
    val canRedo by model.canRedo.collectAsState()
    val saving by model.saving.collectAsState()
    val stickerCats by model.stickerCategories.collectAsState()
    val selectedStickerId by model.selectedStickerId.collectAsState()
    val addMode by model.addMode.collectAsState()
    val isVideo = media?.video != null
    val tabs = remember(isVideo) { EditorTab.forMedia(isVideo) }
    val context = LocalContext.current
    val videoUri = media?.uri?.takeIf { isVideo }
    val videoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }
    var videoPlaying by remember { mutableStateOf(true) }
    var videoPositionMs by remember { mutableLongStateOf(0L) }
    val trimStart = edit.trimStartMs
    val trimEnd = edit.trimEndMs ?: (media?.video?.duration ?: 0L)

    DisposableEffect(videoUri) {
        if (videoUri != null) {
            videoPlayer.setMediaItem(MediaItem.fromUri(videoUri))
            videoPlayer.prepare()
            videoPlayer.playWhenReady = true
            videoPlaying = true
        }
        onDispose {
            videoPlayer.stop()
            videoPlayer.clearMediaItems()
        }
    }
    DisposableEffect(Unit) {
        onDispose { videoPlayer.release() }
    }
    DisposableEffect(edit.muted, edit.originalVolume) {
        videoPlayer.volume = if (edit.muted) 0f else edit.originalVolume
        onDispose { }
    }
    LaunchedEffect(videoUri, trimStart, trimEnd, videoPlaying) {
        if (videoUri == null) return@LaunchedEffect
        while (isActive) {
            val pos = videoPlayer.currentPosition
            videoPositionMs = pos
            if (trimEnd > trimStart && pos >= trimEnd) {
                videoPlayer.seekTo(trimStart)
                if (!videoPlaying) videoPlayer.pause()
            }
            delay(50)
        }
    }
    LaunchedEffect(videoPlaying) {
        if (videoUri == null) return@LaunchedEffect
        videoPlayer.playWhenReady = videoPlaying
    }
    LaunchedEffect(trimStart, tab) {
        if (videoUri != null && tab == EditorTab.TRIM) {
            videoPlayer.seekTo(trimStart)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(Modifier.fillMaxSize()) {
            EditorTopBar(
                canUndo = canUndo,
                canRedo = canRedo,
                onBack = onClose,
                onUndo = model::undo,
                onRedo = model::redo,
                onRevert = model::revert,
                onSave = { model.save { ok -> if (ok) onClose() } },
                saving = saving,
            )

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = if (tab == EditorTab.TRANSFORM) 20.dp else 8.dp)
                    .then(
                        if (tab == EditorTab.TRANSFORM) Modifier.systemGestureExclusion()
                        else Modifier
                    ),
                contentAlignment = Alignment.Center,
            ) {
                val uri = media?.uri
                if (uri != null) {
                    if (isVideo) {
                        EditorVideoPreview(player = videoPlayer)
                    } else {
                        EditorPhotoPreview(
                            uri = uri,
                            edit = edit,
                            model = model,
                            tab = tab,
                            addMode = addMode,
                            selectedStickerId = selectedStickerId,
                        )
                    }
                } else {
                    CircularProgressIndicator(color = Color.White)
                }
            }

            EditorToolPanel(
                tab = tab,
                edit = edit,
                previewUri = media?.uri,
                isVideo = isVideo,
                stickerCategories = stickerCats,
                addMode = addMode,
                model = model,
                onOpenStickerOrder = onOpenStickerOrder,
                trimPositionMs = videoPositionMs,
                trimPlaying = videoPlaying,
                onTrimPlayingChange = { playing ->
                    videoPlaying = playing
                    if (playing && videoPlayer.currentPosition !in trimStart..trimEnd.coerceAtLeast(
                            trimStart
                        )
                    ) {
                        videoPlayer.seekTo(trimStart)
                    }
                },
                onTrimRangeChange = { start, end ->
                    model.setTrim(start, end, recordHistory = false)
                    if (videoPlayer.currentPosition < start || videoPlayer.currentPosition > end) {
                        videoPlayer.seekTo(start)
                    }
                },
                onTrimRangeFinished = {
                    model.setTrim(edit.trimStartMs, edit.trimEndMs ?: trimEnd, recordHistory = true)
                },
            )

            EditorBottomBar(
                tabs = tabs,
                selected = tab,
                onSelect = model::selectTab,
            )
        }

        if (saving) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

@Composable
private fun EditorTopBar(
    canUndo: Boolean,
    canRedo: Boolean,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onRevert: () -> Unit,
    onSave: () -> Unit,
    saving: Boolean,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painterResource(R.drawable.arrow_back),
                contentDescription = stringResource(R.string.back),
                tint = Color.White,
            )
        }
        IconButton(onClick = onUndo, enabled = canUndo) {
            Icon(
                painterResource(R.drawable.editor_undo),
                contentDescription = stringResource(R.string.editor_undo),
                tint = if (canUndo) Color.White else Color.White.copy(alpha = 0.35f),
            )
        }
        IconButton(onClick = onRedo, enabled = canRedo) {
            Icon(
                painterResource(R.drawable.editor_redo),
                contentDescription = stringResource(R.string.editor_redo),
                tint = if (canRedo) Color.White else Color.White.copy(alpha = 0.35f),
            )
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onRevert) {
            Text(stringResource(R.string.editor_revert), color = Color.White)
        }
        TextButton(onClick = onSave, enabled = !saving) {
            Text(
                stringResource(R.string.editor_save),
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun EditorPhotoPreview(
    uri: Uri,
    edit: EditState,
    model: EditorViewModel,
    tab: EditorTab,
    addMode: Int,
    selectedStickerId: Long?,
) {
    val matrix = remember(edit.filter, edit.adjust) {
        EditorExport.colorMatrix(edit.filter, edit.adjust)?.let { androidMatrix ->
            ColorFilter.colorMatrix(ColorMatrix(androidMatrix.array.copyOf()))
        }
    }
    var draftStroke by remember { mutableStateOf<List<StrokePoint>>(emptyList()) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var dragCorner by remember { mutableStateOf<Int?>(null) }
    // Чуть от края — иначе системный жест «назад» перехватывает углы.
    val quad = edit.perspective ?: PerspectiveQuad(
        topLeftX = 0.08f,
        topLeftY = 0.08f,
        topRightX = 0.92f,
        topRightY = 0.08f,
        bottomRightX = 0.92f,
        bottomRightY = 0.92f,
        bottomLeftX = 0.08f,
        bottomLeftY = 0.92f,
    )
    val density = LocalDensity.current
    val handleHitPx = with(density) { 44.dp.toPx() }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { boxSize = it }
            .then(if (tab == EditorTab.TRANSFORM) Modifier.systemGestureExclusion() else Modifier)
            .then(
                when {
                    tab == EditorTab.ADD && addMode == 0 -> Modifier.pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                draftStroke = listOf(
                                    StrokePoint(offset.x / size.width, offset.y / size.height)
                                )
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val p = change.position
                                draftStroke = draftStroke + StrokePoint(
                                    p.x / size.width,
                                    p.y / size.height,
                                )
                            },
                            onDragEnd = {
                                if (draftStroke.size >= 2) {
                                    model.addStroke(
                                        DrawStroke(
                                            points = draftStroke,
                                            color = 0xFFFF5252,
                                            width = 0.012f,
                                        )
                                    )
                                }
                                draftStroke = emptyList()
                            },
                        )
                    }

                    tab == EditorTab.TRANSFORM -> Modifier.pointerInput(quad, boxSize) {
                        if (boxSize.width == 0) return@pointerInput
                        fun corners(): List<Offset> = listOf(
                            Offset(quad.topLeftX * size.width, quad.topLeftY * size.height),
                            Offset(quad.topRightX * size.width, quad.topRightY * size.height),
                            Offset(quad.bottomRightX * size.width, quad.bottomRightY * size.height),
                            Offset(quad.bottomLeftX * size.width, quad.bottomLeftY * size.height),
                        )
                        detectDragGestures(
                            onDragStart = { offset ->
                                dragCorner = corners().indexOfFirst { c ->
                                    hypot(c.x - offset.x, c.y - offset.y) <= handleHitPx
                                }.takeIf { it >= 0 }
                            },
                            onDrag = { change, _ ->
                                val idx = dragCorner ?: return@detectDragGestures
                                change.consume()
                                val nx = (change.position.x / size.width).coerceIn(0f, 1f)
                                val ny = (change.position.y / size.height).coerceIn(0f, 1f)
                                val next = when (idx) {
                                    0 -> quad.copy(topLeftX = nx, topLeftY = ny)
                                    1 -> quad.copy(topRightX = nx, topRightY = ny)
                                    2 -> quad.copy(bottomRightX = nx, bottomRightY = ny)
                                    else -> quad.copy(bottomLeftX = nx, bottomLeftY = ny)
                                }
                                model.setPerspective(next, recordHistory = false)
                            },
                            onDragEnd = { dragCorner = null },
                            onDragCancel = { dragCorner = null },
                        )
                    }

                    else -> Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            colorFilter = matrix,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(2.dp)),
        )
        if (tab == EditorTab.TRANSFORM && boxSize.width > 0) {
            Canvas(Modifier.fillMaxSize()) {
                val pts = listOf(
                    Offset(quad.topLeftX * size.width, quad.topLeftY * size.height),
                    Offset(quad.topRightX * size.width, quad.topRightY * size.height),
                    Offset(quad.bottomRightX * size.width, quad.bottomRightY * size.height),
                    Offset(quad.bottomLeftX * size.width, quad.bottomLeftY * size.height),
                )
                val path = Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    lineTo(pts[1].x, pts[1].y)
                    lineTo(pts[2].x, pts[2].y)
                    lineTo(pts[3].x, pts[3].y)
                    close()
                }
                drawPath(path, Color.White.copy(alpha = 0.85f), style = Stroke(width = 2.dp.toPx()))
                pts.forEach { p ->
                    drawCircle(Color.White, radius = 11.dp.toPx(), center = p)
                    drawCircle(Color(0xFFFFD54F), radius = 7.dp.toPx(), center = p)
                }
            }
        }
        EditorStickersLayer(
            stickers = edit.stickers,
            interactive = tab == EditorTab.ADD && addMode != 0,
            selectedId = selectedStickerId,
            onSelect = model::selectSticker,
            onUpdate = model::updateSticker,
            onDuplicate = model::duplicateSticker,
            onFlip = model::flipSticker,
            onDelete = model::deleteSticker,
        )
        edit.texts.forEach { t ->
            Text(
                text = t.text,
                color = Color(t.color),
                fontSize = (22 * t.scale).sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun EditorStickersLayer(
    stickers: List<StickerOverlay>,
    interactive: Boolean,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    onUpdate: (
        id: Long,
        x: Float?,
        y: Float?,
        scale: Float?,
        rotation: Float?,
        recordHistory: Boolean,
    ) -> Unit,
    onDuplicate: (Long) -> Unit,
    onFlip: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val density = LocalDensity.current
        if (interactive) {
            Box(
                Modifier
                    .fillMaxSize()
                    .zIndex(1f)
                    .pointerInput(selectedId) {
                        detectTapGestures { onSelect(null) }
                    },
            )
        }
        stickers.forEach { sticker ->
            val selected = selectedId == sticker.id
            val boxPx =
                with(density) { (56.dp * sticker.scale).toPx() }.coerceAtLeast(with(density) { 48.dp.toPx() })
            val cx = sticker.x * widthPx
            val cy = sticker.y * heightPx
            val latest by rememberUpdatedState(sticker)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .zIndex(if (selected) 4f else 2f)
                    .offset {
                        IntOffset((cx - boxPx / 2f).roundToInt(), (cy - boxPx / 2f).roundToInt())
                    }
                    .size(with(density) { boxPx.toDp() })
                    .graphicsLayer { rotationZ = sticker.rotation }
                    .then(
                        if (!interactive) Modifier
                        else Modifier
                            .pointerInput(sticker.id, widthPx, heightPx) {
                                detectDragGestures(
                                    onDragStart = { onSelect(sticker.id) },
                                    onDrag = { change, drag ->
                                        change.consume()
                                        val cur = latest
                                        // drag в локальных координатах после rotationZ → в экранные
                                        val rad = Math.toRadians(cur.rotation.toDouble())
                                        val cos = kotlin.math.cos(rad).toFloat()
                                        val sin = kotlin.math.sin(rad).toFloat()
                                        val dx = drag.x * cos - drag.y * sin
                                        val dy = drag.x * sin + drag.y * cos
                                        onUpdate(
                                            cur.id,
                                            cur.x + dx / widthPx,
                                            cur.y + dy / heightPx,
                                            null,
                                            null,
                                            false,
                                        )
                                    },
                                    onDragEnd = {
                                        onUpdate(
                                            sticker.id,
                                            null,
                                            null,
                                            null,
                                            null,
                                            true
                                        )
                                    },
                                    onDragCancel = {
                                        onUpdate(
                                            sticker.id,
                                            null,
                                            null,
                                            null,
                                            null,
                                            true
                                        )
                                    },
                                )
                            }
                            .pointerInput(sticker.id) {
                                detectTapGestures { onSelect(sticker.id) }
                            },
                    ),
            ) {
                // flip только на эмодзи — иначе scaleX инвертирует drag
                Text(
                    text = sticker.emoji,
                    fontSize = (36 * sticker.scale).sp,
                    modifier = Modifier.graphicsLayer {
                        scaleX = if (sticker.flipHorizontal) -1f else 1f
                    },
                )
                if (interactive && selected) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .border(1.5.dp, Color.White, RoundedCornerShape(4.dp)),
                    )
                }
            }
            if (interactive && selected) {
                val handleR = with(density) { 11.dp.toPx() }
                val corners = listOf(
                    Offset(cx - boxPx / 2f, cy - boxPx / 2f),
                    Offset(cx + boxPx / 2f, cy - boxPx / 2f),
                    Offset(cx + boxPx / 2f, cy + boxPx / 2f),
                    Offset(cx - boxPx / 2f, cy + boxPx / 2f),
                )
                corners.forEachIndexed { index, corner ->
                    val rotated = rotateOffset(corner, Offset(cx, cy), sticker.rotation)
                    Box(
                        modifier = Modifier
                            .zIndex(5f)
                            .offset {
                                IntOffset(
                                    (rotated.x - handleR).roundToInt(),
                                    (rotated.y - handleR).roundToInt(),
                                )
                            }
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(1.dp, Color(0xFFBDBDBD), CircleShape)
                            // Ключ без rotated/cx — иначе жест рвётся на каждом кадре поворота.
                            .pointerInput(sticker.id, index, widthPx, heightPx) {
                                var startScale = 1f
                                var startRot = 0f
                                var startAngle = 0f
                                var startDist = 1f
                                var finger = Offset.Zero
                                var center = Offset.Zero
                                detectDragGestures(
                                    onDragStart = {
                                        val s = latest
                                        center = Offset(s.x * widthPx, s.y * heightPx)
                                        val half = with(density) {
                                            (56.dp * s.scale).toPx()
                                        }.coerceAtLeast(with(density) { 48.dp.toPx() }) / 2f
                                        val local = when (index) {
                                            0 -> Offset(center.x - half, center.y - half)
                                            1 -> Offset(center.x + half, center.y - half)
                                            2 -> Offset(center.x + half, center.y + half)
                                            else -> Offset(center.x - half, center.y + half)
                                        }
                                        finger = rotateOffset(local, center, s.rotation)
                                        startScale = s.scale
                                        startRot = s.rotation
                                        startDist = hypot(finger.x - center.x, finger.y - center.y)
                                            .coerceAtLeast(1f)
                                        startAngle = atan2(finger.y - center.y, finger.x - center.x)
                                    },
                                    onDrag = { change, drag ->
                                        change.consume()
                                        finger += drag
                                        val dist = hypot(finger.x - center.x, finger.y - center.y)
                                            .coerceAtLeast(1f)
                                        val angle = atan2(finger.y - center.y, finger.x - center.x)
                                        val deg = Math.toDegrees(
                                            normalizeAngleRad(angle - startAngle).toDouble(),
                                        ).toFloat()
                                        onUpdate(
                                            latest.id,
                                            null,
                                            null,
                                            (startScale * dist / startDist).coerceIn(0.35f, 4f),
                                            startRot + deg,
                                            false,
                                        )
                                    },
                                    onDragEnd = {
                                        onUpdate(
                                            latest.id,
                                            null,
                                            null,
                                            null,
                                            null,
                                            true
                                        )
                                    },
                                    onDragCancel = {
                                        onUpdate(
                                            latest.id,
                                            null,
                                            null,
                                            null,
                                            null,
                                            true
                                        )
                                    },
                                )
                            },
                    )
                }
                val menuY = (cy - boxPx / 2f - with(density) { 36.dp.toPx() })
                    .coerceAtLeast(with(density) { 4.dp.toPx() })
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    shadowElevation = 3.dp,
                    modifier = Modifier
                        .zIndex(6f)
                        .offset {
                            IntOffset(
                                (cx - with(density) { 48.dp.toPx() }).roundToInt(),
                                menuY.roundToInt()
                            )
                        },
                ) {
                    Row(
                        Modifier.padding(horizontal = 2.dp, vertical = 0.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = { onDuplicate(sticker.id) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                painterResource(R.drawable.editor_sticker_copy),
                                contentDescription = stringResource(R.string.editor_sticker_copy),
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        IconButton(
                            onClick = { onFlip(sticker.id) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                painterResource(R.drawable.editor_sticker_flip),
                                contentDescription = stringResource(R.string.editor_sticker_flip),
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        IconButton(
                            onClick = { onDelete(sticker.id) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                painterResource(R.drawable.delete),
                                contentDescription = stringResource(R.string.editor_sticker_delete),
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun rotateOffset(point: Offset, center: Offset, degrees: Float): Offset {
    val rad = Math.toRadians(degrees.toDouble())
    val cos = kotlin.math.cos(rad).toFloat()
    val sin = kotlin.math.sin(rad).toFloat()
    val dx = point.x - center.x
    val dy = point.y - center.y
    return Offset(center.x + dx * cos - dy * sin, center.y + dx * sin + dy * cos)
}

private fun normalizeAngleRad(delta: Float): Float {
    var d = delta
    val pi = Math.PI.toFloat()
    while (d > pi) d -= 2f * pi
    while (d < -pi) d += 2f * pi
    return d
}

@Composable
private fun EditorVideoPreview(player: ExoPlayer) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                this.player = player
            }
        },
        update = { it.player = player },
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp),
    )
}

@Composable
private fun EditorToolPanel(
    tab: EditorTab,
    edit: EditState,
    previewUri: Uri?,
    isVideo: Boolean,
    stickerCategories: List<StickerCategory>,
    addMode: Int,
    model: EditorViewModel,
    onOpenStickerOrder: () -> Unit,
    trimPositionMs: Long = 0L,
    trimPlaying: Boolean = false,
    onTrimPlayingChange: (Boolean) -> Unit = {},
    onTrimRangeChange: (Long, Long) -> Unit = { _, _ -> },
    onTrimRangeFinished: () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF121212))
            .padding(vertical = 8.dp)
    ) {
        when (tab) {
            EditorTab.AI -> AiPanel(onApply = model::applyAiEnhance)
            EditorTab.TRANSFORM -> TransformPanel(
                onRotate = model::rotate90,
                onFlip = model::flipHorizontal,
                onResetCrop = { model.setCrop(null) },
                onResetPerspective = model::resetPerspective,
            )

            EditorTab.FILTER -> FilterPanel(
                previewUri = previewUri,
                selected = edit.filter,
                onSelect = model::setFilter,
            )

            EditorTab.ADJUST -> AdjustPanel(
                params = edit.adjust,
                onChange = model::setAdjust,
            )

            EditorTab.ADD -> AddPanel(
                categories = stickerCategories,
                addMode = addMode,
                onAddModeChange = model::setAddMode,
                onAddSticker = model::addSticker,
                onAddText = { model.addText(it) },
                onOpenOrder = onOpenStickerOrder,
            )

            EditorTab.TRIM -> if (isVideo && previewUri != null) {
                val endMs = (edit.trimEndMs ?: 0L).coerceAtLeast(edit.trimStartMs + 1_000L)
                EditorVideoTrimPanel(
                    uri = previewUri,
                    range = edit.trimStartMs to endMs,
                    positionMs = trimPositionMs,
                    playing = trimPlaying,
                    onPlayingChange = onTrimPlayingChange,
                    onRangeChange = { (start, end) -> onTrimRangeChange(start, end) },
                    onRangeChangeFinished = onTrimRangeFinished,
                )
            }

            EditorTab.SOUND -> if (isVideo) {
                SoundPanel(
                    edit = edit,
                    onVolume = model::setOriginalVolume,
                    onMuted = model::setMuted,
                    onSoundtrack = model::setSoundtrack,
                    onSoundtrackVolume = model::setSoundtrackVolume,
                )
            }
        }
    }
}

@Composable
private fun AiPanel(onApply: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        TextButton(onClick = onApply) {
            Text(stringResource(R.string.editor_ai_apply), color = Color(0xFFFFD54F))
        }
    }
}

@Composable
private fun TransformPanel(
    onRotate: () -> Unit,
    onFlip: () -> Unit,
    onResetCrop: () -> Unit,
    onResetPerspective: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 12.dp)) {
        Text(
            text = stringResource(R.string.editor_perspective_hint),
            color = Color.Gray,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ToolChip(stringResource(R.string.editor_rotate), onRotate)
            ToolChip(stringResource(R.string.editor_flip), onFlip)
            ToolChip(stringResource(R.string.editor_reset_crop), onResetCrop)
            ToolChip(stringResource(R.string.editor_reset_perspective), onResetPerspective)
        }
    }
}

@Composable
private fun filterLabel(preset: FilterPreset): String = when (preset) {
    FilterPreset.ORIGINAL -> stringResource(R.string.editor_filter_original)
    FilterPreset.MONO -> stringResource(R.string.editor_filter_mono)
    FilterPreset.WARM -> stringResource(R.string.editor_filter_warm)
    FilterPreset.COOL -> stringResource(R.string.editor_filter_cool)
    FilterPreset.VIVID -> stringResource(R.string.editor_filter_vivid)
    FilterPreset.FADE -> stringResource(R.string.editor_filter_fade)
}

@Composable
private fun FilterPanel(
    previewUri: Uri?,
    selected: FilterPreset,
    onSelect: (FilterPreset) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = filterLabel(selected),
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 10.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(FilterPreset.entries.toList(), key = { it.name }) { preset ->
                val active = preset == selected
                val thumbFilter = remember(preset) {
                    EditorExport.colorMatrix(preset, AdjustParams())?.let { androidMatrix ->
                        ColorFilter.colorMatrix(ColorMatrix(androidMatrix.array.copyOf()))
                    }
                }
                Box(
                    Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .then(
                            if (active) Modifier.border(
                                2.dp,
                                Color.White,
                                RoundedCornerShape(12.dp)
                            )
                            else Modifier
                        )
                        .clickable { onSelect(preset) }
                ) {
                    if (previewUri != null) {
                        AsyncImage(
                            model = previewUri,
                            contentDescription = filterLabel(preset),
                            contentScale = ContentScale.Crop,
                            colorFilter = thumbFilter,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color(0xFF2A2A2A))
                        )
                    }
                }
            }
        }
    }
}

private enum class AdjustTarget { Brightness, Contrast, Saturation, Sharpness }

@Composable
private fun AdjustPanel(params: AdjustParams, onChange: (AdjustParams) -> Unit) {
    var target by remember { mutableStateOf(AdjustTarget.Brightness) }
    val value = when (target) {
        AdjustTarget.Brightness -> params.brightness
        AdjustTarget.Contrast -> params.contrast
        AdjustTarget.Saturation -> params.saturation
        AdjustTarget.Sharpness -> params.sharpness
    }
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = when (target) {
                AdjustTarget.Brightness -> stringResource(R.string.editor_brightness)
                AdjustTarget.Contrast -> stringResource(R.string.editor_contrast)
                AdjustTarget.Saturation -> stringResource(R.string.editor_saturation)
                AdjustTarget.Sharpness -> stringResource(R.string.editor_sharpness)
            },
            color = Color.White,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AdjustTarget.entries.forEach { t ->
                val active = t == target
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (active) Color.White else Color(0xFF2A2A2A))
                        .clickable { target = t },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = when (t) {
                            AdjustTarget.Brightness -> "☀"
                            AdjustTarget.Contrast -> "◐"
                            AdjustTarget.Saturation -> "◆"
                            AdjustTarget.Sharpness -> "!"
                        },
                        color = if (active) Color.Black else Color.White,
                    )
                }
            }
        }
        Slider(
            value = value,
            onValueChange = { v ->
                onChange(
                    when (target) {
                        AdjustTarget.Brightness -> params.copy(brightness = v)
                        AdjustTarget.Contrast -> params.copy(contrast = v)
                        AdjustTarget.Saturation -> params.copy(saturation = v)
                        AdjustTarget.Sharpness -> params.copy(sharpness = v)
                    }
                )
            },
            valueRange = if (target == AdjustTarget.Sharpness) 0f..1f else -1f..1f,
        )
    }
}

@Composable
private fun AddPanel(
    categories: List<StickerCategory>,
    addMode: Int,
    onAddModeChange: (Int) -> Unit,
    onAddSticker: (StickerCategory, StickerItem) -> Unit,
    onAddText: (String) -> Unit,
    onOpenOrder: () -> Unit,
) {
    var selectedCat by remember(categories) { mutableStateOf(categories.firstOrNull()) }
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            listOf(
                stringResource(R.string.editor_draw) to 0,
                stringResource(R.string.editor_stickers) to 1,
                stringResource(R.string.editor_text) to 2,
            ).forEach { (label, idx) ->
                Text(
                    text = label,
                    color = if (addMode == idx) Color(0xFFFFD54F) else Color.White,
                    modifier = Modifier.clickable { onAddModeChange(idx) },
                    fontWeight = if (addMode == idx) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
            Spacer(Modifier.weight(1f))
            if (addMode == 1) {
                Text(
                    text = stringResource(R.string.editor_sticker_order),
                    color = Color(0xFF90CAF9),
                    modifier = Modifier.clickable(onClick = onOpenOrder),
                    fontSize = 13.sp,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        when (addMode) {
            0 -> Text(
                stringResource(R.string.editor_draw_hint),
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            1 -> {
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    categories.forEach { cat ->
                        val active = cat.id == selectedCat?.id
                        Text(
                            text = "${cat.iconEmoji} ${cat.defaultTitle}",
                            color = if (active) Color.Black else Color.White,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (active) Color.White else Color(0xFF2A2A2A))
                                .clickable { selectedCat = cat }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            fontSize = 12.sp,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    selectedCat?.stickers?.forEach { item ->
                        Text(
                            text = item.emoji,
                            fontSize = 28.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF2A2A2A))
                                .clickable { selectedCat?.let { onAddSticker(it, item) } }
                                .padding(10.dp),
                        )
                    }
                }
            }

            2 -> {
                Row(
                    Modifier.padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("Hello", "Love", "OK", "★").forEach { sample ->
                        ToolChip(sample) { onAddText(sample) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SoundPanel(
    edit: EditState,
    onVolume: (Float) -> Unit,
    onMuted: (Boolean) -> Unit,
    onSoundtrack: (Uri?) -> Unit,
    onSoundtrackVolume: (Float) -> Unit,
) {
    val pickAudio = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> onSoundtrack(uri) }
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(stringResource(R.string.editor_video_sound), color = Color.White)
        Slider(value = edit.originalVolume, onValueChange = onVolume)
        Row {
            ToolChip(
                if (edit.muted) stringResource(R.string.unmute) else stringResource(R.string.mute)
            ) { onMuted(!edit.muted) }
            Spacer(Modifier.width(8.dp))
            ToolChip(stringResource(R.string.editor_add_soundtrack)) {
                pickAudio.launch("audio/*")
            }
            if (edit.soundtrackUri != null) {
                Spacer(Modifier.width(8.dp))
                ToolChip(stringResource(R.string.clear)) { onSoundtrack(null) }
            }
        }
        if (edit.soundtrackUri != null) {
            Text(stringResource(R.string.editor_soundtrack_volume), color = Color.White)
            Slider(value = edit.soundtrackVolume, onValueChange = onSoundtrackVolume)
        }
    }
}

@Composable
private fun ToolChip(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = Color.White,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF2A2A2A))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
