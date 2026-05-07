package com.asinosoft.gallery

import android.net.Uri
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

object OAuthRedirectBus {
    private val _events = MutableSharedFlow<Uri>(extraBufferCapacity = 1)
    val events: SharedFlow<Uri> = _events

    fun publish(uri: Uri) {
        _events.tryEmit(uri)
    }
}
