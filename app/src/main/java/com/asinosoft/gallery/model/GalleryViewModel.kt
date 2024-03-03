package com.asinosoft.gallery.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asinosoft.gallery.data.Image
import com.asinosoft.gallery.data.ImageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.util.logging.Logger
import javax.inject.Inject
import kotlin.system.measureTimeMillis

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val repository: ImageRepository,
) : ViewModel() {
    private val _images = MutableStateFlow<List<Image>?>(null)
    val images: Flow<List<Image>> get() = _images.filterNotNull()

    init {
        refreshData()
    }

    private fun refreshData() {
        Logger.getLogger("app").info("refresh image list")
        viewModelScope.launch {
            try {
                val time = measureTimeMillis {
                    _images.value = repository.findAll()
                }
                Logger.getLogger("app").info("image list refreshed in $time ms")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
