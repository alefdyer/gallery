package com.asinosoft.gallery.ui.component

import android.os.SystemClock
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.data.storage.StorageType
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Поочерёдно проигрывает превью видео, полностью видимых в сетке
 */
class VideoPreviewQueue {
    var previewId by mutableStateOf<Long?>(null)
        internal set

    internal val ended = Channel<Long>(Channel.CONFLATED)

    fun onEnded(media: Media) {
        ended.trySend(media.id)
    }
}

@Composable
fun rememberVideoPreviewQueue(items: List<Media>, state: LazyGridState): VideoPreviewQueue {
    val queue = remember { VideoPreviewQueue() }
    LaunchedEffect(items, state) {
        val appearedAt = mutableMapOf<Long, Long>()
        snapshotFlow {
            val layoutInfo = state.layoutInfo
            val visibleVideos = layoutInfo.visibleItemsInfo
                .filter {
                    it.offset.y >= layoutInfo.viewportStartOffset &&
                        it.offset.y + it.size.height <= layoutInfo.viewportEndOffset
                }
                .mapNotNull { items.getOrNull(it.index) }
                .filter { null != it.video && it.storageType == StorageType.LOCAL }
                .map { it.id }
            visibleVideos to state.isScrollInProgress
        }.collectLatest { (visibleVideos, isScrolling) ->
            val now = SystemClock.uptimeMillis()
            appearedAt.keys.retainAll(visibleVideos.toSet())
            visibleVideos.forEach { appearedAt.getOrPut(it) { now } }

            if (isScrolling || visibleVideos.isEmpty()) {
                if (queue.previewId !in visibleVideos) queue.previewId = null
                return@collectLatest
            }

            var index = visibleVideos.indexOf(queue.previewId)
            if (index < 0) {
                queue.previewId = null
                index = 0
                val wait = appearedAt.getValue(visibleVideos[0]) + VIDEO_PREVIEW_DELAY_MS - now
                if (wait > 0) delay(wait)
            }

            var restart = false
            while (true) {
                val id = visibleVideos[index]
                if (restart || queue.previewId != id) {
                    if (queue.previewId == id) {
                        queue.previewId = null
                        withFrameNanos { }
                    }
                    while (queue.ended.tryReceive().isSuccess) Unit
                    queue.previewId = id
                }
                withTimeoutOrNull(VIDEO_PREVIEW_MAX_MS) {
                    while (queue.ended.receive() != id) Unit
                }
                index = (index + 1) % visibleVideos.size
                restart = true
            }
        }
    }
    return queue
}

private const val VIDEO_PREVIEW_MAX_MS = 10_000L
private const val VIDEO_PREVIEW_DELAY_MS = 2000L
