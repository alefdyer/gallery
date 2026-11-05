package com.asinosoft.photogallery.data.storage.webdav

import android.util.Log
import com.asinosoft.photogallery.data.storage.FolderScanState
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient

/** Depth 0 по всем папкам сразу. У каждой свой запрос, ответы приходят параллельно. */
object DavDepthStamp {
    suspend fun load(
        client: OkHttpClient,
        login: String?,
        password: String?,
        urls: List<String>,
    ): Map<String, String> {
        if (urls.isEmpty()) return emptyMap()
        val started = System.currentTimeMillis()
        return coroutineScope {
            urls.map { url ->
                async(Dispatchers.IO) {
                    val sardine = OkHttpSardine(client).apply {
                        setCredentials(login, password)
                    }
                    val stamp = try {
                        FolderScanState.dirStamp(sardine.list(url, 0).firstOrNull())
                    } catch (ex: Throwable) {
                        Log.e(TAG, "Stamp failed: $url", ex)
                        throw ex
                    }
                    url to stamp
                }
            }.awaitAll().toMap()
        }.also {
            Log.i(
                TAG,
                "Stamp check ${urls.size} folders in ${System.currentTimeMillis() - started} ms"
            )
        }
    }

    private const val TAG = "dav"
}
