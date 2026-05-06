package com.asinosoft.gallery.data.storage.dropbox

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

object DropboxApi {
    const val BASE_URL = "https://api.dropboxapi.com/2/"
    private const val OAUTH_AUTHORIZE_URL = "https://www.dropbox.com/oauth2/authorize"
    private const val OAUTH_TOKEN_URL = "https://api.dropboxapi.com/oauth2/token"

    fun buildAuthorizationUrl(
        appKey: String,
        redirectUri: String,
        codeChallenge: String,
        state: String
    ): String = OAUTH_AUTHORIZE_URL
        .toHttpUrl()
        .newBuilder()
        .addQueryParameter("client_id", appKey)
        .addQueryParameter("response_type", "code")
        .addQueryParameter("redirect_uri", redirectUri)
        .addQueryParameter("code_challenge", codeChallenge)
        .addQueryParameter("code_challenge_method", "S256")
        .addQueryParameter("token_access_type", "offline")
        .addQueryParameter("state", state)
        .build()
        .toString()

    suspend fun exchangeCodeForToken(
        appKey: String,
        redirectUri: String,
        code: String,
        codeVerifier: String,
        client: OkHttpClient = OkHttpClient()
    ): Result<DropboxTokenResponse> = withContext(Dispatchers.IO) {
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
                parseDropboxTokenResponse(payload)
                    ?: throw IllegalStateException("Empty token response")
            }
        }
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

fun parseDropboxTokenResponse(payload: String): DropboxTokenResponse? {
    val raw = Gson().fromJson(payload, DropboxTokenRawResponse::class.java) ?: return null
    val accessToken = raw.accessToken ?: return null
    return DropboxTokenResponse(
        accessToken = accessToken,
        accountId = raw.accountId,
        uid = raw.uid
    )
}
