package com.asinosoft.photogallery.data

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * На Android одновременно два LibVLC (плеер + sout-конвертация) ломают video out:
 * после шаринга остаётся звук и чёрный экран. Держим эксклюзивный доступ.
 */
object LibVlcCoordinator {
    private val mutex = Mutex()
    private val playbackHeld = AtomicBoolean(false)
    private val _generation = MutableStateFlow(0)
    val generation: StateFlow<Int> = _generation.asStateFlow()

    /** true = идёт share-транскод, плеер не создавать. */
    private val _exclusive = MutableStateFlow(false)
    val exclusive: StateFlow<Boolean> = _exclusive.asStateFlow()

    fun onPlaybackAcquired() {
        playbackHeld.set(true)
    }

    fun onPlaybackReleased() {
        playbackHeld.set(false)
    }

    /**
     * Останавливает плеер, ждёт отпускание, выполняет [block],
     * затем разрешает пересоздание плеера.
     */
    suspend fun <T> withExclusive(block: suspend () -> T): T = mutex.withLock {
        _exclusive.value = true
        _generation.value = _generation.value + 1
        var waits = 0
        while (playbackHeld.get() && waits < 80) {
            delay(50)
            waits++
        }
        // Дать Compose отменить LaunchedEffect и дойти до suspend (иначе getTime на released).
        delay(150)
        try {
            block()
        } finally {
            _exclusive.value = false
            _generation.value = _generation.value + 1
        }
    }
}
