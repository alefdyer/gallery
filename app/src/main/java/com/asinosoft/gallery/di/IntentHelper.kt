package com.asinosoft.gallery.di

import android.app.Activity
import android.content.Context
import android.content.Intent
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
    private var delete: ActivityResultLauncher<IntentSenderRequest>? = null
    private var deleteCallback: (suspend () -> Unit)? = null

    override fun onCreate(owner: LifecycleOwner) {
        delete = (owner as ComponentActivity).activityResultRegistry.register(
            "key",
            owner,
            ActivityResultContracts.StartIntentSenderForResult()
        ) {
            CoroutineScope(Dispatchers.Main).launch {
                launch {
                    if (Activity.RESULT_OK == it.resultCode) {
                        deleteCallback?.invoke()
                    }
                }
            }
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        delete = null
    }

    fun delete(uris: Collection<Uri>, context: Context, callback: suspend () -> Unit) {
        deleteCallback = callback

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val sender =
                MediaStore
                    .createDeleteRequest(
                        context.contentResolver,
                        uris
                    ).intentSender

            val request =
                IntentSenderRequest
                    .Builder(sender)
                    .setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION, 0)
                    .build()

            delete?.launch(request)
        } else {
            throw NotImplementedError()
        }
    }
}
