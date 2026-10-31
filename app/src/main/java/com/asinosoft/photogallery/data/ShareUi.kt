package com.asinosoft.photogallery.data

import kotlinx.coroutines.Job
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

    @Volatile
    private var activeJob: Job? = null

    /** Растёт при cancel — устаревший download/convert не должен снова поднять плашку. */
    @Volatile
    private var session: Int = 0

    private var jobSession: Int = 0

    fun bindJob(job: Job) {
        jobSession = session
        activeJob = job
    }

    /** Отмена текущей подготовки (уход с видео / кнопка «Отмена»). */
    fun cancel() {
        session++
        val job = activeJob
        activeJob = null
        job?.cancel()
        _status.value = null
    }

    fun setStatus(message: String?, progress: Float? = null) {
        if (message != null && jobSession != session) return
        _status.value = message?.let { ShareStatus(it, progress?.coerceIn(0f, 1f)) }
        if (message == null) {
            activeJob = null
        }
    }

    fun setProgress(progress: Float?) {
        if (jobSession != session) return
        _status.update { current ->
            current?.copy(progress = progress?.coerceIn(0f, 1f))
        }
    }

    val isBusy: Boolean
        get() = _status.value != null
}
