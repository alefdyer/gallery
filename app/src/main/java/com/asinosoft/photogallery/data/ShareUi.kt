package com.asinosoft.photogallery.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ShareStatus(
    val message: String,
    /** 0f..1f или null — неизвестный/бесконечный прогресс. */
    val progress: Float? = null,
)

/** Глобальный статус подготовки share — плашка в UI вместо snackbar. */
object ShareUi {
    private val _status = MutableStateFlow<ShareStatus?>(null)
    val status: StateFlow<ShareStatus?> = _status.asStateFlow()

    fun setStatus(message: String?, progress: Float? = null) {
        _status.value = message?.let { ShareStatus(it, progress?.coerceIn(0f, 1f)) }
    }

    fun setProgress(progress: Float?) {
        _status.update { current ->
            current?.copy(progress = progress?.coerceIn(0f, 1f))
        }
    }

    val isBusy: Boolean
        get() = _status.value != null
}
