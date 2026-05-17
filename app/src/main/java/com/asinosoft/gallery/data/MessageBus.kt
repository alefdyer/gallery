package com.asinosoft.gallery.data

import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

object MessageBus {
    private val messagesFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = messagesFlow

    val context = CoroutineExceptionHandler { _, throwable ->
        Log.w("message", throwable.toString())
        messagesFlow.tryEmit(throwable.toString())
    }
}

fun CoroutineScope.launchAndCatch(block: suspend CoroutineScope.() -> Unit) =
    launch(MessageBus.context, block = block)
