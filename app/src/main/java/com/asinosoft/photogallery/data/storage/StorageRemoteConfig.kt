package com.asinosoft.photogallery.data.storage

import android.util.Log
import com.asinosoft.photogallery.BuildConfig
import com.asinosoft.photogallery.R
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Какие типы хранилищ можно **добавлять** новым. Уже подключённые не трогаем.
 * Remote Config key: [KEY_STORAGE_TYPES_ENABLED] = JSON array of enum names.
 */
@Singleton
class StorageRemoteConfig @Inject constructor() {
    private val remoteConfig = FirebaseRemoteConfig.getInstance()
    private val gson = Gson()
    private val _enabledForNew = MutableStateFlow(DEFAULT_ENABLED)
    val enabledForNew: StateFlow<Set<StorageType>> = _enabledForNew.asStateFlow()

    init {
        remoteConfig.setConfigSettingsAsync(
            remoteConfigSettings {
                minimumFetchIntervalInSeconds =
                    if (BuildConfig.DEBUG) DEBUG_FETCH_INTERVAL_SEC else PROD_FETCH_INTERVAL_SEC
            }
        )
        remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults)
        // Дефолты / кэш прошлого fetch — до сети.
        applyFromActivated()
        Log.i(TAG, "defaults/cache enabledForNew=${_enabledForNew.value}")
    }

    /** Fetch с сервера и обновить [enabledForNew]. */
    suspend fun refresh() {
        runCatching {
            remoteConfig.fetchAndActivate().await()
        }.onFailure { ex ->
            Log.w(TAG, "fetchAndActivate failed — keeping ${_enabledForNew.value}", ex)
        }
        applyFromActivated()
        Log.i(TAG, "enabledForNew=${_enabledForNew.value}")
    }

    fun isEnabledForNew(type: StorageType): Boolean {
        if (type == StorageType.LOCAL) return true
        return type in _enabledForNew.value
    }

    private fun applyFromActivated() {
        val raw = remoteConfig.getString(KEY_STORAGE_TYPES_ENABLED)
        _enabledForNew.value = parseEnabled(raw)
    }

    private fun parseEnabled(raw: String): Set<StorageType> {
        if (raw.isBlank()) return DEFAULT_ENABLED
        return runCatching {
            val list: List<String> = gson.fromJson(
                raw,
                object : TypeToken<List<String>>() {}.type
            ) ?: return DEFAULT_ENABLED
            val parsed = list.mapNotNull { name ->
                runCatching { StorageType.valueOf(name.trim().uppercase()) }.getOrNull()
                    ?.takeUnless { it == StorageType.LOCAL }
            }.toSet()
            parsed.ifEmpty { DEFAULT_ENABLED }
        }.getOrElse {
            Log.w(TAG, "Bad $KEY_STORAGE_TYPES_ENABLED: ${raw.take(120)}")
            DEFAULT_ENABLED
        }
    }

    companion object {
        private const val TAG = "gallery.remote_config"
        const val KEY_STORAGE_TYPES_ENABLED = "storage_types_enabled"
        private const val DEBUG_FETCH_INTERVAL_SEC = 0L
        private const val PROD_FETCH_INTERVAL_SEC = 3600L

        val DEFAULT_ENABLED: Set<StorageType> = setOf(
            StorageType.GOOGLE_DRIVE,
            StorageType.DROPBOX,
            StorageType.NEXTCLOUD,
            StorageType.WEBDAV,
            StorageType.ONEDRIVE,
            StorageType.YANDEX,
        )
    }
}
