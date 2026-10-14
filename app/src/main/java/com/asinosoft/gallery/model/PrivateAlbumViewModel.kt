package com.asinosoft.gallery.model

import androidx.lifecycle.viewModelScope
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.data.PrivateAlbum
import com.asinosoft.gallery.data.launchAndCatch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PrivateAlbumViewModel @Inject constructor(
    private val privateAlbum: PrivateAlbum
) : MediaFolderViewModel(privateAlbum.media) {
    override fun restore(items: Collection<Media>) {
        viewModelScope.launchAndCatch { privateAlbum.restore(items) }
    }

    override fun delete(items: Collection<Media>) {
        viewModelScope.launchAndCatch { privateAlbum.delete(items) }
    }
}
