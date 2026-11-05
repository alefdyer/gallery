package com.asinosoft.photogallery.data.storage.webdav

import android.net.Uri
import android.util.Log
import android.util.Xml
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import com.asinosoft.photogallery.data.Image
import com.asinosoft.photogallery.data.Media
import com.asinosoft.photogallery.data.Video
import com.asinosoft.photogallery.data.mediaParentPath
import com.asinosoft.photogallery.data.mediaSortDateTime
import com.asinosoft.photogallery.data.parseTakenAtFromFilename
import com.asinosoft.photogallery.data.storage.Storage
import com.asinosoft.photogallery.data.storage.StorageChange
import com.asinosoft.photogallery.data.storage.SyncCursorExpiredException
import java.io.StringReader
import java.net.URI
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser

object DavSyncCollection {
    private const val TAG = "DavSync"
    private const val NS_DAV = "DAV:"

    suspend fun fetchAllChanges(
        client: OkHttpClient,
        authorize: (Request) -> Request,
        rootUrl: String,
        storage: Storage,
        rootPathPrefix: String,
        syncToken: String?,
        thumbnailUri: ((String) -> Uri?)? = null,
    ): Pair<List<StorageChange>, String?>? = withContext(Dispatchers.IO) {
        val tokenBlank = syncToken.isNullOrBlank()
        val body = buildSyncCollectionBody(syncToken)
        val request = authorize(
            Request.Builder()
                .url(rootUrl)
                .method(
                    "REPORT",
                    body.toRequestBody("application/xml; charset=utf-8".toMediaType())
                )
                .header("Depth", "infinity")
                .build()
        )
        val response = client.newCall(request).execute()
        response.use { resp ->
            val code = resp.code
            when {
                code == 501 || code == 405 -> return@withContext null
                tokenBlank && (code == 403 || code == 404) -> return@withContext null
                !tokenBlank && code in 400..499 ->
                    throw SyncCursorExpiredException("DAV sync-collection HTTP $code")

                code !in 200..299 && code != 207 ->
                    throw SyncCursorExpiredException("DAV sync-collection HTTP $code")
            }
            val xml = resp.body?.string().orEmpty()
            if (xml.isBlank()) return@withContext Pair(emptyList(), syncToken)
            parseMultistatus(
                xml = xml,
                rootUrl = rootUrl,
                storage = storage,
                rootPathPrefix = rootPathPrefix,
                thumbnailUri = thumbnailUri,
            )
        }
    }

    private fun buildSyncCollectionBody(syncToken: String?): String = buildString {
        append("""<?xml version="1.0" encoding="utf-8" ?>""")
        append("""<D:sync-collection xmlns:D="DAV:">""")
        if (!syncToken.isNullOrBlank()) {
            append("""<D:sync-token>""")
            append(escapeXml(syncToken))
            append("""</D:sync-token>""")
        }
        append("""<D:sync-level>infinite</D:sync-level>""")
        append("""<D:prop>""")
        append("""<D:displayname/>""")
        append("""<D:getcontentlength/>""")
        append("""<D:getcontenttype/>""")
        append("""<D:getlastmodified/>""")
        append("""<D:resourcetype/>""")
        append("""</D:prop>""")
        append("""</D:sync-collection>""")
    }

    private fun parseMultistatus(
        xml: String,
        rootUrl: String,
        storage: Storage,
        rootPathPrefix: String,
        thumbnailUri: ((String) -> Uri?)?,
    ): Pair<List<StorageChange>, String?> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(StringReader(xml))

        var newSyncToken: String? = null
        val changes = ArrayList<StorageChange>()
        var event = parser.eventType
        var inResponse = false
        var currentHref: String? = null
        var currentStatus: String? = null
        val props = HashMap<String, String>()
        var isCollection = false

        fun resetResponse() {
            currentHref = null
            currentStatus = null
            props.clear()
            isCollection = false
            inResponse = false
        }

        fun flushResponse() {
            val href = currentHref ?: return
            val davPath = hrefToDavPath(href, rootUrl)
            val status = currentStatus.orEmpty()
            if (status.contains(" 404 ") || status.contains("404 Not Found")) {
                changes += StorageChange.Delete(davPath)
                resetResponse()
                return
            }
            if (isCollection) {
                resetResponse()
                return
            }
            val name = props["displayname"] ?: davPath.substringAfterLast('/').ifBlank {
                resetResponse()
                return
            }
            val mime = mimeTypeOf(props["getcontenttype"], name)
            if (!mime.startsWith("image/") && !mime.startsWith("video/")) {
                resetResponse()
                return
            }
            val modifiedAt = parseHttpDate(props["getlastmodified"])
            val takenAt = parseTakenAtFromFilename(name)
            val (sortDate, sortTime) = mediaSortDateTime(takenAt, modifiedAt, null)
            val isImage = mime.startsWith("image/")
            val length = props["getcontentlength"]?.toLongOrNull() ?: -1L
            val resolvedUrl = resolveHref(rootUrl, href)
            changes += StorageChange.Upsert(
                Media(
                    uri = resolvedUrl.toUri(),
                    date = sortDate,
                    time = sortTime,
                    path = mediaParentPath(davPath, rootPathPrefix),
                    size = length,
                    filename = name,
                    mimeType = mime,
                    storageId = storage.id,
                    storageType = storage.type,
                    storageItemId = davPath,
                    thumbnail = thumbnailUri?.invoke(davPath),
                    image = Image().takeIf { isImage },
                    video = Video().takeIf { !isImage },
                    takenAt = takenAt,
                    modifiedAt = modifiedAt,
                )
            )
            resetResponse()
        }

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val local = parser.name
                    val ns = parser.namespace
                    when {
                        local == "sync-token" && ns == NS_DAV ->
                            newSyncToken = parser.nextText().trim().ifBlank { null }

                        local == "response" && ns == NS_DAV -> {
                            inResponse = true
                            currentHref = null
                            currentStatus = null
                            props.clear()
                            isCollection = false
                        }

                        inResponse && local == "href" && ns == NS_DAV ->
                            currentHref = parser.nextText().trim()

                        inResponse && local == "status" && ns == NS_DAV ->
                            currentStatus = parser.nextText().trim()

                        inResponse && local == "collection" && ns == NS_DAV ->
                            isCollection = true

                        inResponse && ns == NS_DAV && local !in setOf(
                            "response", "propstat", "prop", "resourcetype"
                        ) -> {
                            val text = readText(parser)
                            if (text.isNotBlank()) props[local] = text
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    if (parser.name == "response" && parser.namespace == NS_DAV && inResponse) {
                        flushResponse()
                    }
                }
            }
            event = parser.next()
        }
        return Pair(changes, newSyncToken)
    }

    private fun readText(parser: XmlPullParser): String {
        if (parser.isEmptyElementTag) return ""
        val depth = parser.depth
        val sb = StringBuilder()
        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT &&
            !(event == XmlPullParser.END_TAG && parser.depth == depth)
        ) {
            if (event == XmlPullParser.TEXT) sb.append(parser.text)
            event = parser.next()
        }
        return sb.toString().trim()
    }

    private fun hrefToDavPath(href: String, rootUrl: String): String {
        val path = runCatching { URI(href).path }.getOrNull()
            ?: runCatching { URI(rootUrl).resolve(href).path }.getOrNull()
            ?: href
        return path
    }

    private fun resolveHref(rootUrl: String, href: String): String =
        URI(rootUrl).resolve(href).toString()

    private fun mimeTypeOf(contentType: String?, name: String): String {
        val extension = name.substringAfterLast('.', "").lowercase()
        val fromName = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        if (fromName != null &&
            (fromName.startsWith("image/") || fromName.startsWith("video/"))
        ) {
            return fromName
        }
        val ct = contentType?.substringBefore(';')?.trim().orEmpty()
        if (ct.startsWith("image/") || ct.startsWith("video/")) return ct
        return ct
    }

    private fun parseHttpDate(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return try {
            ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                .toInstant().toEpochMilli()
        } catch (_: Throwable) {
            try {
                Instant.parse(value).toEpochMilli()
            } catch (_: Throwable) {
                Log.w(TAG, "Can't parse date: $value")
                null
            }
        }
    }

    private fun escapeXml(text: String): String = buildString {
        text.forEach { c ->
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(c)
            }
        }
    }
}
