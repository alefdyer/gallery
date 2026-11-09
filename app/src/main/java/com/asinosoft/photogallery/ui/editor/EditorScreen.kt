package com.asinosoft.photogallery.ui.editor

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import com.asinosoft.photogallery.GalleryApp
import com.asinosoft.photogallery.R
import com.asinosoft.photogallery.data.Media
import com.asinosoft.photogallery.data.editor.EditorExport
import com.asinosoft.photogallery.data.editor.StickerCategory
import com.asinosoft.photogallery.data.editor.StickerItem
import com.asinosoft.photogallery.data.storage.StorageAuthProvider
import com.asinosoft.photogallery.data.storage.StorageType
import com.asinosoft.photogallery.ui.component.ZoomableMediaBox
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import android.graphics.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun EditorScreen(
    onClose: () -> Unit,
    onOpenStickerOrder: () -> Unit,
    onSaved: (Long) -> Unit = { onClose() },
    saveAlbumId: Long? = null,
    saveAsFavorite: Boolean = false,
    model: EditorViewModel = hiltViewModel(),
) {
    val media by model.media.collectAsState()
    val loadProgress by model.loadProgress.collectAsState()
    val tab by model.tab.collectAsState()
    val edit by model.edit.collectAsState()
    val openedEdit by model.openedEdit.collectAsState()
    val hasEdits = edit != openedEdit
    val canUndo by model.canUndo.collectAsState()
    val canRedo by model.canRedo.collectAsState()
    val saving by model.saving.collectAsState()
    val stickerCats by model.stickerCategories.collectAsState()
    val selectedStickerId by model.selectedStickerId.collectAsState()
    val addMode by model.addMode.collectAsState()
    val drawSettings by model.drawSettings.collectAsState()
    val textTyping by model.textTyping.collectAsState()
    val selectedTextId by model.selectedTextId.collectAsState()
    val typingOnPhoto = textTyping && tab == EditorTab.ADD && addMode == 2
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val imeBottom = imeInsets.getBottom(density)
    var imeSeen by remember { mutableStateOf(false) }
    LaunchedEffect(typingOnPhoto) {
        if (!typingOnPhoto) {
            imeSeen = false
            keyboard?.hide()
        }
    }
    LaunchedEffect(typingOnPhoto, imeBottom) {
        if (!typingOnPhoto) return@LaunchedEffect
        if (imeBottom > 0) {
            imeSeen = true
        } else if (imeSeen) {
            delay(150)
            if (imeInsets.getBottom(density) == 0) model.stopTextInput()
        }
    }
    val isVideo = media?.video != null
    val remoteMedia = media?.takeIf { it.storageType != StorageType.LOCAL }
    val isRemoteMedia = remoteMedia != null
    val tabs = remember(isVideo) { EditorTab.forMedia(isVideo) }
    val context = LocalContext.current
    val videoUri = media?.uri?.takeIf { isVideo }
    val videoPlayer = remember(media?.id, remoteMedia?.storageId) {
        val builder = ExoPlayer.Builder(context)
        if (remoteMedia != null) {
            val app = context.applicationContext as GalleryApp
            val httpFactory = OkHttpDataSource.Factory(app.httpClient)
                .setDefaultRequestProperties(
                    mapOf(
                        StorageAuthProvider.HEADER_STORAGE_ID to remoteMedia.storageId.toString()
                    )
                )
            builder.setMediaSourceFactory(
                DefaultMediaSourceFactory(DefaultDataSource.Factory(context, httpFactory))
            )
        }
        builder.build().apply { repeatMode = Player.REPEAT_MODE_OFF }
    }
    var videoPlaying by remember { mutableStateOf(true) }
    var videoPositionMs by remember { mutableLongStateOf(0L) }
    val trimStart = edit.trimStartMs
    val trimEnd = edit.trimEndMs ?: (media?.video?.duration ?: 0L)

    DisposableEffect(videoUri, videoPlayer) {
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
    DisposableEffect(videoPlayer) {
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

    var confirmLeave by remember { mutableStateOf(false) }
    var confirmRevert by remember { mutableStateOf(false) }
    fun requestClose() {
        if (saving) return
        if (hasEdits) confirmLeave = true else onClose()
    }
    BackHandler {
        when {
            confirmRevert -> confirmRevert = false
            confirmLeave -> confirmLeave = false
            else -> requestClose()
        }
    }

    val sourceWidth = media?.image?.width ?: 0
    val sourceHeight = media?.image?.height ?: 0
    val quarterTurn = edit.rotationDegrees % 180 != 0
    val displayWidth = if (quarterTurn) sourceHeight else sourceWidth
    val displayHeight = if (quarterTurn) sourceWidth else sourceHeight
    val toolPanel: @Composable (Modifier) -> Unit = { panelModifier ->
        EditorToolPanel(
            tab = tab,
            edit = edit,
            imageWidth = displayWidth,
            imageHeight = displayHeight,
            previewUri = media?.uri,
            previewMedia = media,
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
            modifier = panelModifier,
        )
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
                onBack = ::requestClose,
                onUndo = model::undo,
                onRedo = model::redo,
                onRevert = { if (hasEdits) confirmRevert = true },
                onSave = {
                    model.save(saveAlbumId, saveAsFavorite) { id ->
                        when {
                            id == null -> Unit
                            id > 0L -> onSaved(id)
                            else -> onClose()
                        }
                    }
                },
                saving = saving,
            )

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(
                        if (tab == EditorTab.TRANSFORM) Modifier.systemGestureExclusion()
                        else Modifier.padding(horizontal = 8.dp)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                val uri = media?.uri
                if (uri != null) {
                    if (isVideo) {
                        ZoomableMediaBox(aspectRatio = null) {
                            EditorVideoPreview(
                                player = videoPlayer,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    } else {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(
                                    horizontal = if (tab == EditorTab.TRANSFORM) 9.dp else 0.dp,
                                ),
                        ) {
                            EditorPhotoPreview(
                                uri = uri,
                                media = media,
                                edit = edit,
                                model = model,
                                tab = tab,
                                addMode = addMode,
                                drawSettings = drawSettings,
                                selectedStickerId = selectedStickerId,
                                imageWidth = sourceWidth,
                                imageHeight = sourceHeight,
                            )
                        }
                    }
                }
            }

            val keyboardOverlap = if (typingOnPhoto) imeBottom else 0
            Layout(
                modifier = Modifier.fillMaxWidth(),
                content = {
                    Column(Modifier.fillMaxWidth()) {
                        toolPanel(Modifier)
                        EditorBottomBar(
                            tabs = tabs,
                            selected = tab,
                            onSelect = model::selectTab,
                        )
                    }
                },
            ) { measurables, constraints ->
                val panel = measurables[0].measure(constraints)
                val reserve = panel.height
                    .coerceAtLeast(keyboardOverlap)
                    .coerceIn(constraints.minHeight, constraints.maxHeight)
                layout(constraints.maxWidth, reserve) {
                    panel.place(0, reserve - panel.height)
                }
            }
        }
        if ((loadProgress != null && media?.uri == null) || saving) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (saving) 0.45f else 0f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }

        if (confirmRevert) {
            AlertDialog(
                onDismissRequest = { confirmRevert = false },
                text = { Text(stringResource(R.string.editor_revert_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmRevert = false
                        model.revert()
                    }) {
                        Text(stringResource(R.string.editor_revert_original))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmRevert = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }

        if (confirmLeave) {
            AlertDialog(
                onDismissRequest = { confirmLeave = false },
                title = { Text(stringResource(R.string.editor_save_prompt)) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmLeave = false
                        model.save(saveAlbumId, saveAsFavorite) { id ->
                            when {
                                id == null -> Unit
                                id > 0L -> onSaved(id)
                                else -> onClose()
                            }
                        }
                    }) {
                        Text(stringResource(R.string.editor_save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        confirmLeave = false
                        onClose()
                    }) {
                        Text(stringResource(R.string.editor_discard))
                    }
                },
            )
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
private fun rememberEditorImageModel(uri: Uri, media: Media?): Any {
    val context = LocalContext.current
    val storageId = media?.storageId
    val remote = media != null && media.storageType != StorageType.LOCAL
    return remember(uri, storageId, remote) {
        if (!remote || storageId == null) {
            uri
        } else {
            ImageRequest.Builder(context)
                .data(uri)
                .httpHeaders(
                    NetworkHeaders.Builder()
                        .set(StorageAuthProvider.HEADER_STORAGE_ID, storageId.toString())
                        .build()
                )
                .build()
        }
    }
}

@Composable
private fun EditorPhotoPreview(
    uri: Uri,
    media: Media?,
    edit: EditState,
    model: EditorViewModel,
    tab: EditorTab,
    addMode: Int,
    drawSettings: DrawSettings,
    selectedStickerId: Long?,
    imageWidth: Int,
    imageHeight: Int,
) {
    val imageModel = rememberEditorImageModel(uri, media)
    val matrix = remember(edit.filter, edit.adjust) {
        EditorExport.colorMatrix(edit.filter, edit.adjust)?.let { androidMatrix ->
            ColorFilter.colorMatrix(ColorMatrix(androidMatrix.array.copyOf()))
        }
    }
    var settled by remember { mutableStateOf(edit) }
    var frozen by remember { mutableStateOf<EditState?>(null) }
    val spinChanged = settled.rotationDegrees != edit.rotationDegrees
            || settled.flipHorizontal != edit.flipHorizontal
            || settled.flipVertical != edit.flipVertical
    var turn by remember { mutableFloatStateOf(0f) }
    var mirrorX by remember { mutableFloatStateOf(1f) }
    var mirrorY by remember { mutableFloatStateOf(1f) }
    var snapCropFrame by remember { mutableStateOf(false) }
    if (spinChanged && frozen == null) {
        frozen = settled
        turn = 0f
        mirrorX = 1f
        mirrorY = 1f
    } else if (!spinChanged && frozen == null && settled != edit) {
        settled = edit
    }
    val pose = frozen ?: edit
    val targetEdit = rememberUpdatedState(edit)
    LaunchedEffect(frozen) {
        val from = frozen ?: return@LaunchedEffect
        val target = targetEdit.value
        val rotDelta = signedDegrees(from.rotationDegrees, target.rotationDegrees)
        val toX = if (target.flipHorizontal != from.flipHorizontal) -1f else 1f
        val toY = if (target.flipVertical != from.flipVertical) -1f else 1f
        val progress = Animatable(0f)
        try {
            progress.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) {
                turn = rotDelta * value
                mirrorX = 1f + (toX - 1f) * value
                mirrorY = 1f + (toY - 1f) * value
            }
        } finally {
            turn = 0f
            mirrorX = 1f
            mirrorY = 1f
            snapCropFrame = true
            settled = target
            frozen = null
        }
    }
    val poseQuarter = pose.rotationDegrees % 180 != 0
    val pixelW = if (poseQuarter) imageHeight else imageWidth
    val pixelH = if (poseQuarter) imageWidth else imageHeight
    var draftStroke by remember { mutableStateOf<List<StrokePoint>>(emptyList()) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val crop = pose.crop ?: CropRect()
    var transforming by remember { mutableStateOf(false) }
    var aspectFrom by remember { mutableStateOf<CropRect?>(null) }
    var aspectTo by remember { mutableStateOf<CropRect?>(null) }
    val aspectProgress = remember { Animatable(1f) }
    var displayedCrop by remember { mutableStateOf(crop) }
    if (snapCropFrame && frozen == null) {
        displayedCrop = crop
        aspectFrom = null
        aspectTo = null
        snapCropFrame = false
    }
    LaunchedEffect(crop) {
        if (transforming) {
            displayedCrop = crop
            aspectFrom = null
            aspectTo = null
            return@LaunchedEffect
        }
        val from = if (aspectFrom != null && aspectTo != null) {
            lerpCrop(aspectFrom!!, aspectTo!!, aspectProgress.value)
        } else {
            displayedCrop
        }
        if (from == crop) {
            displayedCrop = crop
            return@LaunchedEffect
        }
        val target = crop
        aspectFrom = from
        aspectTo = target
        try {
            aspectProgress.snapTo(0f)
            aspectProgress.animateTo(1f, tween(280, easing = FastOutSlowInEasing))
        } finally {
            if (aspectTo == target) {
                displayedCrop = target
                aspectFrom = null
                aspectTo = null
            }
        }
    }
    val renderCrop = when {
        transforming -> crop
        aspectFrom != null && aspectTo == crop ->
            lerpCrop(aspectFrom!!, crop, aspectProgress.value)

        displayedCrop != crop -> displayedCrop
        else -> crop
    }
    val cropState = rememberUpdatedState(crop)
    val density = LocalDensity.current
    val handleHitPx = with(density) { 36.dp.toPx() }
    val frameInsetY = with(density) { 9.dp.toPx() }
    var gestureLock by remember { mutableStateOf<CropLayout?>(null) }
    var gestureSerial by remember { mutableIntStateOf(0) }
    val settleScope = rememberCoroutineScope()
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val settle = remember { Animatable(1f) }
    var settleFrom by remember { mutableStateOf<CropLayout?>(null) }
    var settleTo by remember { mutableStateOf<CropLayout?>(null) }
    var settleCrop by remember { mutableStateOf(CropRect()) }
    val fittedLayout = if (boxSize.width > 0 && boxSize.height > 0) {
        cropLayout(
            boxSize.width.toFloat(),
            boxSize.height.toFloat(),
            pixelW,
            pixelH,
            renderCrop,
            frameInsetY,
        )
    } else {
        null
    }
    val liveLayout = if (tab == EditorTab.TRANSFORM) fittedLayout else null
    val settling = settleFrom != null && settleTo != null && settle.value < 1f
    val imageLayout = when {
        gestureLock != null -> gestureLock
        settling -> lerpLayout(settleFrom!!, settleTo!!, settle.value)
        else -> liveLayout
    }
    val frame = when {
        gestureLock != null -> frameFromCrop(crop, gestureLock!!)
        settling -> frameFromCrop(settleCrop, imageLayout!!)
        liveLayout != null -> liveLayout.frame
        else -> null
    }
    val zoomEnabled = tab != EditorTab.TRANSFORM
    val shapeId by model.selectedShapeId.collectAsState()
    val strokesState = rememberUpdatedState(edit.strokes)
    val shapeState = rememberUpdatedState(shapeId)
    val strokeFrameState = rememberUpdatedState(
        fittedLayout?.frame?.takeIf { it.width > 1f && it.height > 1f }
    )
    var aim by remember { mutableStateOf<Offset?>(null) }
    var sampleBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val pickSettings = rememberUpdatedState(drawSettings)
    val pickBitmap = rememberUpdatedState(sampleBitmap)
    val pickLayout = rememberUpdatedState(fittedLayout)
    val pickPose = rememberUpdatedState(pose)
    val pickContext = LocalContext.current
    LaunchedEffect(uri, media?.storageId, drawSettings.picking) {
        if (!drawSettings.picking) {
            sampleBitmap = null
            return@LaunchedEffect
        }
        sampleBitmap = withContext(Dispatchers.IO) {
            val remote = media != null && media.storageType != StorageType.LOCAL
            if (remote && media != null) {
                val app = pickContext.applicationContext as GalleryApp
                val request = okhttp3.Request.Builder()
                    .url(uri.toString())
                    .header(StorageAuthProvider.HEADER_STORAGE_ID, media.storageId.toString())
                    .build()
                runCatching {
                    app.httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@use null
                        response.body?.byteStream()?.use { input ->
                            val options = android.graphics.BitmapFactory.Options()
                                .apply { inSampleSize = 4 }
                            android.graphics.BitmapFactory.decodeStream(input, null, options)
                        }
                    }
                }.getOrNull()
            } else {
                pickContext.contentResolver.openInputStream(uri)?.use { input ->
                    val options =
                        android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
                    android.graphics.BitmapFactory.decodeStream(input, null, options)
                }
            }
        }
    }

    val shownTurn = if (frozen != null) turn else 0f
    val shownMirrorX = if (frozen != null) mirrorX else 1f
    val shownMirrorY = if (frozen != null) mirrorY else 1f
    val visibleFrame = if (tab != EditorTab.TRANSFORM) fittedLayout?.frame else null
    val orientScale = orientationFitScale(
        contentWidth = when {
            visibleFrame != null -> visibleFrame.width
            imageLayout != null -> imageLayout.imageWidth
            pixelW > 0 && pixelH > 0 && boxSize.width > 0 -> maxFrame(
                boxSize.width.toFloat(),
                boxSize.height.toFloat(),
                pixelW.toFloat() / pixelH,
                frameInsetY,
            ).width

            else -> 0f
        },
        contentHeight = when {
            visibleFrame != null -> visibleFrame.height
            imageLayout != null -> imageLayout.imageHeight
            pixelW > 0 && pixelH > 0 && boxSize.width > 0 -> maxFrame(
                boxSize.width.toFloat(),
                boxSize.height.toFloat(),
                pixelW.toFloat() / pixelH,
                frameInsetY,
            ).height

            else -> 0f
        },
        boxWidth = boxSize.width.toFloat(),
        boxHeight = boxSize.height.toFloat(),
        degrees = shownTurn,
    )
    // Пан зума одним пальцем всегда; стикер/текст забирают жест сами (consume).
    EditorZoomBox(zoomEnabled = zoomEnabled, oneFingerPan = true) { editorZoom ->
        Box(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .graphicsLayer {
                    transformOrigin = TransformOrigin.Center
                    rotationZ = shownTurn
                    scaleX = orientScale * shownMirrorX
                    scaleY = orientScale * shownMirrorY
                }
                .onSizeChanged { boxSize = it }
                .then(if (tab == EditorTab.TRANSFORM) Modifier.systemGestureExclusion() else Modifier)
                .then(
                    when {
                        tab == EditorTab.ADD && drawSettings.picking && (addMode == 0 || addMode == 2) ->
                            Modifier.pointerInput(Unit) {
                                fun colorAt(point: Offset): Long? = sampleDisplayedColor(
                                    bitmap = pickBitmap.value,
                                    x = point.x,
                                    y = point.y,
                                    layout = pickLayout.value,
                                    rotation = pickPose.value.rotationDegrees,
                                    flipHorizontal = pickPose.value.flipHorizontal,
                                    flipVertical = pickPose.value.flipVertical,
                                )

                                fun show(color: Long) {
                                    val current = pickSettings.value
                                    if (color != current.color) {
                                        model.setDraw(current.copy(color = color, picking = true))
                                        if (addMode == 2) model.tintSelectedText(color)
                                    }
                                }
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    aim = down.position
                                    colorAt(down.position)?.let(::show)
                                    try {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change =
                                                event.changes.firstOrNull { it.id == down.id }
                                                    ?: break
                                            if (!change.pressed) break
                                            change.consume()
                                            aim = change.position
                                            colorAt(change.position)?.let(::show)
                                        }
                                    } finally {
                                        val point = aim
                                        aim = null
                                        val current = pickSettings.value
                                        val picked = point?.let(::colorAt)
                                        model.setDraw(
                                            current.copy(
                                                color = picked ?: current.color,
                                                picking = false,
                                            )
                                        )
                                        if (addMode == 2) {
                                            picked?.let(model::tintSelectedText)
                                            model.finishTextEdit()
                                        }
                                    }
                                }
                            }

                        tab == EditorTab.ADD && addMode == 0 && drawSettings.style == DrawStyle.ERASER ->
                            Modifier.pointerInput(drawSettings) {
                                val radius = brushWidth(drawSettings)
                                fun norm(pos: Offset) = strokePointInFrame(pos, strokeFrameState.value, size)
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    var last = norm(down.position)
                                    var cancelled = false
                                    model.beginErase()
                                    model.eraseAlong(null, last, radius, wholeStroke = false)
                                    try {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            if (event.changes.count { it.pressed } >= 2) {
                                                cancelled = true
                                                break
                                            }
                                            val change =
                                                event.changes.firstOrNull { it.id == down.id }
                                                    ?: break
                                            if (!change.pressed) break
                                            change.consume()
                                            val point = norm(change.position)
                                            model.eraseAlong(
                                                last,
                                                point,
                                                radius,
                                                wholeStroke = false
                                            )
                                            last = point
                                        }
                                    } finally {
                                        if (cancelled) model.cancelErase() else model.endErase()
                                    }
                                }
                            }

                        tab == EditorTab.ADD && addMode == 0 -> Modifier.pointerInput(drawSettings) {
                            val strokesNow = strokesState
                            val shapeNow = shapeState
                            fun norm(pos: Offset) = strokePointInFrame(pos, strokeFrameState.value, size)
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                val start = norm(down.position)
                                val selected = strokesNow.value.find { it.id == shapeNow.value }
                                val frameW = strokeFrameState.value?.width?.takeIf { it > 1f }
                                    ?: size.width.toFloat()
                                val handle = selected?.let {
                                    hitHandle(it, start, 28.dp.toPx() / frameW)
                                }
                                if (handle != null && selected.style.isShape()) {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.changes.count { it.pressed } >= 2) break
                                        val change =
                                            event.changes.firstOrNull { it.id == down.id }
                                                ?: break
                                        if (!change.pressed) break
                                        change.consume()
                                        model.dragShapeHandle(handle, norm(change.position))
                                    }
                                } else if (drawSettings.style.isShape()) {
                                    var end = start
                                    var cancelled = false
                                    draftStroke = listOf(start, end)
                                    try {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            if (event.changes.count { it.pressed } >= 2) {
                                                cancelled = true
                                                break
                                            }
                                            val change =
                                                event.changes.firstOrNull { it.id == down.id }
                                                    ?: break
                                            if (!change.pressed) break
                                            change.consume()
                                            end = norm(change.position)
                                            draftStroke = listOf(start, end)
                                        }
                                    } finally {
                                        val path = draftStroke
                                        draftStroke = emptyList()
                                        if (!cancelled && path.size >= 2) {
                                            model.addStroke(strokeFor(drawSettings, path))
                                        }
                                    }
                                } else {
                                    var cancelled = false
                                    draftStroke = listOf(start)
                                    try {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            if (event.changes.count { it.pressed } >= 2) {
                                                cancelled = true
                                                break
                                            }
                                            val change =
                                                event.changes.firstOrNull { it.id == down.id }
                                                    ?: break
                                            if (!change.pressed) break
                                            change.consume()
                                            draftStroke = draftStroke + norm(change.position)
                                        }
                                    } finally {
                                        val path = draftStroke
                                        draftStroke = emptyList()
                                        if (!cancelled && path.size >= 2) {
                                            model.addStroke(strokeFor(drawSettings, path))
                                        }
                                    }
                                }
                            }
                        }

                        tab == EditorTab.TRANSFORM -> Modifier.pointerInput(
                            boxSize,
                            pixelW,
                            pixelH,
                            frameInsetY,
                        ) {
                            if (boxSize.width == 0) return@pointerInput
                            fun layoutOf(rect: CropRect) = cropLayout(
                                size.width.toFloat(),
                                size.height.toFloat(),
                                pixelW,
                                pixelH,
                                rect,
                                frameInsetY,
                            )
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val mine = gestureSerial + 1
                                gestureSerial = mine
                                val pointers =
                                    LinkedHashMap<androidx.compose.ui.input.pointer.PointerId, Offset>()
                                pointers[down.id] = down.position
                                var working = cropState.value
                                val startLayout = layoutOf(working)
                                val handle = cropHandleAt(
                                    down.position,
                                    frameFromCrop(working, startLayout),
                                    handleHitPx,
                                )
                                if (handle >= 0) gestureLock = startLayout else gestureLock = null
                                settleJob?.cancel()
                                settleJob = null
                                if (handle < 0) {
                                    settleFrom = null
                                    settleTo = null
                                }
                                transforming = true
                                aspectFrom = null
                                aspectTo = null
                                model.beginCropDrag()
                                try {
                                    var event = awaitPointerEvent()
                                    while (event.changes.fastAny { it.pressed }) {
                                        val before = HashMap(pointers)
                                        event.changes.fastForEach { change ->
                                            if (change.pressed) pointers[change.id] =
                                                change.position
                                            else pointers.remove(change.id)
                                        }
                                        if (handle >= 0) {
                                            val change =
                                                event.changes.firstOrNull { it.id == down.id && it.pressed }
                                            if (change != null && change.positionChanged()) {
                                                val delta =
                                                    change.position - change.previousPosition
                                                change.consume()
                                                working = moveCrop(
                                                    working,
                                                    handle,
                                                    delta.x / startLayout.imageWidth,
                                                    delta.y / startLayout.imageHeight,
                                                )
                                                model.updateCrop(working)
                                            }
                                        } else if (pointers.size >= 2) {
                                            val ids = pointers.keys.take(2).toList()
                                            val startA = before[ids[0]] ?: pointers.getValue(ids[0])
                                            val startB = before[ids[1]] ?: pointers.getValue(ids[1])
                                            val endA = pointers.getValue(ids[0])
                                            val endB = pointers.getValue(ids[1])
                                            val spanBefore = hypot(
                                                startB.x - startA.x,
                                                startB.y - startA.y,
                                            ).coerceAtLeast(1f)
                                            val spanAfter = hypot(
                                                endB.x - endA.x,
                                                endB.y - endA.y,
                                            ).coerceAtLeast(1f)
                                            working = zoomCrop(working, spanAfter / spanBefore)
                                            model.updateCrop(working)
                                            event.changes.fastForEach {
                                                if (it.positionChanged()) it.consume()
                                            }
                                        } else {
                                            val pan = event.calculatePan()
                                            val lay = layoutOf(working)
                                            working = panCrop(
                                                working,
                                                pan.x,
                                                pan.y,
                                                lay.imageWidth,
                                                lay.imageHeight,
                                            )
                                            model.updateCrop(working)
                                            event.changes.fastForEach {
                                                if (it.positionChanged()) it.consume()
                                            }
                                        }
                                        event = awaitPointerEvent()
                                    }
                                } finally {
                                    if (gestureSerial != mine) return@awaitEachGesture
                                    if (handle >= 0) {
                                        val serial = mine
                                        val from = startLayout
                                        val to = layoutOf(working)
                                        val cropNow = working
                                        settleJob = settleScope.launch {
                                            if (gestureSerial != serial) return@launch
                                            settleCrop = cropNow
                                            settleFrom = from
                                            settleTo = to
                                            settle.snapTo(0f)
                                            if (gestureSerial != serial) return@launch
                                            gestureLock = null
                                            try {
                                                settle.animateTo(
                                                    1f,
                                                    tween(320, easing = FastOutSlowInEasing),
                                                )
                                            } finally {
                                                if (gestureSerial == serial) {
                                                    gestureLock = null
                                                    settleFrom = null
                                                    settleTo = null
                                                }
                                            }
                                        }
                                    } else {
                                        gestureLock = null
                                    }
                                    transforming = false
                                    model.endCropDrag()
                                }
                            }
                        }

                        else -> Modifier
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            val rotation = pose.rotationDegrees % 360
            val quarterTurn = rotation == 90 || rotation == 270
            val otherLayout = if (tab != EditorTab.TRANSFORM) fittedLayout else null
            val slot = when {
                imageLayout != null -> ImageFit(
                    imageLayout.imageLeft,
                    imageLayout.imageTop,
                    imageLayout.imageWidth,
                    imageLayout.imageHeight,
                )

                quarterTurn && boxSize.width > 0 && pixelW > 0 && pixelH > 0 -> maxFrame(
                    boxSize.width.toFloat(),
                    boxSize.height.toFloat(),
                    pixelW.toFloat() / pixelH,
                    frameInsetY,
                )

                else -> null
            }
            val photoModifier = Modifier.rotatedImage(
                left = (otherLayout?.imageLeft ?: slot?.left ?: 0f),
                top = (otherLayout?.imageTop ?: slot?.top ?: 0f),
                width = (otherLayout?.imageWidth ?: slot?.width ?: 0f),
                height = (otherLayout?.imageHeight ?: slot?.height ?: 0f),
                rotationDegrees = rotation,
                flipHorizontal = pose.flipHorizontal,
                flipVertical = pose.flipVertical,
            )
            if (otherLayout != null) {
                val cropFrame = otherLayout.frame
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            val content = this
                            clipRect(
                                cropFrame.left,
                                cropFrame.top,
                                cropFrame.left + cropFrame.width,
                                cropFrame.top + cropFrame.height,
                            ) {
                                content.drawContent()
                            }
                        },
                ) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        colorFilter = matrix,
                        modifier = photoModifier,
                    )
                }
            } else {
                AsyncImage(
                    model = imageModel,
                    contentDescription = null,
                    contentScale = if (slot != null) ContentScale.FillBounds else ContentScale.Fit,
                    colorFilter = matrix,
                    modifier = if (slot != null) {
                        photoModifier
                    } else {
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(2.dp))
                            .spinFlip(rotation, pose.flipHorizontal, pose.flipVertical)
                    },
                )
            }
            if (frame != null) {
                Canvas(Modifier.fillMaxSize()) {
                    val left = frame.left
                    val top = frame.top
                    val right = frame.left + frame.width
                    val bottom = frame.top + frame.height
                    val shade = Color.Black.copy(alpha = 0.45f)
                    drawRect(shade, Offset.Zero, Size(size.width, top.coerceAtLeast(0f)))
                    drawRect(
                        shade,
                        Offset(0f, bottom),
                        Size(size.width, (size.height - bottom).coerceAtLeast(0f)),
                    )
                    drawRect(
                        shade,
                        Offset(0f, top),
                        Size(left.coerceAtLeast(0f), (bottom - top).coerceAtLeast(0f))
                    )
                    drawRect(
                        shade,
                        Offset(right, top),
                        Size(
                            (size.width - right).coerceAtLeast(0f),
                            (bottom - top).coerceAtLeast(0f)
                        ),
                    )
                    val frame = Color.White
                    drawRect(
                        frame,
                        Offset(left, top),
                        Size(right - left, bottom - top),
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                    val thirdW = (right - left) / 3f
                    val thirdH = (bottom - top) / 3f
                    val grid = frame.copy(alpha = 0.55f)
                    val gridStroke = Stroke(width = 1.dp.toPx())
                    repeat(2) { i ->
                        val x = left + thirdW * (i + 1)
                        val y = top + thirdH * (i + 1)
                        drawLine(
                            grid,
                            Offset(x, top),
                            Offset(x, bottom),
                            strokeWidth = gridStroke.width
                        )
                        drawLine(
                            grid,
                            Offset(left, y),
                            Offset(right, y),
                            strokeWidth = gridStroke.width
                        )
                    }
                    val arm = 16.dp.toPx()
                    val thick = Stroke(width = 3.dp.toPx())
                    fun corner(x: Float, y: Float, dx: Float, dy: Float) {
                        drawLine(
                            frame,
                            Offset(x, y),
                            Offset(x + dx * arm, y),
                            strokeWidth = thick.width
                        )
                        drawLine(
                            frame,
                            Offset(x, y),
                            Offset(x, y + dy * arm),
                            strokeWidth = thick.width
                        )
                    }
                    corner(left, top, 1f, 1f)
                    corner(right, top, -1f, 1f)
                    corner(right, bottom, -1f, -1f)
                    corner(left, bottom, 1f, -1f)
                    val bar = 18.dp.toPx()
                    val midX = (left + right) / 2f
                    val midY = (top + bottom) / 2f
                    drawLine(
                        frame,
                        Offset(midX - bar, top),
                        Offset(midX + bar, top),
                        strokeWidth = thick.width
                    )
                    drawLine(
                        frame,
                        Offset(midX - bar, bottom),
                        Offset(midX + bar, bottom),
                        strokeWidth = thick.width
                    )
                    drawLine(
                        frame,
                        Offset(left, midY - bar),
                        Offset(left, midY + bar),
                        strokeWidth = thick.width
                    )
                    drawLine(
                        frame,
                        Offset(right, midY - bar),
                        Offset(right, midY + bar),
                        strokeWidth = thick.width
                    )
                }
            }
            val overlayFrame = fittedLayout?.frame?.takeIf { it.width > 1f && it.height > 1f }?.let {
                OverlayContentFrame(it.left, it.top, it.width, it.height)
            }
            DrawStrokesOverlay(
                strokes = edit.strokes,
                draft = if (draftStroke.size >= 2 && drawSettings.style != DrawStyle.ERASER) {
                    strokeFor(drawSettings, draftStroke)
                } else {
                    null
                },
                selectedId = shapeId,
                contentFrame = overlayFrame,
                modifier = Modifier.fillMaxSize(),
            )
            if (drawSettings.picking && aim != null) {
                ColorAim(aim!!, Modifier.fillMaxSize())
            }
            EditorStickersLayer(
                stickers = edit.stickers,
                contentFrame = overlayFrame,
                interactive = tab == EditorTab.ADD && addMode == 1,
                selectedId = selectedStickerId,
                zoom = editorZoom,
                onSelect = model::selectSticker,
                onUpdate = model::updateSticker,
                onDuplicate = model::duplicateSticker,
                onFlip = model::flipSticker,
                onDelete = model::deleteSticker,
            )
            val selectedTextId by model.selectedTextId.collectAsState()
            val textTyping by model.textTyping.collectAsState()
            EditorTextsLayer(
                texts = edit.texts,
                contentFrame = overlayFrame,
                interactive = tab == EditorTab.ADD && addMode == 2,
                typing = textTyping && tab == EditorTab.ADD && addMode == 2,
                selectedId = selectedTextId,
                zoom = editorZoom,
                onSelect = model::selectText,
                onClear = {
                    model.stopTextInput()
                    model.selectText(null)
                },
                onTypeAt = { x, y -> model.startTextInput(x = x, y = y) },
                onTypeText = { model.startTextInput(it) },
                onTextChange = { model.editTextFields(text = it, live = true) },
                onUpdate = { id, x, y, scale, rotation, record ->
                    model.updateText(id, x, y, scale, rotation, record)
                },
                onDuplicate = model::duplicateText,
                onDelete = model::deleteText,
            )
        }
    }
}

/**
 * Щипок для зума превью.
 * Два пальца — всегда (Initial pass, до стикеров/текста).
 * Один палец при scale=1 не перехватывается — стикеры/рисование работают.
 * translation в координатах экрана (без * scale) — палец 1:1.
 */
@Composable
private fun EditorZoomBox(
    zoomEnabled: Boolean,
    oneFingerPan: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(zoom: Float) -> Unit,
) {
    var viewSize by remember { mutableStateOf(Size.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(zoomEnabled) {
        if (!zoomEnabled) {
            scale = 1f
            offsetX = 0f
            offsetY = 0f
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { viewSize = it.toSize() }
            .then(
                if (!zoomEnabled || viewSize == Size.Zero) Modifier
                else Modifier.pointerInput(viewSize, oneFingerPan) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var panTravel = 0f
                        var panning = false
                        // Стикер/ручка/текст забрали down — пан этого жеста больше не трогаем.
                        var overlayClaimed = false
                        var active = true
                        while (active) {
                            // Pinch раньше детей — стикеры/текст не съедают zoom-out.
                            val initial = awaitPointerEvent(PointerEventPass.Initial)
                            active = initial.changes.fastAny { it.pressed }
                            val pressed = initial.changes.count { it.pressed }
                            if (pressed >= 2) {
                                val zoom = initial.calculateZoom()
                                val pan = initial.calculatePan()
                                val newScale = (scale * zoom).coerceIn(1f, EDITOR_MAX_ZOOM)
                                val maxX =
                                    max(0f, (viewSize.width * newScale - viewSize.width) / 2f)
                                val maxY =
                                    max(0f, (viewSize.height * newScale - viewSize.height) / 2f)
                                // Держим точку щипка: offset растёт пропорционально zoom.
                                offsetX = (offsetX * zoom + pan.x).coerceIn(-maxX, maxX)
                                offsetY = (offsetY * zoom + pan.y).coerceIn(-maxY, maxY)
                                scale = newScale
                                initial.changes.fastForEach {
                                    if (it.positionChanged()) it.consume()
                                }
                            } else if (oneFingerPan && scale > 1.01f && active) {
                                val event = awaitPointerEvent(PointerEventPass.Final)
                                active = event.changes.fastAny { it.pressed }
                                if (event.changes.any { it.isConsumed }) overlayClaimed = true
                                if (!overlayClaimed) {
                                    val pan = event.calculatePan()
                                    panTravel += hypot(pan.x, pan.y)
                                    if (!panning && panTravel < viewConfiguration.touchSlop) {
                                        continue
                                    }
                                    panning = true
                                    val maxX =
                                        max(0f, (viewSize.width * scale - viewSize.width) / 2f)
                                    val maxY =
                                        max(0f, (viewSize.height * scale - viewSize.height) / 2f)
                                    // pan уже в экранных px — без умножения на scale.
                                    offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                                    offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                                    event.changes.fastForEach {
                                        if (it.positionChanged()) it.consume()
                                    }
                                }
                            }
                        }
                        if (scale < 1.05f) {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                },
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                },
            content = { content(scale) },
        )
    }
}

private const val EDITOR_MAX_ZOOM = 5f

@Composable
private fun EditorStickersLayer(
    stickers: List<StickerOverlay>,
    contentFrame: OverlayContentFrame? = null,
    interactive: Boolean,
    selectedId: Long?,
    zoom: Float = 1f,
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
    var layerCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { layerCoords = it },
    ) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        // Позиция и размер от кадра фото, не letterbox — как в EditorExport.
        val frameLeft = contentFrame?.left ?: 0f
        val frameTop = contentFrame?.top ?: 0f
        val frameW = contentFrame?.width?.takeIf { it > 1f } ?: widthPx
        val frameH = contentFrame?.height?.takeIf { it > 1f } ?: heightPx
        val sizeRefPx = frameW
        val density = LocalDensity.current
        if (interactive) {
            Box(
                Modifier
                    .fillMaxSize()
                    .zIndex(1f)
                    .pointerInput(selectedId) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val start = down.position
                            val slop = viewConfiguration.touchSlop
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.count { it.pressed } >= 2) return@awaitEachGesture
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: return@awaitEachGesture
                                if (!change.pressed) {
                                    onSelect(null)
                                    return@awaitEachGesture
                                }
                                // Драг по пустому — отдаём пан зуму, не держим жест.
                                if (hypot(
                                        change.position.x - start.x,
                                        change.position.y - start.y,
                                    ) > slop
                                ) {
                                    return@awaitEachGesture
                                }
                            }
                        }
                    },
            )
        }
        val z = zoom.coerceAtLeast(0.01f)
        val chromeStroke = with(density) { 1.5.dp.toPx() / z }
        val handleSizePx = with(density) { 22.dp.toPx() / z }
        val handleR = handleSizePx / 2f
        stickers.forEach { sticker ->
            val selected = selectedId == sticker.id
            val latest by rememberUpdatedState(sticker)
            var dragPx by remember(sticker.id) { mutableStateOf(Offset.Zero) }
            var liveScale by remember(sticker.id) { mutableStateOf<Float?>(null) }
            var liveRotation by remember(sticker.id) { mutableStateOf<Float?>(null) }
            val drawScale = liveScale ?: sticker.scale
            val drawRotation = liveRotation ?: sticker.rotation
            val boxPx = stickerBoxSizePx(sizeRefPx, drawScale)
            val glyphPx = stickerTextSizePx(sizeRefPx, drawScale)
            val half = boxPx / 2f
            val cx = frameLeft + sticker.x * frameW + dragPx.x
            val cy = frameTop + sticker.y * frameH + dragPx.y
            val center = Offset(cx, cy)
            val corners = listOf(
                Offset(-1f, -1f),
                Offset(1f, -1f),
                Offset(1f, 1f),
                Offset(-1f, 1f),
            ).map { corner ->
                rotateOffset(
                    Offset(corner.x * half, corner.y * half),
                    Offset.Zero,
                    drawRotation,
                ) + center
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .zIndex(if (selected) 4f else 2f)
                    .offset {
                        IntOffset((cx - half).roundToInt(), (cy - half).roundToInt())
                    }
                    .size(with(density) { boxPx.toDp() })
                    .graphicsLayer {
                        rotationZ = drawRotation
                        transformOrigin = TransformOrigin.Center
                    }
                    .then(
                        if (!interactive) Modifier
                        else Modifier.pointerInput(sticker.id, frameW, frameH) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                down.consume()
                                onSelect(sticker.id)
                                val slop = viewConfiguration.touchSlop
                                var dragging = false
                                var acc = Offset.Zero
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.changes.count { it.pressed } >= 2) {
                                        if (dragging) {
                                            val cur = latest
                                            onUpdate(
                                                cur.id,
                                                cur.x + acc.x / frameW,
                                                cur.y + acc.y / frameH,
                                                null,
                                                null,
                                                true,
                                            )
                                            dragPx = Offset.Zero
                                        }
                                        return@awaitEachGesture
                                    }
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                        ?: return@awaitEachGesture
                                    if (!change.pressed) {
                                        if (dragging) {
                                            val cur = latest
                                            onUpdate(
                                                cur.id,
                                                cur.x + acc.x / frameW,
                                                cur.y + acc.y / frameH,
                                                null,
                                                null,
                                                true,
                                            )
                                            dragPx = Offset.Zero
                                        }
                                        return@awaitEachGesture
                                    }
                                    val delta = change.positionChange()
                                    change.consume()
                                    if (!dragging &&
                                        hypot(
                                            change.position.x - down.position.x,
                                            change.position.y - down.position.y,
                                        ) > slop
                                    ) {
                                        dragging = true
                                    }
                                    if (dragging && (delta.x != 0f || delta.y != 0f)) {
                                        val cur = latest
                                        val parent = rotateOffset(
                                            delta,
                                            Offset.Zero,
                                            cur.rotation,
                                        )
                                        acc += parent
                                        dragPx = acc
                                    }
                                }
                            }
                        },
                    ),
            ) {
                Text(
                    text = sticker.emoji,
                    fontSize = with(density) { glyphPx.toSp() },
                    modifier = Modifier.graphicsLayer {
                        scaleX = if (sticker.flipHorizontal) -1f else 1f
                    },
                )
                if (interactive && selected) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawRoundRect(
                            Color.White,
                            style = Stroke(width = chromeStroke),
                            cornerRadius = CornerRadius(4.dp.toPx() / z, 4.dp.toPx() / z),
                        )
                    }
                }
            }
            if (interactive && selected) {
                val centerState by rememberUpdatedState(center)
                val layerCoordsState by rememberUpdatedState(layerCoords)
                // Углы — только scale; отдельная ручка снизу — только rotate.
                corners.forEachIndexed { index, at ->
                    var handleCoords by remember(sticker.id, index) {
                        mutableStateOf<LayoutCoordinates?>(null)
                    }
                    Box(
                        Modifier
                            .zIndex(5f)
                            .offset {
                                IntOffset(
                                    (at.x - handleR).roundToInt(),
                                    (at.y - handleR).roundToInt(),
                                )
                            }
                            .size(with(density) { handleSizePx.toDp() })
                            .onGloballyPositioned { handleCoords = it }
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(
                                with(density) { (1.dp.toPx() / z).toDp() },
                                Color(0xFFBDBDBD),
                                CircleShape,
                            )
                            .pointerInput(sticker.id, index) {
                                detectOverlayScaleGesture(
                                    center = { centerState },
                                    layerCoords = { layerCoordsState },
                                    handleCoords = { handleCoords },
                                    startScale = { latest.scale },
                                    onScale = { scale ->
                                        liveScale = scale
                                        onUpdate(
                                            latest.id, null, null, scale, null, false,
                                        )
                                    },
                                    onEnd = {
                                        onUpdate(
                                            latest.id,
                                            null,
                                            null,
                                            liveScale ?: latest.scale,
                                            null,
                                            true,
                                        )
                                        liveScale = null
                                    },
                                )
                            },
                    )
                }
                val rotateAt = center + rotateOffset(
                    Offset(0f, half + handleSizePx * 1.15f),
                    Offset.Zero,
                    drawRotation,
                )
                var rotateCoords by remember(sticker.id) {
                    mutableStateOf<LayoutCoordinates?>(null)
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .zIndex(5f)
                        .offset {
                            IntOffset(
                                (rotateAt.x - handleR).roundToInt(),
                                (rotateAt.y - handleR).roundToInt(),
                            )
                        }
                        .size(with(density) { handleSizePx.toDp() })
                        .onGloballyPositioned { rotateCoords = it }
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(
                            with(density) { (1.dp.toPx() / z).toDp() },
                            Color(0xFFBDBDBD),
                            CircleShape,
                        )
                        .pointerInput(sticker.id) {
                            detectOverlayRotateGesture(
                                center = { centerState },
                                layerCoords = { layerCoordsState },
                                handleCoords = { rotateCoords },
                                startRotation = { latest.rotation },
                                onRotation = { rot ->
                                    liveRotation = rot
                                    onUpdate(
                                        latest.id, null, null, null, rot, false,
                                    )
                                },
                                onEnd = {
                                    onUpdate(
                                        latest.id,
                                        null,
                                        null,
                                        null,
                                        liveRotation ?: latest.rotation,
                                        true,
                                    )
                                    liveRotation = null
                                },
                            )
                        },
                ) {
                    Icon(
                        painterResource(R.drawable.editor_rotate),
                        contentDescription = stringResource(R.string.editor_rotate),
                        tint = Color.Black,
                        modifier = Modifier.size(with(density) { (handleSizePx * 0.55f).toDp() }),
                    )
                }
                val menuY = (cy - half - with(density) { 36.dp.toPx() / z })
                    .coerceAtLeast(with(density) { 4.dp.toPx() })
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    shadowElevation = 3.dp,
                    modifier = Modifier
                        .zIndex(6f)
                        .graphicsLayer {
                            scaleX = 1f / z
                            scaleY = 1f / z
                            transformOrigin = TransformOrigin.Center
                        }
                        .offset {
                            IntOffset(
                                (cx - with(density) { 48.dp.toPx() }).roundToInt(),
                                menuY.roundToInt(),
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
private fun EditorVideoPreview(
    player: ExoPlayer,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                this.player = player
            }
        },
        update = { it.player = player },
        modifier = modifier.fillMaxSize(),
    )
}

@Composable
private fun EditorToolPanel(
    tab: EditorTab,
    edit: EditState,
    imageWidth: Int,
    imageHeight: Int,
    previewUri: Uri?,
    previewMedia: Media?,
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Color(0xFF121212))
            .padding(vertical = if (tab == EditorTab.TRANSFORM) 2.dp else 8.dp)
    ) {
        when (tab) {
            EditorTab.TRANSFORM -> TransformPanel(
                imageWidth = imageWidth,
                imageHeight = imageHeight,
                onAspect = model::setCrop,
                onRotate = model::rotate90,
                onRotateRight = model::rotateClockwise,
                onFlipHorizontal = model::flipHorizontal,
                onFlipVertical = model::flipVertical,
            )

            EditorTab.FILTER -> FilterPanel(
                previewUri = previewUri,
                previewMedia = previewMedia,
                selected = edit.filter,
                onSelect = model::setFilter,
            )

            EditorTab.ADJUST -> AdjustPanel(
                params = edit.adjust,
                onChange = model::setAdjust,
                onAuto = model::applyAiEnhance,
            )

            EditorTab.ADD -> AddPanel(
                categories = stickerCategories,
                addMode = addMode,
                drawSettings = model.drawSettings.collectAsState().value,
                onDraw = model::setDraw,
                onAddModeChange = model::setAddMode,
                onAddSticker = model::addSticker,
                selectedText = edit.texts.find { it.id == model.selectedTextId.collectAsState().value },
                onTextEdit = model::editTextFields,
                onTextKeyboard = { model.startTextInput(model.selectedTextId.value) },
                onTextDone = {
                    model.stopTextInput()
                    model.selectText(null)
                },
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
private fun TransformPanel(
    imageWidth: Int,
    imageHeight: Int,
    onAspect: (CropRect?) -> Unit,
    onRotate: () -> Unit,
    onRotateRight: () -> Unit,
    onFlipHorizontal: () -> Unit,
    onFlipVertical: () -> Unit,
) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    val configuration = LocalConfiguration.current
    val screenAspect = configuration.screenWidthDp.toFloat() /
            configuration.screenHeightDp.toFloat().coerceAtLeast(1f)
    val presets = remember { aspectPresets() }
    val amber = Color(0xFFFFD54F)
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CropMenuButton(
                icon = R.drawable.editor_flip_h,
                description = stringResource(R.string.editor_flip),
                onClick = onFlipHorizontal,
            )
            CropMenuButton(
                icon = R.drawable.editor_flip_v,
                description = stringResource(R.string.editor_flip_vertical),
                onClick = onFlipVertical,
            )
            CropMenuButton(
                icon = R.drawable.editor_rotate,
                description = stringResource(R.string.editor_rotate),
                onClick = onRotate,
            )
            CropMenuButton(
                icon = R.drawable.editor_rotate_right,
                description = stringResource(R.string.editor_rotate_right),
                onClick = onRotateRight,
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
                presets.forEach { preset ->
                    val selected = preset.id == selectedId
                    Column(
                        Modifier
                            .widthIn(min = 64.dp)
                            .clickable {
                                selectedId = preset.id
                                val aspect = if (preset.useScreen) screenAspect else preset.aspect
                                onAspect(
                                    if (aspect == null || aspect <= 0f) null
                                    else CropRect.centered(imageWidth, imageHeight, aspect),
                                )
                            }
                            .padding(horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AspectPresetIcon(
                            preset = preset,
                            selected = selected,
                            screenAspect = screenAspect,
                        )
                        Text(
                            text = preset.label ?: stringResource(preset.labelRes),
                            color = if (selected) amber else Color(0xFFB0B0B0),
                            fontSize = 10.sp,
                            maxLines = 1,
                        )
                    }
                }
            }
    }
}

@Composable
private fun CropMenuButton(
    icon: Int,
    description: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(
            painterResource(icon),
            contentDescription = description,
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Рисует фото крупнее области просмотра: обычный size обрезается родителем и картинка тянется. */
private fun Modifier.placedImage(left: Float, top: Float, width: Float, height: Float): Modifier =
    layout { measurable, constraints ->
        val w = width.roundToInt().coerceAtLeast(1)
        val h = height.roundToInt().coerceAtLeast(1)
        val placeable = measurable.measure(Constraints.fixed(w, h))
        val layoutWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else w
        val layoutHeight = if (constraints.hasBoundedHeight) constraints.maxHeight else h
        layout(layoutWidth, layoutHeight) {
            placeable.place(left.roundToInt(), top.roundToInt())
        }
    }

private fun signedDegrees(from: Int, to: Int): Float {
    var delta = (to - from) % 360
    if (delta > 180) delta -= 360
    if (delta < -180) delta += 360
    return delta.toFloat()
}

private fun orientationFitScale(
    contentWidth: Float,
    contentHeight: Float,
    boxWidth: Float,
    boxHeight: Float,
    degrees: Float,
): Float {
    if (contentWidth <= 1f || contentHeight <= 1f || boxWidth <= 1f || boxHeight <= 1f) return 1f
    if (abs(degrees) < 0.01f) return 1f
    val radians = Math.toRadians(degrees.toDouble())
    val cosine = abs(cos(radians)).toFloat()
    val sine = abs(sin(radians)).toFloat()
    val boundsW = contentWidth * cosine + contentHeight * sine
    val boundsH = contentWidth * sine + contentHeight * cosine
    val fitted = min(boxWidth / boundsW, boxHeight / boundsH)
    val base = min(boxWidth / contentWidth, boxHeight / contentHeight)
    if (base <= 0f) return 1f
    return fitted / base
}

/** Слот в пропорциях после поворота. На 90° фото рисуется в обратных пропорциях и поворачивается вокруг центра. */
private fun Modifier.rotatedImage(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    rotationDegrees: Int,
    flipHorizontal: Boolean,
    flipVertical: Boolean,
): Modifier {
    val positioned = placedImage(left, top, width, height)
    val quarterTurn = rotationDegrees == 90 || rotationDegrees == 270
    if (!quarterTurn && rotationDegrees == 0 && !flipHorizontal && !flipVertical) return positioned
    val slotW = width.roundToInt().coerceAtLeast(1)
    val slotH = height.roundToInt().coerceAtLeast(1)
    val imageW = if (quarterTurn) slotH else slotW
    val imageH = if (quarterTurn) slotW else slotH
    return positioned.layout { measurable, _ ->
        val placeable = measurable.measure(Constraints.fixed(imageW, imageH))
        layout(slotW, slotH) {
            val x = (slotW - imageW) / 2
            val y = (slotH - imageH) / 2
            placeable.placeWithLayer(x, y) {
                transformOrigin = TransformOrigin.Center
                rotationZ = rotationDegrees.toFloat()
                scaleX = if (flipHorizontal) -1f else 1f
                scaleY = if (flipVertical) -1f else 1f
            }
        }
    }
}

private fun Modifier.spinFlip(
    rotationDegrees: Int,
    flipHorizontal: Boolean,
    flipVertical: Boolean
): Modifier {
    if (rotationDegrees == 0 && !flipHorizontal && !flipVertical) return this
    return graphicsLayer {
        transformOrigin = TransformOrigin.Center
        rotationZ = rotationDegrees.toFloat()
        scaleX = if (flipHorizontal) -1f else 1f
        scaleY = if (flipVertical) -1f else 1f
    }
}

private fun sampleDisplayedColor(
    bitmap: android.graphics.Bitmap?,
    x: Float,
    y: Float,
    layout: CropLayout?,
    rotation: Int,
    flipHorizontal: Boolean,
    flipVertical: Boolean,
): Long? {
    val image = bitmap ?: return null
    val slot = layout ?: return null
    val centerX = slot.imageLeft + slot.imageWidth / 2f
    val centerY = slot.imageTop + slot.imageHeight / 2f
    val matrix = Matrix()
    matrix.postScale(
        if (flipHorizontal) -1f else 1f,
        if (flipVertical) -1f else 1f,
        centerX,
        centerY,
    )
    matrix.postRotate(rotation.toFloat(), centerX, centerY)
    val inverse = Matrix()
    if (!matrix.invert(inverse)) return null
    val point = floatArrayOf(x, y)
    inverse.mapPoints(point)
    val quarter = rotation % 180 != 0
    val contentWidth = if (quarter) slot.imageHeight else slot.imageWidth
    val contentHeight = if (quarter) slot.imageWidth else slot.imageHeight
    val left = centerX - contentWidth / 2f
    val top = centerY - contentHeight / 2f
    val u = (point[0] - left) / contentWidth
    val v = (point[1] - top) / contentHeight
    if (u !in 0f..1f || v !in 0f..1f) return null
    val px = (u * (image.width - 1)).roundToInt().coerceIn(0, image.width - 1)
    val py = (v * (image.height - 1)).roundToInt().coerceIn(0, image.height - 1)
    return 0xFF000000L or (image.getPixel(px, py).toLong() and 0xFFFFFFL)
}

private data class ImageFit(val left: Float, val top: Float, val width: Float, val height: Float)

/** Нормализация штриха к кадру фото (как стикеры/текст), не к letterbox-контейнеру. */
private fun strokePointInFrame(pos: Offset, frame: ImageFit?, box: IntSize): StrokePoint {
    val fw = frame?.width?.takeIf { it > 1f }
    val fh = frame?.height?.takeIf { it > 1f }
    return if (frame != null && fw != null && fh != null) {
        StrokePoint((pos.x - frame.left) / fw, (pos.y - frame.top) / fh)
    } else {
        StrokePoint(
            pos.x / box.width.coerceAtLeast(1).toFloat(),
            pos.y / box.height.coerceAtLeast(1).toFloat(),
        )
    }
}

private data class CropLayout(
    val frame: ImageFit,
    val imageLeft: Float,
    val imageTop: Float,
    val imageWidth: Float,
    val imageHeight: Float,
)

private fun lerpCrop(from: CropRect, to: CropRect, fraction: Float): CropRect {
    val t = fraction.coerceIn(0f, 1f)
    return CropRect(
        left = from.left + (to.left - from.left) * t,
        top = from.top + (to.top - from.top) * t,
        right = from.right + (to.right - from.right) * t,
        bottom = from.bottom + (to.bottom - from.bottom) * t,
    )
}

private fun lerpLayout(from: CropLayout, to: CropLayout, fraction: Float): CropLayout {
    val t = fraction.coerceIn(0f, 1f)
    return CropLayout(
        frame = to.frame,
        imageLeft = from.imageLeft + (to.imageLeft - from.imageLeft) * t,
        imageTop = from.imageTop + (to.imageTop - from.imageTop) * t,
        imageWidth = from.imageWidth + (to.imageWidth - from.imageWidth) * t,
        imageHeight = from.imageHeight + (to.imageHeight - from.imageHeight) * t,
    )
}

private fun cropLayout(
    boxWidth: Float,
    boxHeight: Float,
    imageWidth: Int,
    imageHeight: Int,
    crop: CropRect,
    insetY: Float = 0f,
): CropLayout {
    val cropWidth = (crop.right - crop.left).coerceAtLeast(0.08f)
    val cropHeight = (crop.bottom - crop.top).coerceAtLeast(0.08f)
    val pixelWidth = if (imageWidth > 0) imageWidth.toFloat() else boxWidth
    val pixelHeight = if (imageHeight > 0) imageHeight.toFloat() else boxHeight
    val aspect = (cropWidth * pixelWidth) / (cropHeight * pixelHeight)
    val frame = maxFrame(boxWidth, boxHeight, aspect, insetY)
    val drawWidth = frame.width / cropWidth
    val drawHeight = frame.height / cropHeight
    return CropLayout(
        frame = frame,
        imageLeft = frame.left - crop.left * drawWidth,
        imageTop = frame.top - crop.top * drawHeight,
        imageWidth = drawWidth,
        imageHeight = drawHeight,
    )
}

private fun maxFrame(
    boxWidth: Float,
    boxHeight: Float,
    pixelAspect: Float,
    insetY: Float = 0f,
): ImageFit {
    if (boxWidth <= 0f || boxHeight <= 0f) return ImageFit(0f, 0f, boxWidth, boxHeight)
    val aspect = pixelAspect.coerceAtLeast(0.01f)
    val padY = insetY.coerceAtLeast(0f)
    val innerHeight = (boxHeight - padY * 2f).coerceAtLeast(1f)
    val width: Float
    val height: Float
    if (aspect >= boxWidth / innerHeight) {
        width = boxWidth
        height = boxWidth / aspect
    } else {
        height = innerHeight
        width = innerHeight * aspect
    }
    return ImageFit(
        (boxWidth - width) / 2f,
        padY + (innerHeight - height) / 2f,
        width,
        height,
    )
}

private fun frameFromCrop(crop: CropRect, layout: CropLayout): ImageFit {
    val width = (crop.right - crop.left) * layout.imageWidth
    val height = (crop.bottom - crop.top) * layout.imageHeight
    return ImageFit(
        layout.imageLeft + crop.left * layout.imageWidth,
        layout.imageTop + crop.top * layout.imageHeight,
        width,
        height,
    )
}

private fun cropHandleAt(point: Offset, frame: ImageFit, hit: Float): Int {
    val left = frame.left
    val top = frame.top
    val right = frame.left + frame.width
    val bottom = frame.top + frame.height
    val corners = listOf(
        Offset(left, top),
        Offset(right, top),
        Offset(right, bottom),
        Offset(left, bottom),
    )
    val corner = corners.indexOfFirst { hypot(it.x - point.x, it.y - point.y) <= hit }
    if (corner >= 0) return corner
    val edges = listOf(
        4 to (Offset(left, top) to Offset(right, top)),
        5 to (Offset(right, top) to Offset(right, bottom)),
        6 to (Offset(left, bottom) to Offset(right, bottom)),
        7 to (Offset(left, top) to Offset(left, bottom)),
    )
    return edges.firstOrNull { (_, segment) ->
        distanceToSegment(point, segment.first, segment.second) <= hit
    }?.first ?: -1
}

private fun moveCrop(crop: CropRect, handle: Int, dx: Float, dy: Float): CropRect {
    val min = 0.08f
    var left = crop.left
    var top = crop.top
    var right = crop.right
    var bottom = crop.bottom
    if (handle == 0 || handle == 3 || handle == 7) {
        left = (left + dx).coerceIn(0f, right - min)
    }
    if (handle == 1 || handle == 2 || handle == 5) {
        right = (right + dx).coerceIn(left + min, 1f)
    }
    if (handle == 0 || handle == 1 || handle == 4) {
        top = (top + dy).coerceIn(0f, bottom - min)
    }
    if (handle == 2 || handle == 3 || handle == 6) {
        bottom = (bottom + dy).coerceIn(top + min, 1f)
    }
    return CropRect(left, top, right, bottom)
}

private fun panCrop(
    crop: CropRect,
    dxPx: Float,
    dyPx: Float,
    drawWidth: Float,
    drawHeight: Float,
): CropRect {
    if (drawWidth <= 1f || drawHeight <= 1f) return crop
    val width = crop.right - crop.left
    val height = crop.bottom - crop.top
    val left = (crop.left - dxPx / drawWidth).coerceIn(0f, 1f - width)
    val top = (crop.top - dyPx / drawHeight).coerceIn(0f, 1f - height)
    return CropRect(left, top, left + width, top + height)
}

private fun zoomCrop(crop: CropRect, zoom: Float): CropRect {
    if (zoom <= 0f || zoom == 1f) return crop
    val currentWidth = (crop.right - crop.left).coerceAtLeast(0.08f)
    val currentHeight = (crop.bottom - crop.top).coerceAtLeast(0.08f)
    var scale = 1f / zoom
    if (currentWidth * scale > 1f || currentHeight * scale > 1f) {
        scale = min(1f / currentWidth, 1f / currentHeight)
    }
    if (currentWidth * scale < 0.08f || currentHeight * scale < 0.08f) {
        scale = max(0.08f / currentWidth, 0.08f / currentHeight)
    }
    val width = currentWidth * scale
    val height = currentHeight * scale
    val centerX = (crop.left + crop.right) / 2f
    val centerY = (crop.top + crop.bottom) / 2f
    var left = centerX - width / 2f
    var top = centerY - height / 2f
    if (left < 0f) left = 0f
    if (top < 0f) top = 0f
    if (left + width > 1f) left = 1f - width
    if (top + height > 1f) top = 1f - height
    return CropRect(left, top, left + width, top + height)
}

private fun distanceToSegment(point: Offset, start: Offset, end: Offset): Float {
    val abx = end.x - start.x
    val aby = end.y - start.y
    val length = abx * abx + aby * aby
    if (length <= 1f) return hypot(point.x - start.x, point.y - start.y)
    val t = (((point.x - start.x) * abx + (point.y - start.y) * aby) / length).coerceIn(0f, 1f)
    return hypot(point.x - (start.x + abx * t), point.y - (start.y + aby * t))
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
    previewMedia: Media?,
    selected: FilterPreset,
    onSelect: (FilterPreset) -> Unit,
) {
    val filterImageModel = previewUri?.let { rememberEditorImageModel(it, previewMedia) }
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
                    if (filterImageModel != null) {
                        AsyncImage(
                            model = filterImageModel,
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

private enum class AdjustTarget(
    @androidx.annotation.DrawableRes val icon: Int,
    @androidx.annotation.StringRes val label: Int,
) {
    Brightness(R.drawable.editor_adj_brightness, R.string.editor_brightness),
    Exposure(R.drawable.editor_adj_exposure, R.string.editor_exposure),
    Contrast(R.drawable.editor_adj_contrast, R.string.editor_contrast),
    Highlights(R.drawable.editor_adj_highlights, R.string.editor_highlights),
    Shadows(R.drawable.editor_adj_shadows, R.string.editor_shadows),
    Saturation(R.drawable.editor_adj_saturation, R.string.editor_saturation),
    Tint(R.drawable.editor_adj_tint, R.string.editor_tint),
    Temperature(R.drawable.editor_adj_temperature, R.string.editor_temperature),
    Definition(R.drawable.editor_adj_definition, R.string.editor_definition),
    Sharpness(R.drawable.editor_adj_sharpness, R.string.editor_sharpness),
}

@Composable
private fun AdjustPanel(
    params: AdjustParams,
    onChange: (AdjustParams) -> Unit,
    onAuto: () -> Unit,
) {
    var target by remember { mutableStateOf(AdjustTarget.Brightness) }
    val value = when (target) {
        AdjustTarget.Brightness -> params.brightness
        AdjustTarget.Exposure -> params.exposure
        AdjustTarget.Contrast -> params.contrast
        AdjustTarget.Highlights -> params.highlights
        AdjustTarget.Shadows -> params.shadows
        AdjustTarget.Saturation -> params.saturation
        AdjustTarget.Tint -> params.tint
        AdjustTarget.Temperature -> params.temperature
        AdjustTarget.Definition -> params.definition
        AdjustTarget.Sharpness -> params.sharpness
    }
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = stringResource(target.label),
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
            Box(
                Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF2A2A2A))
                    .clickable(onClick = onAuto)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.editor_ai_apply),
                    color = Color(0xFFFFD54F),
                    fontSize = 13.sp,
                )
            }
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
                    Icon(
                        painter = painterResource(t.icon),
                        contentDescription = stringResource(t.label),
                        tint = if (active) Color.Black else Color.White,
                        modifier = Modifier.size(22.dp),
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
                        AdjustTarget.Exposure -> params.copy(exposure = v)
                        AdjustTarget.Contrast -> params.copy(contrast = v)
                        AdjustTarget.Highlights -> params.copy(highlights = v)
                        AdjustTarget.Shadows -> params.copy(shadows = v)
                        AdjustTarget.Saturation -> params.copy(saturation = v)
                        AdjustTarget.Tint -> params.copy(tint = v)
                        AdjustTarget.Temperature -> params.copy(temperature = v)
                        AdjustTarget.Definition -> params.copy(definition = v)
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
    drawSettings: DrawSettings,
    onDraw: (DrawSettings) -> Unit,
    onAddModeChange: (Int) -> Unit,
    onAddSticker: (StickerCategory, StickerItem) -> Unit,
    selectedText: TextOverlay?,
    onTextEdit: (String?, Long?, String?, String?, String?, Boolean?, Boolean) -> Unit,
    onTextKeyboard: () -> Unit,
    onTextDone: () -> Unit,
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
        AddModesBody(
            categories = categories,
            selectedCat = selectedCat,
            onSelectCat = { selectedCat = it },
            addMode = addMode,
            drawSettings = drawSettings,
            onDraw = onDraw,
            onAddSticker = onAddSticker,
            selectedText = selectedText,
            onTextEdit = onTextEdit,
            onTextKeyboard = onTextKeyboard,
            onTextDone = onTextDone,
        )
    }
}

@Composable
private fun AddModesBody(
    categories: List<StickerCategory>,
    selectedCat: StickerCategory?,
    onSelectCat: (StickerCategory) -> Unit,
    addMode: Int,
    drawSettings: DrawSettings,
    onDraw: (DrawSettings) -> Unit,
    onAddSticker: (StickerCategory, StickerItem) -> Unit,
    selectedText: TextOverlay?,
    onTextEdit: (String?, Long?, String?, String?, String?, Boolean?, Boolean) -> Unit,
    onTextKeyboard: () -> Unit,
    onTextDone: () -> Unit,
) {
    SubcomposeLayout(Modifier.fillMaxWidth()) { constraints ->
        val loose = Constraints(maxWidth = constraints.maxWidth, maxHeight = Constraints.Infinity)
        val stickerTop = subcompose("sticker-top") {
            StickerRowProbe(bottom = false)
        }.first().measure(loose).height
        val stickerBottom = subcompose("sticker-bottom") {
            StickerRowProbe(bottom = true)
        }.first().measure(loose).height
        val emojiH = subcompose("emoji") {
            StickerEmojiRow(selectedCat, null)
        }.first().measure(loose).height
        val gap = 8.dp.roundToPx()
        val drawBottom = 46.dp.roundToPx()
        val textSlot = (stickerTop + gap + stickerBottom).coerceAtLeast(1)
        fun topHeight(bottom: Int): Dp =
            (textSlot - gap - bottom).coerceAtLeast(0).toDp()

        val shown = subcompose("shown") {
            when (addMode) {
                0 -> DrawToolbar(
                    settings = drawSettings,
                    onChange = onDraw,
                    modifier = Modifier.fillMaxWidth(),
                    topRowHeight = topHeight(drawBottom),
                )

                1 -> StickerBrowser(
                    categories = categories,
                    selected = selectedCat,
                    onSelect = onSelectCat,
                    onAdd = onAddSticker,
                    topRowHeight = topHeight(emojiH),
                )

                else -> TextPanel(
                    selected = selectedText,
                    drawSettings = drawSettings,
                    onDraw = onDraw,
                    onEdit = onTextEdit,
                    onKeyboard = onTextKeyboard,
                    onDone = onTextDone,
                    modifier = Modifier.fillMaxWidth(),
                    buttonSize = stickerTop.coerceAtLeast(1).toDp(),
                    chipSize = stickerBottom.coerceAtLeast(1).toDp(),
                )
            }
        }.first().measure(
            Constraints(maxWidth = constraints.maxWidth, maxHeight = textSlot),
        )
        layout(constraints.maxWidth, textSlot) {
            shown.place(0, 0)
        }
    }
}

@Composable
private fun StickerBrowser(
    categories: List<StickerCategory>,
    selected: StickerCategory?,
    onSelect: (StickerCategory) -> Unit,
    onAdd: (StickerCategory, StickerItem) -> Unit,
    topRowHeight: Dp?,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(
                    if (topRowHeight != null) Modifier
                        .height(topRowHeight)
                        .clipToBounds() else Modifier
                )
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            categories.forEach { cat ->
                val active = cat.id == selected?.id
                Text(
                    text = "${cat.iconEmoji} ${cat.defaultTitle}",
                    color = if (active) Color.Black else Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (active) Color.White else Color(0xFF2A2A2A))
                        .clickable { onSelect(cat) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    fontSize = 12.sp,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        StickerEmojiRow(selected, onAdd)
    }
}

@Composable
private fun StickerRowProbe(bottom: Boolean) {
    Text(
        text = if (bottom) "😀" else "Aa",
        fontSize = if (bottom) 28.sp else 12.sp,
        modifier = Modifier.padding(
            horizontal = if (bottom) 10.dp else 10.dp,
            vertical = if (bottom) 10.dp else 6.dp,
        ),
    )
}

@Composable
private fun StickerEmojiRow(
    selected: StickerCategory?,
    onAdd: ((StickerCategory, StickerItem) -> Unit)?,
) {
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        selected?.stickers?.forEach { item ->
            Text(
                text = item.emoji,
                fontSize = 28.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF2A2A2A))
                    .clickable { onAdd?.invoke(selected, item) }
                    .padding(10.dp),
            )
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
