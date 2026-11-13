package com.asinosoft.photogallery.data.storage

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/** OkHttp Request.tag — переживает редирект, в отличие от X-Gallery-Storage-Id. */
data class GalleryStorageTag(val id: Long)

@Singleton
class StorageAuthProvider @Inject constructor(
    val storageDao: StorageDao,
    val storageProviderRegistry: StorageProviderRegistry
) {
    /**
     * Добавляет Authorization.
     * Токен/пароль всегда из БД (не из кэша провайдера) — иначе после refresh OAuth
     * Coil продолжает слать старый Bearer и ловит HTTP 401.
     */
    fun authorize(request: Request): Request {
        val forcedId = request.header(HEADER_STORAGE_ID)?.toLongOrNull()
            ?: request.tag(GalleryStorageTag::class.java)?.id
        val clean = request.newBuilder().removeHeader(HEADER_STORAGE_ID).build()

        val storage = runBlocking {
            if (forcedId != null) {
                runCatching { storageDao.getStorageById(forcedId) }.getOrNull()
                    ?.takeUnless { it.type == StorageType.LOCAL }
            } else {
                findStorageForUrl(clean.url)
            }
        } ?: return request

        return storageProviderRegistry.createStorageProvider(storage).authorize(clean)
    }

    /** @deprecated больше не нужен список в памяти; оставлен для вызовов после add/delete. */
    suspend fun refresh() {
        // no-op: authorize читает БД на каждый запрос
    }

    private suspend fun findStorageForUrl(url: HttpUrl): Storage? {
        val host = url.host
        val remotes = storageDao.getStorages().first()
            .filterNot { it.type == StorageType.LOCAL }
        remotes.firstOrNull { it.url?.host == host }?.let { return it }
        // Несколько Drive/Dropbox на одном host — без X-Gallery-Storage-Id
        // нельзя угадать аккаунт (иначе as010201 получит токен asinosoft).
        val shared = remotes.filter { matchesSharedHost(it.type, host) }
        return shared.singleOrNull()
    }

    private fun matchesSharedHost(type: StorageType, host: String): Boolean = when (type) {
        StorageType.YANDEX ->
            host == "downloader.disk.yandex.ru" ||
                    host == "cloud-api.yandex.net" ||
                    host.endsWith(".yandex.net") ||
                    host.endsWith(".yandex.ru")

        StorageType.DROPBOX ->
            host == "api.dropboxapi.com" ||
                    host == "content.dropboxapi.com" ||
                    host.endsWith(".dropboxusercontent.com")

        StorageType.GOOGLE_DRIVE ->
            host == "www.googleapis.com" ||
                    host == "googleapis.com" ||
                    host.endsWith(".googleusercontent.com")

        StorageType.ONEDRIVE ->
            host == "graph.microsoft.com" ||
                    host.endsWith(".onedrive.com") ||
                    host.endsWith(".sharepoint.com")

        else -> false
    }

    companion object {
        const val HEADER_STORAGE_ID = "X-Gallery-Storage-Id"
    }
}
