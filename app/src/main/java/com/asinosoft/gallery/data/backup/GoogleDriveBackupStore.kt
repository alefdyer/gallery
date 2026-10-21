package com.asinosoft.gallery.data.backup

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.core.content.edit
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleDriveBackupStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    val hasToken: Boolean
        get() = !prefs.getString(KEY_ACCESS_TOKEN, null).isNullOrBlank()

    fun clearToken() {
        prefs.edit { remove(KEY_ACCESS_TOKEN).remove(KEY_FILE_ID) }
    }

    fun authorizationRequest(): AuthorizationRequest =
        AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
            .build()

    /**
     * Запускает выдачу разрешения Drive App Data.
     */
    suspend fun beginAuthorize(activity: Activity): GoogleDriveAuthStart {
        val result = Identity.getAuthorizationClient(activity)
            .authorize(authorizationRequest())
            .await()
        if (result.hasResolution()) {
            val pending = result.pendingIntent
                ?: error("Authorization resolution without PendingIntent")
            return GoogleDriveAuthStart.NeedsUi(pending)
        }
        val token = result.accessToken
        if (token.isNullOrBlank()) {
            error(oauthConfigHint("No access token after authorize()"))
        }
        prefs.edit { putString(KEY_ACCESS_TOKEN, token) }
        return GoogleDriveAuthStart.AlreadyAuthorized
    }

    /**
     * После UI согласия: разобрать Intent или повторно запросить токен.
     */
    suspend fun handleAuthorizeResult(
        activity: Activity,
        result: ActivityResult,
    ): GoogleDriveAuthOutcome {
        if (result.resultCode == Activity.RESULT_CANCELED && result.data == null) {
            return GoogleDriveAuthOutcome.Cancelled
        }
        if (result.data != null) {
            try {
                val auth = Identity.getAuthorizationClient(activity)
                    .getAuthorizationResultFromIntent(result.data)
                val token = auth.accessToken
                if (!token.isNullOrBlank()) {
                    prefs.edit { putString(KEY_ACCESS_TOKEN, token) }
                    return GoogleDriveAuthOutcome.Success
                }
                Log.w(TAG, "Intent OK but accessToken is null; re-authorizing")
            } catch (e: ApiException) {
                Log.w(TAG, "getAuthorizationResultFromIntent failed: ${e.statusCode}", e)
                return mapApiException(e)
            }
        } else if (result.resultCode != Activity.RESULT_OK) {
            return GoogleDriveAuthOutcome.Cancelled
        }

        // Согласие могло сохраниться без токена в Intent — запрашиваем снова.
        return try {
            when (val again = beginAuthorize(activity)) {
                GoogleDriveAuthStart.AlreadyAuthorized -> GoogleDriveAuthOutcome.Success
                is GoogleDriveAuthStart.NeedsUi ->
                    GoogleDriveAuthOutcome.Failed(
                        oauthConfigHint("Consent UI returned without token (still needs resolution)")
                    )
            }
        } catch (e: ApiException) {
            mapApiException(e)
        } catch (e: Exception) {
            GoogleDriveAuthOutcome.Failed(e.message ?: "Authorization failed")
        }
    }

    private fun mapApiException(e: ApiException): GoogleDriveAuthOutcome {
        val message = e.message.orEmpty()
        return when {
            e.statusCode == CommonStatusCodes.CANCELED -> GoogleDriveAuthOutcome.Cancelled
            e.statusCode == CommonStatusCodes.DEVELOPER_ERROR ||
                message.contains("UNREGISTERED_ON_API_CONSOLE", ignoreCase = true) ->
                GoogleDriveAuthOutcome.Failed(oauthConfigHint())

            else ->
                GoogleDriveAuthOutcome.Failed(
                    message.takeIf { it.isNotBlank() }
                        ?: "Google auth error ${e.statusCode}"
                )
        }
    }

    private fun oauthConfigHint(prefix: String = "UNREGISTERED_ON_API_CONSOLE"): String =
        "$prefix. В Google Cloud Console включите Drive API и создайте OAuth-клиент " +
            "типа Android: package ${context.packageName} и SHA-1 сертификата, " +
            "которым подписана установленная сборка (debug и release — разные клиенты). " +
            "google.oauthClientId приложение не регистрирует."

    /** Все сохранённые копии в App Data, новые сверху. */
    suspend fun listBackups(): List<DriveBackupFile> = withContext(Dispatchers.IO) {
        val token = requireToken()
        val query = "name contains '$BACKUP_FILE_PREFIX' and trashed = false".encodeURL()
        val url = "$DRIVE_FILES?spaces=appDataFolder&q=$query" +
                "&fields=files(id,name,modifiedTime)&orderBy=modifiedTime%20desc"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throwIllegalHttp(response.code, body)
            }
            gson.fromJson(body, DriveFileList::class.java)?.files.orEmpty()
                .mapNotNull { file ->
                    val id = file.id ?: return@mapNotNull null
                    val name = file.name ?: return@mapNotNull null
                    DriveBackupFile(id, displayNameOf(name), file.modifiedTime)
                }
        }
    }

    /**
     * Сохраняет копию: [fileId] задан — заменяет существующий файл,
     * не задан — создаёт новый с именем [displayName].
     */
    suspend fun uploadJson(json: String, fileId: String?, displayName: String) =
        withContext(Dispatchers.IO) {
            Log.i(TAG, "Upload start, bytes=${json.length}")
            val token = requireToken()
            if (null != fileId) {
                updateFile(token, fileId, json)
                prefs.edit { putString(KEY_FILE_ID, fileId) }
            } else {
                val id = createFile(token, json, fileNameFor(displayName))
                prefs.edit { putString(KEY_FILE_ID, id) }
            }
            Log.i(TAG, "Upload done")
        }

    suspend fun downloadJson(fileId: String): String = withContext(Dispatchers.IO) {
        Log.i(TAG, "Download start")
        val token = requireToken()
        val request = Request.Builder()
            .url("$DRIVE_FILES/$fileId?alt=media")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throwIllegalHttp(response.code, body)
            }
            Log.i(TAG, "Download done, bytes=${body.length}")
            body
        }
    }

    suspend fun deleteBackup(fileId: String) = withContext(Dispatchers.IO) {
        val token = requireToken()
        val request = Request.Builder()
            .url("$DRIVE_FILES/$fileId")
            .header("Authorization", "Bearer $token")
            .delete()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throwIllegalHttp(response.code, body)
            }
        }
        if (prefs.getString(KEY_FILE_ID, null) == fileId) {
            prefs.edit { remove(KEY_FILE_ID) }
        }
    }

    internal fun fileNameFor(displayName: String): String {
        val clean = displayName.trim()
            .replace(Regex("[\\\\/:*?\"<>|']"), "_")
            .take(60)
            .ifBlank { "backup" }
        return "$BACKUP_FILE_PREFIX$clean.json"
    }

    internal fun displayNameOf(fileName: String): String = when {
        fileName == BACKUP_FILE_NAME -> LEGACY_DISPLAY_NAME
        fileName.startsWith(BACKUP_FILE_PREFIX) ->
            fileName.removePrefix(BACKUP_FILE_PREFIX).removeSuffix(".json")

        else -> fileName.removeSuffix(".json")
    }

    private fun throwIllegalHttp(code: Int, body: String): Nothing {
        if (code == 401 || code == 403) {
            clearToken()
            error("Сессия Google Drive истекла. Подключите аккаунт снова.")
        }
        throw IllegalStateException(driveError(body) ?: "HTTP $code")
    }

    private fun requireToken(): String =
        prefs.getString(KEY_ACCESS_TOKEN, null)
            ?: error("Not signed in to Google Drive")

    private fun createFile(token: String, json: String, fileName: String): String {
        val metadata = """{"name":"$fileName","parents":["appDataFolder"]}"""
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "metadata",
                null,
                metadata.toRequestBody("application/json; charset=UTF-8".toMediaType())
            )
            .addFormDataPart(
                "file",
                fileName,
                json.toRequestBody("application/json; charset=UTF-8".toMediaType())
            )
            .build()
        val request = Request.Builder()
            .url("$DRIVE_UPLOAD?uploadType=multipart")
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throwIllegalHttp(response.code, payload)
            }
            return gson.fromJson(payload, DriveFile::class.java)?.id
                ?: error("Empty file id")
        }
    }

    private fun updateFile(token: String, fileId: String, json: String) {
        val request = Request.Builder()
            .url("$DRIVE_UPLOAD/$fileId?uploadType=media")
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json; charset=UTF-8")
            .method(
                "PATCH",
                json.toRequestBody("application/json; charset=UTF-8".toMediaType())
            )
            .build()
        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throwIllegalHttp(response.code, payload)
            }
        }
    }

    private fun driveError(payload: String): String? = runCatching {
        gson.fromJson(payload, DriveErrorResponse::class.java)?.error?.message
    }.getOrNull()

    private fun String.encodeURL(): String =
        java.net.URLEncoder.encode(this, Charsets.UTF_8.name())

    private data class DriveFileList(val files: List<DriveFile>?)
    private data class DriveFile(val id: String?, val name: String?, val modifiedTime: String?)
    private data class DriveErrorResponse(val error: DriveError?)
    private data class DriveError(
        val message: String?,
        @SerializedName("status") val status: String?,
    )

    companion object {
        const val BACKUP_FILE_NAME = "gallery-settings-v1.json"
        private const val BACKUP_FILE_PREFIX = "gallery-settings-"
        private const val LEGACY_DISPLAY_NAME = "Основная копия"
        private const val TAG = "GoogleDriveBackup"
        private const val PREFS = "google_drive_backup"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_FILE_ID = "file_id"
        private const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        private const val DRIVE_FILES = "https://www.googleapis.com/drive/v3/files"
        private const val DRIVE_UPLOAD = "https://www.googleapis.com/upload/drive/v3/files"
    }
}

/** Одна сохранённая копия на Google Drive. [modifiedTime] — RFC 3339 строка от Drive или null. */
data class DriveBackupFile(
    val id: String,
    val displayName: String,
    val modifiedTime: String?,
)

sealed class GoogleDriveAuthStart {
    data object AlreadyAuthorized : GoogleDriveAuthStart()
    data class NeedsUi(val pendingIntent: PendingIntent) : GoogleDriveAuthStart()
}

sealed class GoogleDriveAuthOutcome {
    data object Success : GoogleDriveAuthOutcome()
    data object Cancelled : GoogleDriveAuthOutcome()
    data class Failed(val message: String) : GoogleDriveAuthOutcome()
}
