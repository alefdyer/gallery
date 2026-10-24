package com.asinosoft.photogallery.model

import androidx.lifecycle.viewModelScope
import com.asinosoft.photogallery.data.Media
import com.asinosoft.photogallery.data.PrivateAlbum
import com.asinosoft.photogallery.data.launchAndCatch
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
