package com.asinosoft.gallery.model

import androidx.lifecycle.ViewModel
import com.asinosoft.gallery.data.PrivateAlbum
import com.asinosoft.gallery.data.Trash
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
