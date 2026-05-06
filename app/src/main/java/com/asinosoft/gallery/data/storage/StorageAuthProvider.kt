package com.asinosoft.gallery.data.storage

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import okhttp3.Request

@Singleton
class StorageAuthProvider @Inject constructor(
    @param:ApplicationContext val context: Context,
    val storageDao: StorageDao,
    val storageProviderRegistry: StorageProviderRegistry
) {
    private var providers = listOf<StorageProvider>()

    init {
        runBlocking {
            refresh()
        }
    }

    suspend fun refresh() {
        providers = storageDao.getStorages()
            .filterNot { it.type == StorageType.LOCAL }
            .map { storage -> storageProviderRegistry.getStorageProvider(storage.id) }
    }

    fun authorize(request: Request): Request {
        providers.forEach { provider ->
            if (request.url.host == provider.storage.url?.host) {
                return provider.authorize(request)
            }
            if (
                provider.storage.type == StorageType.YANDEX &&
                (
                    request.url.host == "downloader.disk.yandex.ru" ||
                        request.url.host == "cloud-api.yandex.net"
                    )
            ) {
                return provider.authorize(request)
            }
            if (
                provider.storage.type == StorageType.DROPBOX &&
                (
                    request.url.host == "api.dropboxapi.com" ||
                        request.url.host == "content.dropboxapi.com"
                    )
            ) {
                return provider.authorize(request)
            }
        }

        return request
    }
}
