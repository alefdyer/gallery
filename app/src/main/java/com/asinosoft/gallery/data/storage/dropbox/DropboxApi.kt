package com.asinosoft.gallery.data.storage.dropbox

import android.net.Uri
import android.util.Base64
import androidx.core.net.toUri
import com.asinosoft.gallery.BuildConfig
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

object DropboxApi {
    const val BASE_URL = "https://api.dropboxapi.com/2/"
    private const val OAUTH_AUTHORIZE_URL = "https://www.dropbox.com/oauth2/authorize"
    private const val OAUTH_TOKEN_URL = "https://api.dropboxapi.com/oauth2/token"

    private lateinit var codeVerifier: String
    private lateinit var codeChallenge: String
    private lateinit var state: String
    private val delivered = AtomicBoolean(false)

    fun startAuthorization(): Uri {
        delivered.set(false)
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

    suspend fun getTokenFromUrl(uri: Uri): Result<String> = runCatching {
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

        return exchangeCodeForToken(
            appKey = BuildConfig.DROPBOX_APP_KEY,
            redirectUri = BuildConfig.DROPBOX_REDIRECT_URI,
            code = code,
            codeVerifier = codeVerifier
        )
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
        appKey: String,
        redirectUri: String,
        code: String,
        codeVerifier: String,
        client: OkHttpClient = OkHttpClient()
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val body = FormBody.Builder()
                .add("code", code)
                .add("grant_type", "authorization_code")
                .add("client_id", appKey)
                .add("redirect_uri", redirectUri)
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
                parseDropboxTokenResponse(payload)?.accessToken
                    ?: throw IllegalStateException("Empty token response")
            }
        }
    }

    private fun parseDropboxTokenResponse(payload: String): DropboxTokenResponse? {
        val raw = Gson().fromJson(payload, DropboxTokenRawResponse::class.java) ?: return null
        val accessToken = raw.accessToken ?: return null
        return DropboxTokenResponse(
            accessToken = accessToken,
            accountId = raw.accountId,
            uid = raw.uid
        )
    }
}

data class DropboxTokenResponse(val accessToken: String, val accountId: String?, val uid: String?)

private data class DropboxTokenRawResponse(
    @SerializedName("access_token")
    val accessToken: String?,
    @SerializedName("account_id")
    val accountId: String?,
    val uid: String?
)

private data class DropboxErrorResponse(
    @SerializedName("error_description")
    val errorDescription: String?
)
