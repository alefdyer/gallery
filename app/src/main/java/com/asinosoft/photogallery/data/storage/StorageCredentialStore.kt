package com.asinosoft.photogallery.data.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Пароли WebDAV/Nextcloud и OAuth-токены вне plaintext Room.
 * Prefs file [PREFS_NAME] исключён из Auto Backup.
 */
@Singleton
class StorageCredentialStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun get(storageId: Long): String? =
        prefs.getString(key(storageId), null)?.takeIf { it.isNotEmpty() }

    fun put(storageId: Long, password: String?) {
        if (storageId <= 0L) return
        prefs.edit().apply {
            if (password.isNullOrEmpty()) {
                remove(key(storageId))
            } else {
                putString(key(storageId), password)
            }
            apply()
        }
    }

    fun remove(storageId: Long) {
        if (storageId <= 0L) return
        prefs.edit().remove(key(storageId)).apply()
    }

    private fun key(storageId: Long) = "pwd_$storageId"

    companion object {
        const val PREFS_NAME = "storage_credentials"
    }
}
