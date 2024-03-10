package com.asinosoft.gallery.model

import androidx.lifecycle.ViewModel
import com.asinosoft.gallery.data.Image
import com.asinosoft.gallery.data.ImageDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val imageDao: ImageDao,
) : ViewModel() {
    val images: Flow<List<Image>> get() = imageDao.getImages()
}
