package com.asinosoft.photogallery.data.storage.webdav

import android.util.Log
import com.asinosoft.photogallery.data.storage.FolderScanState
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient

/** Depth 0 по всем папкам сразу. Папки без ответа спрашиваются вторым пакетом. */
object DavDepthStamp {
    suspend fun load(
        client: OkHttpClient,
        login: String?,
        password: String?,
        urls: List<String>,
    ): Map<String, String> {
        if (urls.isEmpty()) return emptyMap()
        val started = System.currentTimeMillis()
        val first = query(client, login, password, urls)
        val missing = urls.filter { it !in first.stamps }
        val second = if (missing.isEmpty()) {
            QueryResult(emptyMap(), null)
        } else {
            Log.i(TAG, "Stamp retry ${missing.size} folders")
            query(client, login, password, missing)
        }
        val stamps = first.stamps + second.stamps
        Log.i(
            TAG,
            "Stamp check ${urls.size} folders, answered ${stamps.size} " +
                    "in ${System.currentTimeMillis() - started} ms",
        )
        if (stamps.isEmpty()) {
            throw first.error ?: second.error ?: IllegalStateException("Stamp check failed")
        }
        return stamps
    }

    private suspend fun query(
        client: OkHttpClient,
        login: String?,
        password: String?,
        urls: List<String>,
    ): QueryResult = coroutineScope {
        val results = urls.map { url ->
            async(Dispatchers.IO) {
                val sardine = OkHttpSardine(client).apply {
                    setCredentials(login, password)
                }
                try {
                    url to Result.success(
                        FolderScanState.dirStamp(sardine.list(url, 0).firstOrNull()),
                    )
                } catch (ex: Throwable) {
                    Log.e(TAG, "Stamp failed: $url", ex)
                    url to Result.failure(ex)
                }
            }
        }.awaitAll()
        val stamps = LinkedHashMap<String, String>()
        var error: Throwable? = null
        for ((url, result) in results) {
            result.fold(
                onSuccess = { stamps[url] = it },
                onFailure = { if (error == null) error = it },
            )
        }
        QueryResult(stamps, error)
    }

    private data class QueryResult(
        val stamps: Map<String, String>,
        val error: Throwable?,
    )

    private const val TAG = "dav"
}
