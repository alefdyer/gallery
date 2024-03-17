package com.asinosoft.gallery.model

import androidx.lifecycle.ViewModel
import com.asinosoft.gallery.data.Image
import com.asinosoft.gallery.data.ImageDao
import com.asinosoft.gallery.data.PreviewFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val imageDao: ImageDao,
    private val previewFactory: PreviewFactory,
) : ViewModel() {
    val images: Flow<List<Image>> get() = imageDao.getImages()

    fun preview(image: Image): String = previewFactory.preview(image)
}
