package com.asinosoft.photogallery.model

import com.asinosoft.photogallery.data.Media
import com.asinosoft.photogallery.data.Trash
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val trash: Trash
) : MediaFolderViewModel(trash.media) {
    val isSupported = trash.isSupported

    fun refresh() = trash.refresh()

    override fun restore(items: Collection<Media>) = trash.restore(items)

    override fun delete(items: Collection<Media>) = trash.delete(items)
}
