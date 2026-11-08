package com.asinosoft.photogallery.data.storage.dropbox

import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.core.net.toUri
import com.asinosoft.photogallery.BuildConfig
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object DropboxApi {
    const val BASE_URL = "https://api.dropboxapi.com/2/"
    private const val OAUTH_AUTHORIZE_URL = "https://www.dropbox.com/oauth2/authorize"
    private const val OAUTH_TOKEN_URL = "https://api.dropboxapi.com/oauth2/token"

    private lateinit var codeVerifier: String
    private lateinit var codeChallenge: String
    private lateinit var state: String
    private val delivered = AtomicBoolean(false)

    /** Refresh token с последнего OAuth, пока addStorage не заберёт. */
    private val pendingRefreshToken = AtomicReference<String?>(null)

    fun startAuthorization(): Uri {
        delivered.set(false)
        pendingRefreshToken.set(null)
        codeVerifier = generateCodeVerifier()
        codeChallenge = codeChallengeS256(codeVerifier)
        state = UUID.randomUUID().toString()

        return OAUTH_AUTHORIZE_URL
            .toUri()
            .buildUpon()
            .appendQueryParameter("client_id", BuildConfig.DROPBOX_APP_KEY)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", BuildConfig.DROPBOX_REDIRECT_URI)
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("token_access_type", "offline")
            .appendQueryParameter("state", state)
            .build()
    }

    fun takePendingRefreshToken(): String? = pendingRefreshToken.getAndSet(null)

    suspend fun getTokenFromUrl(uri: Uri): Result<TokenResult> = runCatching {
        val url = uri.toString()
        if (!url.startsWith(BuildConfig.DROPBOX_REDIRECT_URI)) {
            throw IllegalStateException("Invalid redirect uri")
        }

        val stateValue = uri.getQueryParameter("state")
        if (stateValue != state) {
            throw Exception("Invalid OAuth state")
        }

        val code = uri.getQueryParameter("code")
        if (code.isNullOrBlank()) {
            throw Exception(
                uri.getQueryParameter("error_description")
                    ?: uri.getQueryParameter("error")
                    ?: "Authorization failed"
            )
        }

        if (!delivered.compareAndSet(false, true)) {
            throw Exception("Token already delivered")
        }

        return exchangeCodeForToken(code)
    }

    /**
     * Обновить short-lived access token. PKCE-приложение: только client_id + refresh_token.
     * Dropbox может вернуть новый refresh_token — его тоже надо сохранить.
     */
    suspend fun refreshAccessToken(
        refreshToken: String,
        client: OkHttpClient = OkHttpClient(),
    ): TokenResult? = withContext(Dispatchers.IO) {
        try {
            val body = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .add("client_id", BuildConfig.DROPBOX_APP_KEY)
                .build()
            val request = Request.Builder()
                .url(OAUTH_TOKEN_URL)
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.w(TAG, "refresh_token HTTP ${response.code}: ${payload.take(200)}")
                    return@withContext null
                }
                val token = parseDropboxTokenResponse(payload) ?: return@withContext null
                TokenResult(
                    accessToken = token.accessToken,
                    email = null,
                    refreshToken = token.refreshToken ?: refreshToken,
                )
            }
        } catch (ex: Throwable) {
            Log.w(TAG, "refresh_token failed", ex)
            null
        }
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(64)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE)
    }

    private fun codeChallengeS256(codeVerifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val challenge = digest.digest(codeVerifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(
            challenge,
            Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE
        )
    }

    private suspend fun exchangeCodeForToken(
        code: String,
        client: OkHttpClient = OkHttpClient()
    ): Result<TokenResult> = withContext(Dispatchers.IO) {
        runCatching {
            val body = FormBody.Builder()
                .add("code", code)
                .add("grant_type", "authorization_code")
                .add("client_id", BuildConfig.DROPBOX_APP_KEY)
                .add("redirect_uri", BuildConfig.DROPBOX_REDIRECT_URI)
                .add("code_verifier", codeVerifier)
                .build()
            val request = Request.Builder()
                .url(OAUTH_TOKEN_URL)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val error = runCatching {
                        Gson().fromJson(payload, DropboxErrorResponse::class.java).errorDescription
                    }.getOrNull()
                    throw IllegalStateException(error ?: "HTTP ${response.code}")
                }
                val token = parseDropboxTokenResponse(payload)
                    ?: throw IllegalStateException("Empty token response")
                if (!token.refreshToken.isNullOrBlank()) {
                    pendingRefreshToken.set(token.refreshToken)
                } else {
                    Log.w(TAG, "OAuth response without refresh_token")
                }
                TokenResult(
                    accessToken = token.accessToken,
                    email = fetchEmail(token.accessToken, client),
                    refreshToken = token.refreshToken,
                )
            }
        }
    }

    fun fetchEmail(accessToken: String, client: OkHttpClient = OkHttpClient()): String? {
        // Dropbox RPC без аргументов: тело `null`, Content-Type application/json.
        val request = Request.Builder()
            .url("${BASE_URL}users/get_current_account")
            .header("Authorization", "Bearer $accessToken")
            .post("null".toRequestBody("application/json".toMediaType()))
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.w(TAG, "get_current_account HTTP ${response.code}: $body")
                    return null
                }
                val account = Gson().fromJson(body, DropboxAccountResponse::class.java)
                account.email?.takeIf { it.isNotBlank() }
                    ?: account.name?.displayName?.takeIf { it.isNotBlank() }
            }
        } catch (ex: Throwable) {
            Log.w(TAG, "get_current_account failed", ex)
            null
        }
    }

    private const val TAG = "DropboxApi"

    private fun parseDropboxTokenResponse(payload: String): DropboxTokenResponse? {
        val raw = Gson().fromJson(payload, DropboxTokenRawResponse::class.java) ?: return null
        val accessToken = raw.accessToken ?: return null
        return DropboxTokenResponse(
            accessToken = accessToken,
            refreshToken = raw.refreshToken,
            accountId = raw.accountId,
            uid = raw.uid,
        )
    }

    data class TokenResult(
        val accessToken: String,
        val email: String?,
        val refreshToken: String? = null,
    )
}

data class DropboxTokenResponse(
    val accessToken: String,
    val refreshToken: String?,
    val accountId: String?,
    val uid: String?,
)

private data class DropboxTokenRawResponse(
    @SerializedName("access_token")
    val accessToken: String?,
    @SerializedName("refresh_token")
    val refreshToken: String?,
    @SerializedName("account_id")
    val accountId: String?,
    val uid: String?,
)

private data class DropboxAccountResponse(
    val email: String? = null,
    val name: DropboxNameResponse? = null
)

private data class DropboxNameResponse(
    @SerializedName("display_name")
    val displayName: String? = null
)

private data class DropboxErrorResponse(
    @SerializedName("error_description")
    val errorDescription: String?
)
