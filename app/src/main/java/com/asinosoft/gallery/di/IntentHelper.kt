package com.asinosoft.gallery.di

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object IntentHelper : DefaultLifecycleObserver {
    private var launcher: ActivityResultLauncher<IntentSenderRequest>? = null
    private var resultCallback: (suspend (Boolean) -> Unit)? = null

    override fun onCreate(owner: LifecycleOwner) {
        launcher = (owner as ComponentActivity).activityResultRegistry.register(
            "key",
            owner,
            ActivityResultContracts.StartIntentSenderForResult()
        ) {
            val callback = resultCallback
            resultCallback = null
            CoroutineScope(Dispatchers.Main).launch {
                callback?.invoke(Activity.RESULT_OK == it.resultCode)
            }
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        launcher = null
    }

    fun delete(uris: Collection<Uri>, context: Context, callback: suspend () -> Unit) =
        deleteRequest(uris, context) { granted -> if (granted) callback() }

    fun deleteRequest(uris: Collection<Uri>, context: Context, onResult: suspend (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            launch(MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender, onResult)
        } else {
            throw NotImplementedError()
        }
    }

    fun launch(sender: IntentSender, onResult: suspend (Boolean) -> Unit) {
        resultCallback = onResult
        launcher?.launch(
            IntentSenderRequest
                .Builder(sender)
                .setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION, 0)
                .build()
        )
    }
}
