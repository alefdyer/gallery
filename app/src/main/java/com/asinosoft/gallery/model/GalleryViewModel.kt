package com.asinosoft.gallery.model

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asinosoft.gallery.data.Image
import com.asinosoft.gallery.data.ImageDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

const val TAG = "gallery.viewmodel"

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val imageDao: ImageDao,
) : ViewModel() {
    init {
        viewModelScope.launch {
            imageDao.getImages().collect {
                Log.d(TAG, "images: ${it.count()}")
                _images.emit(it)
            }
        }
    }

    private val _images = MutableStateFlow<List<Image>>(emptyList())

    val images: StateFlow<List<Image>>
        get() = _images
}
