package com.asinosoft.photogallery.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asinosoft.photogallery.R
import com.asinosoft.photogallery.data.ExifTakenAt
import com.asinosoft.photogallery.data.Image
import com.asinosoft.photogallery.data.Media
import com.asinosoft.photogallery.data.MediaDao
import com.asinosoft.photogallery.data.MediaService
import com.asinosoft.photogallery.data.MessageBus
import com.asinosoft.photogallery.data.editor.EditorExport
import com.asinosoft.photogallery.data.editor.StickerCatalog
import com.asinosoft.photogallery.data.editor.StickerCategory
import com.asinosoft.photogallery.data.editor.StickerItem
import com.asinosoft.photogallery.data.launchAndCatch
import com.asinosoft.photogallery.data.storage.StorageType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

@HiltViewModel
class EditorViewModel @Inject constructor(
    state: SavedStateHandle,
    private val mediaDao: MediaDao,
    private val mediaService: MediaService,
    private val export: EditorExport,
    private val stickers: StickerCatalog,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {
    private val mediaId: Long = state["mediaId"]!!

    private val _media = MutableStateFlow<Media?>(null)
    val media: StateFlow<Media?> = _media.asStateFlow()

    /** Скачивание с облака в редактор: 0f..1f, null — не идёт. */
    private val _loadProgress = MutableStateFlow<Float?>(null)
    val loadProgress: StateFlow<Float?> = _loadProgress.asStateFlow()

    private val _tab = MutableStateFlow(EditorTab.TRANSFORM)
    val tab: StateFlow<EditorTab> = _tab.asStateFlow()

    private val _edit = MutableStateFlow(EditState())
    val edit: StateFlow<EditState> = _edit.asStateFlow()

    private val _openedEdit = MutableStateFlow(EditState())
    val openedEdit: StateFlow<EditState> = _openedEdit.asStateFlow()

    private val undoStack = ArrayDeque<EditState>()
    private val redoStack = ArrayDeque<EditState>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _stickerCategories = MutableStateFlow(stickers.categories())
    val stickerCategories: StateFlow<List<StickerCategory>> = _stickerCategories.asStateFlow()

    private val _selectedStickerId = MutableStateFlow<Long?>(null)
    val selectedStickerId: StateFlow<Long?> = _selectedStickerId.asStateFlow()
    private val _selectedTextId = MutableStateFlow<Long?>(null)
    val selectedTextId: StateFlow<Long?> = _selectedTextId.asStateFlow()
    private val _textTyping = MutableStateFlow(false)
    val textTyping: StateFlow<Boolean> = _textTyping.asStateFlow()

    private val _addMode = MutableStateFlow(1) // 0 draw, 1 stickers, 2 text
    val addMode: StateFlow<Int> = _addMode.asStateFlow()

    private val _draw = MutableStateFlow(DrawSettings())
    val drawSettings: StateFlow<DrawSettings> = _draw.asStateFlow()
    private val _shapeId = MutableStateFlow<Long?>(null)
    val selectedShapeId: StateFlow<Long?> = _shapeId.asStateFlow()

    private var nextOverlayId = 1L

    /** Снимок перед live-правкой (стикер/trim), чтобы undo взял состояние до жеста. */
    private var liveEditBaseline: EditState? = null

    init {
        viewModelScope.launchAndCatch {
            val m = withContext(Dispatchers.IO) { mediaDao.getById(mediaId) } // Room
            val withUri = if (m == null) {
                null
            } else {
                val remote = m.storageType != StorageType.LOCAL
                if (remote) {
                    _loadProgress.value = 0f
                    val local = runCatching {
                        mediaService.resolveLocalEditUri(m) { p -> _loadProgress.value = p }
                    }.getOrNull()
                    val resolved = if (local != null) {
                        m.withEditorImageSize(local)
                    } else {
                        // Кэш недоступен / слишком большой — стрим по сети.
                        val source = mediaService.resolveEditorSourceUri(m) ?: m.uri
                        if (source != null) m.withEditorImageSize(source) else m
                    }
                    _loadProgress.value = null
                    resolved
                } else {
                    val source = mediaService.resolveEditorSourceUri(m) ?: m.uri
                    if (source != null) m.withEditorImageSize(source) else m
                }
            }
            _media.value = withUri
            val isVideo = withUri?.video != null
            _tab.value = EditorTab.forMedia(isVideo).first()
            if (isVideo && withUri != null) {
                val initial = EditState(trimEndMs = withUri.video?.duration?.takeIf { d -> d > 0 })
                _edit.value = initial
                _openedEdit.value = initial
            }
        }
    }

    /** URI + размер как на экране (EXIF), иначе альбомное жмётся в портрет. */
    private fun Media.withEditorImageSize(uri: Uri): Media {
        if (video != null) return copy(uri = uri)
        val probe = ExifTakenAt.probeUri(context, uri)
        val nextImage = if (probe.width > 0 && probe.height > 0) {
            Image(width = probe.width, height = probe.height, orientation = probe.orientation)
        } else {
            image
        }
        return copy(uri = uri, image = nextImage)
    }

    fun selectTab(tab: EditorTab) {
        _tab.value = tab
        if (tab != EditorTab.ADD) {
            _selectedStickerId.value = null
            _selectedTextId.value = null
            _textTyping.value = false
            endLiveEdit()
        }
    }

    fun setAddMode(mode: Int) {
        _addMode.value = mode
        if (mode != 1) _selectedStickerId.value = null
        if (mode != 2) {
            _selectedTextId.value = null
            _textTyping.value = false
            endLiveEdit()
        }
    }

    fun selectSticker(id: Long?) {
        _selectedStickerId.value = id
    }

    fun refreshStickers() {
        _stickerCategories.value = stickers.categories()
    }

    fun setStickerCategoryOrder(ids: List<String>) {
        stickers.setCategoryOrder(ids)
        refreshStickers()
    }

    private fun near(a: StrokePoint, b: StrokePoint, radius: Float): Boolean {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return dx * dx + dy * dy <= radius * radius
    }

    private fun cutStroke(
        stroke: DrawStroke,
        path: List<StrokePoint>,
        radius: Float
    ): List<DrawStroke> {
        if (stroke.style.isShape()) {
            val hit = path.any { shapeHit(stroke, it, radius) }
            return if (hit) emptyList() else listOf(stroke)
        }
        val parts = mutableListOf<DrawStroke>()
        val run = mutableListOf<StrokePoint>()
        stroke.points.forEach { point ->
            val hit = path.any { near(point, it, radius) }
            if (hit) {
                if (run.size >= 2) parts += stroke.copy(points = run.toList())
                run.clear()
            } else {
                run += point
            }
        }
        if (run.size >= 2) parts += stroke.copy(points = run.toList())
        return parts
    }

    private fun commit(block: (EditState) -> EditState) {
        val prev = _edit.value
        val next = block(prev)
        if (next == prev) return
        undoStack.addLast(prev)
        while (undoStack.size > 40) undoStack.removeFirst()
        redoStack.clear()
        _edit.value = next
        updateHistoryFlags()
    }

    private fun updateHistoryFlags() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(_edit.value)
        _edit.value = prev
        updateHistoryFlags()
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(_edit.value)
        _edit.value = next
        updateHistoryFlags()
    }

    fun revert() {
        if (_edit.value == EditState()) return
        commit { EditState(trimEndMs = _media.value?.video?.duration?.takeIf { it > 0 }) }
    }

    fun rotate90() = commit { state ->
        state.copy(
            rotationDegrees = (state.rotationDegrees + 270) % 360,
            crop = state.crop?.rotatedCounterClockwise(),
        )
    }

    fun rotateClockwise() = commit { state ->
        state.copy(
            rotationDegrees = (state.rotationDegrees + 90) % 360,
            crop = state.crop?.rotatedClockwise(),
        )
    }

    fun flipHorizontal() = commit { state ->
        state.copy(
            flipHorizontal = !state.flipHorizontal,
            crop = state.crop?.mirroredHorizontally(),
        )
    }

    fun flipVertical() = commit { state ->
        state.copy(
            flipVertical = !state.flipVertical,
            crop = state.crop?.mirroredVertically(),
        )
    }

    fun setCrop(crop: CropRect?) =
        commit { it.copy(crop = crop?.takeUnless { rect -> rect.isFull() }) }

    private var cropBeforeDrag: EditState? = null

    fun beginCropDrag() {
        if (cropBeforeDrag == null) cropBeforeDrag = _edit.value
    }

    fun updateCrop(crop: CropRect) {
        _edit.update { it.copy(crop = crop) }
    }

    fun endCropDrag() {
        val start = cropBeforeDrag ?: return
        cropBeforeDrag = null
        val cropped = _edit.value.crop?.takeUnless { it.isFull() }
        val now = _edit.value.copy(crop = cropped)
        _edit.value = now
        if (start == now) return
        undoStack.addLast(start)
        while (undoStack.size > 40) undoStack.removeFirst()
        redoStack.clear()
        updateHistoryFlags()
    }
    fun setPerspective(quad: PerspectiveQuad?, recordHistory: Boolean = true) {
        val next =
            { s: EditState -> s.copy(perspective = quad?.takeUnless { q -> q.isIdentity() }) }
        if (recordHistory) commit(next) else _edit.update(next)
    }

    fun resetPerspective() = commit { it.copy(perspective = null) }
    fun setFilter(filter: FilterPreset) = commit { it.copy(filter = filter) }
    fun setAdjust(adjust: AdjustParams) = commit { it.copy(adjust = adjust) }

    fun applyAiEnhance() = commit {
        it.copy(
            adjust = it.adjust.copy(
                contrast = (it.adjust.contrast + 0.15f).coerceIn(-1f, 1f),
                saturation = (it.adjust.saturation + 0.12f).coerceIn(-1f, 1f),
                brightness = (it.adjust.brightness + 0.05f).coerceIn(-1f, 1f),
            )
        )
    }

    fun setDraw(settings: DrawSettings) {
        val previous = _draw.value
        _draw.value = settings
        if (settings.style != previous.style) _shapeId.value = null
        val id = _shapeId.value ?: return
        if (settings.slider != DrawSlider.SIZE || settings.size == previous.size) return
        _edit.update { state ->
            state.copy(
                strokes = state.strokes.map { stroke ->
                    if (stroke.id == id && stroke.style.isShape()) {
                        stroke.copy(width = brushWidth(settings))
                    } else {
                        stroke
                    }
                }
            )
        }
    }

    fun addStroke(stroke: DrawStroke) {
        val stored = stroke.copy(id = nextOverlayId++)
        commit { it.copy(strokes = it.strokes + stored) }
        _shapeId.value = if (stored.style.isShape()) stored.id else null
    }

    fun dragShapeHandle(index: Int, point: StrokePoint) {
        val id = _shapeId.value ?: return
        val size = _draw.value.size
        _edit.update { state ->
            state.copy(
                strokes = state.strokes.map { stroke ->
                    if (stroke.id == id) moveShapeHandle(stroke, index, point, size) else stroke
                }
            )
        }
    }

    private var eraseBefore: EditState? = null

    fun beginErase() {
        if (eraseBefore == null) eraseBefore = _edit.value
    }

    /** Стирает сразу под пальцем. В историю попадает один жест целиком. */
    fun eraseAlong(
        from: StrokePoint?,
        to: StrokePoint,
        radius: Float,
        wholeStroke: Boolean,
    ) {
        val samples = eraserSamples(from, to, radius)
        _edit.update { state ->
            val next = if (wholeStroke) {
                state.strokes.filter { stroke ->
                    stroke.points.none { point -> samples.any { hit -> near(point, hit, radius) } }
                }
            } else {
                state.strokes.flatMap { stroke -> cutStroke(stroke, samples, radius) }
            }
            if (next == state.strokes) state else state.copy(strokes = next)
        }
    }

    fun endErase() {
        val start = eraseBefore ?: return
        eraseBefore = null
        val now = _edit.value
        if (start == now) return
        undoStack.addLast(start)
        while (undoStack.size > 40) undoStack.removeFirst()
        redoStack.clear()
        updateHistoryFlags()
    }

    private fun eraserSamples(
        from: StrokePoint?,
        to: StrokePoint,
        radius: Float
    ): List<StrokePoint> {
        if (from == null) return listOf(to)
        val dx = to.x - from.x
        val dy = to.y - from.y
        val dist = kotlin.math.sqrt(dx * dx + dy * dy)
        val step = (radius * 0.35f).coerceAtLeast(0.003f)
        val count = (dist / step).toInt().coerceIn(1, 24)
        return List(count) { index ->
            val t = (index + 1f) / count
            StrokePoint(from.x + dx * t, from.y + dy * t)
        }
    }

    fun selectText(id: Long?) {
        _selectedTextId.value = id
    }

    fun finishTextEdit() = endLiveEdit()

    /** Пустое место — новая рамка. Нажатие на текст — правка этой рамки. */
    fun startTextInput(id: Long? = null, x: Float = 0.5f, y: Float = 0.5f) {
        if (_textTyping.value && id != null && _selectedTextId.value == id) return
        if (_textTyping.value) {
            val previous = _selectedTextId.value
            val previousItem = _edit.value.texts.find { it.id == previous }
            if (previousItem != null && previousItem.text.isBlank()) {
                _edit.update { state -> state.copy(texts = state.texts.filterNot { it.id == previous }) }
            }
            endLiveEdit()
        }
        val existing = id?.let { wanted -> _edit.value.texts.find { it.id == wanted } }
        if (existing != null) {
            _selectedTextId.value = existing.id
        } else {
            val newId = nextOverlayId++
            beginLiveEdit()
            _edit.update { state ->
                state.copy(
                    texts = state.texts + TextOverlay(
                        id = newId,
                        text = "",
                        x = x.coerceIn(0.08f, 0.92f),
                        y = y.coerceIn(0.08f, 0.92f),
                        color = _draw.value.color,
                    )
                )
            }
            _selectedTextId.value = newId
        }
        _textTyping.value = true
    }

    fun stopTextInput() {
        if (!_textTyping.value && liveEditBaseline == null) return
        _textTyping.value = false
        val id = _selectedTextId.value
        val item = _edit.value.texts.find { it.id == id }
        if (item != null && item.text.isBlank()) {
            _edit.update { state -> state.copy(texts = state.texts.filterNot { it.id == id }) }
            _selectedTextId.value = null
        }
        endLiveEdit()
    }

    fun editTextFields(
        text: String? = null,
        color: Long? = null,
        fontId: String? = null,
        styleId: String? = null,
        templateId: String? = null,
        outlined: Boolean? = null,
        live: Boolean = false,
    ) {
        val currentId = _selectedTextId.value
        val existing = _edit.value.texts.find { it.id == currentId }
        if (existing == null && text.isNullOrBlank()) return
        val createdId = if (existing == null) {
            val id = nextOverlayId++
            _selectedTextId.value = id
            id
        } else {
            existing.id
        }
        val next: (EditState) -> EditState = { state ->
            val current = state.texts.find { it.id == createdId }
            if (current == null) {
                state.copy(
                    texts = state.texts + TextOverlay(
                        id = createdId,
                        text = text.orEmpty(),
                        x = 0.5f,
                        y = 0.42f,
                        color = color ?: _draw.value.color,
                        fontId = fontId ?: "normal",
                        styleId = styleId ?: "solid",
                        templateId = templateId ?: "plain",
                        outlined = outlined ?: false,
                    )
                )
            } else {
                state.copy(
                    texts = state.texts.map { item ->
                        if (item.id != current.id) item
                        else item.copy(
                            text = text ?: item.text,
                            color = color ?: item.color,
                            fontId = fontId ?: item.fontId,
                            styleId = styleId ?: item.styleId,
                            templateId = templateId ?: item.templateId,
                            outlined = outlined ?: item.outlined,
                        )
                    }
                )
            }
        }
        if (live) {
            beginLiveEdit()
            _edit.update(next)
        } else {
            endLiveEdit()
            commit(next)
        }
    }

    fun tintSelectedText(color: Long) {
        editTextFields(color = color, styleId = "solid", live = false)
    }

    fun updateText(
        id: Long,
        x: Float? = null,
        y: Float? = null,
        scale: Float? = null,
        rotation: Float? = null,
        recordHistory: Boolean = false,
    ) {
        // Даже при recordHistory сначала применяем x/y/scale/rotation —
        // иначе финальный кадр жеста с true мог бы отбросить размер.
        if (x != null || y != null || scale != null || rotation != null) {
            beginLiveEdit()
            _edit.update { state ->
                state.copy(
                    texts = state.texts.map { item ->
                        if (item.id != id) item
                        else item.copy(
                            x = (x ?: item.x).coerceIn(0.02f, 0.98f),
                            y = (y ?: item.y).coerceIn(0.02f, 0.98f),
                            scale = (scale ?: item.scale).coerceIn(0.35f, 4f),
                            rotation = rotation ?: item.rotation,
                        )
                    }
                )
            }
        }
        if (recordHistory) endLiveEdit()
    }

    fun duplicateText(id: Long) {
        endLiveEdit()
        val src = _edit.value.texts.firstOrNull { it.id == id } ?: return
        val newId = nextOverlayId++
        commit {
            it.copy(
                texts = it.texts + src.copy(
                    id = newId,
                    x = (src.x + 0.06f).coerceIn(0.02f, 0.98f),
                    y = (src.y + 0.06f).coerceIn(0.02f, 0.98f),
                )
            )
        }
        _selectedTextId.value = newId
    }

    fun deleteText(id: Long) {
        endLiveEdit()
        commit { it.copy(texts = it.texts.filterNot { item -> item.id == id }) }
        if (_selectedTextId.value == id) _selectedTextId.value = null
    }

    fun addSticker(category: StickerCategory, item: StickerItem) {
        val id = nextOverlayId++
        commit {
            it.copy(
                stickers = it.stickers + StickerOverlay(
                    id = id,
                    categoryId = category.id,
                    stickerId = item.id,
                    emoji = item.emoji,
                    x = 0.5f,
                    y = 0.5f,
                )
            )
        }
        _addMode.value = 1
        _selectedStickerId.value = id
    }

    fun updateSticker(
        id: Long,
        x: Float? = null,
        y: Float? = null,
        scale: Float? = null,
        rotation: Float? = null,
        recordHistory: Boolean = false,
    ) {
        if (x != null || y != null || scale != null || rotation != null) {
            beginLiveEdit()
            _edit.update { s ->
                s.copy(
                    stickers = s.stickers.map { sticker ->
                        if (sticker.id != id) sticker
                        else sticker.copy(
                            x = (x ?: sticker.x).coerceIn(0.02f, 0.98f),
                            y = (y ?: sticker.y).coerceIn(0.02f, 0.98f),
                            scale = (scale ?: sticker.scale).coerceIn(0.35f, 4f),
                            rotation = rotation ?: sticker.rotation,
                        )
                    }
                )
            }
        }
        if (recordHistory) endLiveEdit()
    }

    fun duplicateSticker(id: Long) {
        val src = _edit.value.stickers.firstOrNull { it.id == id } ?: return
        val newId = nextOverlayId++
        commit {
            it.copy(
                stickers = it.stickers + src.copy(
                    id = newId,
                    x = (src.x + 0.06f).coerceIn(0.02f, 0.98f),
                    y = (src.y + 0.06f).coerceIn(0.02f, 0.98f),
                )
            )
        }
        _selectedStickerId.value = newId
    }

    fun flipSticker(id: Long) = commit {
        it.copy(
            stickers = it.stickers.map { sticker ->
                if (sticker.id != id) sticker
                else sticker.copy(flipHorizontal = !sticker.flipHorizontal)
            }
        )
    }

    fun deleteSticker(id: Long) {
        commit { it.copy(stickers = it.stickers.filterNot { s -> s.id == id }) }
        if (_selectedStickerId.value == id) _selectedStickerId.value = null
    }

    fun setTrim(startMs: Long, endMs: Long, recordHistory: Boolean = true) {
        if (recordHistory) {
            endLiveEdit()
            return
        }
        beginLiveEdit()
        _edit.update {
            it.copy(trimStartMs = startMs.coerceAtLeast(0), trimEndMs = endMs)
        }
    }

    private fun beginLiveEdit() {
        if (liveEditBaseline == null) liveEditBaseline = _edit.value
    }

    private fun endLiveEdit() {
        val base = liveEditBaseline ?: return
        liveEditBaseline = null
        if (_edit.value == base) return
        undoStack.addLast(base)
        while (undoStack.size > 40) undoStack.removeFirst()
        redoStack.clear()
        updateHistoryFlags()
    }

    fun setOriginalVolume(volume: Float) = commit {
        it.copy(originalVolume = volume.coerceIn(0f, 1f), muted = volume <= 0.001f)
    }

    fun setMuted(muted: Boolean) = commit {
        it.copy(
            muted = muted,
            originalVolume = if (muted) 0f else it.originalVolume.coerceAtLeast(0.5f)
        )
    }

    fun setSoundtrack(uri: Uri?) = commit { it.copy(soundtrackUri = uri) }
    fun setSoundtrackVolume(volume: Float) = commit {
        it.copy(soundtrackVolume = volume.coerceIn(0f, 1f))
    }

    /**
     * @param onDone id новой копии, 0 если файл записан, но строка в базе не найдена, null если сохранить не удалось.
     */
    fun save(
        albumId: Long?,
        favorite: Boolean,
        onDone: (Long?) -> Unit,
    ) = viewModelScope.launchAndCatch {
        if (_edit.value == _openedEdit.value) {
            MessageBus.emit(context.getString(R.string.editor_no_changes))
            onDone(null)
            return@launchAndCatch
        }
        val m = _media.value ?: run {
            onDone(null)
            return@launchAndCatch
        }
        _saving.value = true
        try {
            // Облако → локальный файл; сохранение всегда в Pictures/Movies телефона.
            val uri = mediaService.resolveLocalEditUri(m) ?: run {
                MessageBus.emit(context.getString(R.string.editor_save_failed))
                onDone(null)
                return@launchAndCatch
            }
            // Из облака или из альбома хранилища («Главная» и т.п.) — новый файл в Pictures, текущие даты.
            val fromRemoteMedia = m.storageType != StorageType.LOCAL
            val fromRemoteAlbum = albumId?.let { mediaService.isRemoteStorageAlbum(it) } == true
            val asNewCapture = fromRemoteMedia || fromRemoteAlbum
            val saved = if (m.video != null) {
                export.exportVideo(uri, _edit.value, m, asNewCapture = asNewCapture)
            } else {
                val bmp = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it)
                    }
                } ?: run {
                    MessageBus.emit(context.getString(R.string.editor_save_failed))
                    onDone(null)
                    return@launchAndCatch
                }
                val out = withContext(Dispatchers.Default) { export.renderPhoto(bmp, _edit.value) }
                withContext(Dispatchers.IO) {
                    export.saveJpeg(out, m, asNewCapture = asNewCapture)
                }
            }
            if (saved == null) {
                MessageBus.emit(context.getString(R.string.editor_save_failed))
                onDone(null)
                return@launchAndCatch
            }
            val copyId = runCatching {
                mediaService.indexSavedCopy(
                    uri = saved,
                    storageId = m.storageId,
                    albumId = albumId,
                    favorite = favorite,
                    stampNow = asNewCapture,
                )
            }.getOrDefault(0L)
            MessageBus.emit(context.getString(R.string.editor_saved))
            onDone(copyId)
        } finally {
            _saving.value = false
        }
    }
}
