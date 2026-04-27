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
        providers.forEach { provider ->
            if (request.url.host == provider.storage.url?.host) {
                return provider.authorize(request)
            }
        }

        return request
    }
}
