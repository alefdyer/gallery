package com.asinosoft.gallery.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

object MessageBus {
    private val messagesFlow = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = messagesFlow

    fun dispatch(message: String) {
        messagesFlow.tryEmit(message)
    }
}
