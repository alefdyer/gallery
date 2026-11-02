package com.asinosoft.photogallery.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asinosoft.photogallery.R
import com.asinosoft.photogallery.data.Media
import com.asinosoft.photogallery.data.MediaDao
import com.asinosoft.photogallery.data.MessageBus
import com.asinosoft.photogallery.data.editor.EditorExport
import com.asinosoft.photogallery.data.editor.StickerCatalog
import com.asinosoft.photogallery.data.editor.StickerCategory
import com.asinosoft.photogallery.data.editor.StickerItem
import com.asinosoft.photogallery.data.launchAndCatch
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
    private val export: EditorExport,
    private val stickers: StickerCatalog,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {
    private val mediaId: Long = state["mediaId"]!!

    private val _media = MutableStateFlow<Media?>(null)
    val media: StateFlow<Media?> = _media.asStateFlow()

    private val _tab = MutableStateFlow(EditorTab.TRANSFORM)
    val tab: StateFlow<EditorTab> = _tab.asStateFlow()

    private val _edit = MutableStateFlow(EditState())
    val edit: StateFlow<EditState> = _edit.asStateFlow()

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

    private val _addMode = MutableStateFlow(1) // 0 draw, 1 stickers, 2 text
    val addMode: StateFlow<Int> = _addMode.asStateFlow()

    private var nextOverlayId = 1L

    /** Снимок перед live-правкой (стикер/trim), чтобы undo взял состояние до жеста. */
    private var liveEditBaseline: EditState? = null

    init {
        viewModelScope.launchAndCatch {
            val m = withContext(Dispatchers.IO) { mediaDao.getById(mediaId) } // Room
            _media.value = m
            val isVideo = m?.video != null
            _tab.value = EditorTab.forMedia(isVideo).first()
            if (isVideo && m != null) {
                _edit.update {
                    it.copy(trimEndMs = m.video?.duration?.takeIf { d -> d > 0 })
                }
            }
        }
    }

    fun selectTab(tab: EditorTab) {
        _tab.value = tab
        if (tab != EditorTab.ADD) _selectedStickerId.value = null
    }

    fun setAddMode(mode: Int) {
        _addMode.value = mode
        if (mode == 0) _selectedStickerId.value = null
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

    fun rotate90() = commit { it.copy(rotationDegrees = (it.rotationDegrees + 90) % 360) }
    fun flipHorizontal() = commit { it.copy(flipHorizontal = !it.flipHorizontal) }
    fun setCrop(crop: CropRect?) = commit { it.copy(crop = crop) }
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

    fun addStroke(stroke: DrawStroke) = commit { it.copy(strokes = it.strokes + stroke) }

    fun addText(text: String, x: Float = 0.5f, y: Float = 0.5f) = commit {
        it.copy(
            texts = it.texts + TextOverlay(
                id = nextOverlayId++,
                text = text,
                x = x,
                y = y,
            )
        )
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
        if (recordHistory) {
            endLiveEdit()
            return
        }
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

    fun save(onDone: (Boolean) -> Unit) = viewModelScope.launchAndCatch {
        val m = _media.value ?: run {
            onDone(false)
            return@launchAndCatch
        }
        val uri = m.uri ?: run {
            onDone(false)
            return@launchAndCatch
        }
        _saving.value = true
        try {
            val saved = if (m.video != null) {
                export.exportVideo(uri, _edit.value, m.filename.ifBlank { "video.mp4" })
            } else {
                val bmp = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it)
                    }
                } ?: run {
                    MessageBus.emit(context.getString(R.string.editor_save_failed))
                    onDone(false)
                    return@launchAndCatch
                }
                val out = withContext(Dispatchers.Default) { export.renderPhoto(bmp, _edit.value) }
                withContext(Dispatchers.IO) {
                    export.saveJpeg(out, m.filename.ifBlank { "photo.jpg" })
                }
            }
            if (saved != null) {
                MessageBus.emit(context.getString(R.string.editor_saved))
                onDone(true)
            } else {
                MessageBus.emit(context.getString(R.string.editor_save_failed))
                onDone(false)
            }
        } finally {
            _saving.value = false
        }
    }
}
