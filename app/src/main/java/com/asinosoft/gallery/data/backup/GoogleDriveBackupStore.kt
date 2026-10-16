package com.asinosoft.gallery.data.backup

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.core.content.edit
import com.asinosoft.gallery.BuildConfig
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

    fun authorizationRequest(): AuthorizationRequest {
        val builder = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
        val clientId = BuildConfig.GOOGLE_OAUTH_CLIENT_ID.trim()
        // requestOfflineAccess требует Web client id (не Android).
        if (clientId.isNotEmpty()) {
            builder.requestOfflineAccess(clientId)
        }
        return builder.build()
    }

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

    private fun mapApiException(e: ApiException): GoogleDriveAuthOutcome =
        when (e.statusCode) {
            CommonStatusCodes.CANCELED -> GoogleDriveAuthOutcome.Cancelled
            CommonStatusCodes.DEVELOPER_ERROR ->
                GoogleDriveAuthOutcome.Failed(oauthConfigHint("DEVELOPER_ERROR (10)"))

            else ->
                GoogleDriveAuthOutcome.Failed(
                    e.message?.takeIf { it.isNotBlank() }
                        ?: "Google auth error ${e.statusCode}"
                )
        }

    private fun oauthConfigHint(prefix: String): String =
        "$prefix. В Google Cloud Console: включите Drive API; создайте OAuth-клиент " +
                "типа Android (package ${context.packageName} + SHA-1 debug/release). " +
                "Опционально Web client id → google.oauthClientId в local.properties."

    suspend fun uploadJson(json: String) = withContext(Dispatchers.IO) {
        Log.i(TAG, "Upload start, bytes=${json.length}")
        val token = requireToken()
        val existingId = findBackupFileId(token) ?: prefs.getString(KEY_FILE_ID, null)
        if (null != existingId) {
            updateFile(token, existingId, json)
            prefs.edit { putString(KEY_FILE_ID, existingId) }
        } else {
            val id = createFile(token, json)
            prefs.edit { putString(KEY_FILE_ID, id) }
        }
        Log.i(TAG, "Upload done")
    }

    suspend fun downloadJson(): String = withContext(Dispatchers.IO) {
        Log.i(TAG, "Download start")
        val token = requireToken()
        val fileId = findBackupFileId(token)
            ?: prefs.getString(KEY_FILE_ID, null)
            ?: error("Файл бэкапа не найден на Google Drive. Сначала сохраните копию.")
        prefs.edit { putString(KEY_FILE_ID, fileId) }
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

    private fun findBackupFileId(token: String): String? {
        val url = "$DRIVE_FILES?spaces=appDataFolder&q=${QUERY.encodeURL()}&fields=files(id,name)"
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
            return gson.fromJson(body, DriveFileList::class.java)?.files?.firstOrNull()?.id
        }
    }

    private fun createFile(token: String, json: String): String {
        val metadata = """{"name":"$BACKUP_FILE_NAME","parents":["appDataFolder"]}"""
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "metadata",
                null,
                metadata.toRequestBody("application/json; charset=UTF-8".toMediaType())
            )
            .addFormDataPart(
                "file",
                BACKUP_FILE_NAME,
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
    private data class DriveFile(val id: String?, val name: String?)
    private data class DriveErrorResponse(val error: DriveError?)
    private data class DriveError(
        val message: String?,
        @SerializedName("status") val status: String?,
    )

    companion object {
        const val BACKUP_FILE_NAME = "gallery-settings-v1.json"
        private const val TAG = "GoogleDriveBackup"
        private const val PREFS = "google_drive_backup"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_FILE_ID = "file_id"
        private const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        private const val DRIVE_FILES = "https://www.googleapis.com/drive/v3/files"
        private const val DRIVE_UPLOAD = "https://www.googleapis.com/upload/drive/v3/files"
        private const val QUERY = "name = 'gallery-settings-v1.json' and trashed = false"
    }
}

sealed class GoogleDriveAuthStart {
    data object AlreadyAuthorized : GoogleDriveAuthStart()
    data class NeedsUi(val pendingIntent: PendingIntent) : GoogleDriveAuthStart()
}

sealed class GoogleDriveAuthOutcome {
    data object Success : GoogleDriveAuthOutcome()
    data object Cancelled : GoogleDriveAuthOutcome()
    data class Failed(val message: String) : GoogleDriveAuthOutcome()
}
