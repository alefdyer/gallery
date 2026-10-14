package com.asinosoft.gallery.model

import androidx.lifecycle.ViewModel
import com.asinosoft.gallery.data.PrivateAlbum
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject

@HiltViewModel
class PrivateMovesViewModel @Inject constructor(
    privateAlbum: PrivateAlbum
) : ViewModel() {
    val movedIn: SharedFlow<Int> = privateAlbum.movedIn
}
