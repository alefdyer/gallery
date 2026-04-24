package com.asinosoft.gallery.data.storage

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import okhttp3.Request

class StorageAuthProvider @Inject constructor(
    @param:ApplicationContext val context: Context,
    val storageDao: StorageDao,
    val storageProviderRegistry: StorageProviderRegistry
) {
    private var providers = listOf<StorageProvider>()

    init {
        refresh()
    }

    fun refresh() {
        synchronized(this) {
            runBlocking {
                providers = storageDao.getStorages().map { storage ->
                    storageProviderRegistry.getStorageProvider(storage)
                }
            }
        }
    }

    fun authorize(request: Request): Request {
        val url = request.url.toString()
        providers.forEach { provider ->
            if (true == provider.storage.url?.let { url.startsWith(it) }) {
                return provider.authorize(request)
            }
        }

        return request
    }
}
