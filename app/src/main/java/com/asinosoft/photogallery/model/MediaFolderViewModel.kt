package com.asinosoft.photogallery.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asinosoft.photogallery.data.Media
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * Папка с файлами вне основной галереи (личная папка, корзина):
 * фильтры по дате и типу, выделение, возврат и удаление
 */
abstract class MediaFolderViewModel(source: Flow<List<Media>>) : ViewModel() {
    val media: StateFlow<List<Media>?> = source
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val dateFilter = MutableStateFlow<DateFilter?>(null)
    val showPhotos = MutableStateFlow(true)
    val showVideos = MutableStateFlow(true)

    private val datedMedia: StateFlow<List<Media>> = combine(media, dateFilter) { items, filter ->
        items.orEmpty().filter { filter?.matches(it) ?: true }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Файлы с учётом фильтров по дате и по типу */
    val visibleMedia: StateFlow<List<Media>> =
        combine(datedMedia, showPhotos, showVideos) { items, photos, videos ->
            val hasBothTypes = items.any { null == it.video } && items.any { null != it.video }
            if (!hasBothTypes || (photos && videos)) items
            else items.filter { if (null == it.video) photos else videos }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val photoCount: StateFlow<Int> = datedMedia.map { list -> list.count { null == it.video } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val videoCount: StateFlow<Int> = datedMedia.map { list -> list.count { null != it.video } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val dateGroups: StateFlow<Map<Int, Map<Int, Map<Int, Int>>>> = media.map { items ->
        items.orEmpty()
            .groupBy { it.date.year }
            .mapValues { (_, yearItems) ->
                yearItems.groupBy { it.date.monthValue }
                    .mapValues { (_, monthItems) ->
                        monthItems.groupBy { it.date.dayOfMonth }.mapValues { it.value.size }
                    }
            }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    val selection: StateFlow<Set<Long>> = _selection

    abstract fun restore(items: Collection<Media>)

    abstract fun delete(items: Collection<Media>)

    fun setDateFilter(filter: DateFilter?) {
        dateFilter.value = filter
    }

    fun togglePhotos() {
        val onlyPhotos = showPhotos.value && !showVideos.value
        showPhotos.value = true
        showVideos.value = onlyPhotos
    }

    fun toggleVideos() {
        val onlyVideos = showVideos.value && !showPhotos.value
        showVideos.value = true
        showPhotos.value = onlyVideos
    }

    fun setSelection(ids: Set<Long>) {
        _selection.value = ids
    }

    fun toggleSelection(media: Media) = _selection.update {
        if (media.id in it) it - media.id else it + media.id
    }

    fun clearSelection() {
        _selection.value = emptySet()
    }

    fun restoreSelection() = restore(takeSelection())

    fun deleteSelection() = delete(takeSelection())

    private fun takeSelection(): List<Media> {
        val ids = _selection.value
        clearSelection()
        return media.value.orEmpty().filter { it.id in ids }
    }
}

private fun DateFilter.matches(media: Media) =
    (year == null || media.date.year == year) &&
        (month == null || media.date.monthValue == month) &&
        (day == null || media.date.dayOfMonth == day)
