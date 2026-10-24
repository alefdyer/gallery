package com.asinosoft.photogallery.model

import androidx.lifecycle.ViewModel
import com.asinosoft.photogallery.data.PrivateAlbum
import com.asinosoft.photogallery.data.Trash
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject

@HiltViewModel
class PrivateMovesViewModel @Inject constructor(
    privateAlbum: PrivateAlbum,
    trash: Trash
) : ViewModel() {
    val movedIn: SharedFlow<Int> = privateAlbum.movedIn
    val trashed: SharedFlow<Int> = trash.trashed
}
