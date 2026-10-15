package hrshd1eux.flowgrab.util

import android.util.Log
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

private const val TAG = "InstantPreviewUtil"

data class InstantPreview(
    val url: String,
    val title: String,
    val uploader: String? = null,
    val thumbnailUrl: String? = null,
    val duration: Double? = null,
) {
    fun toVideoInfo(): VideoInfo =
        VideoInfo(
            originalUrl = url,
            webpageUrl = url,
            title = title,
            uploader = uploader,
            thumbnail = thumbnailUrl,
            duration = duration,
        )
}

object InstantPreviewUtil {
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val previewCache = ConcurrentHashMap<String, InstantPreview>()

    fun getCached(url: String): InstantPreview? = previewCache[url]

    suspend fun fetchInstantPreview(url: String): InstantPreview? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        val cleanUrl = sanitizeUrl(url)
        previewCache[cleanUrl]?.let { return@withContext it }
        previewCache[url]?.let { return@withContext it }

        val preview = when {
            cleanUrl.contains("youtu", ignoreCase = true) -> fetchYouTubeOEmbed(cleanUrl)
            cleanUrl.contains("soundcloud.com", ignoreCase = true) -> fetchOEmbed("https://soundcloud.com/oembed?format=json&url=", cleanUrl)
            cleanUrl.contains("tiktok.com", ignoreCase = true) -> fetchOEmbed("https://www.tiktok.com/oembed?url=", cleanUrl)
            cleanUrl.contains("vimeo.com", ignoreCase = true) -> fetchOEmbed("https://vimeo.com/api/oembed.json?url=", cleanUrl)
            cleanUrl.contains("reddit.com", ignoreCase = true) -> fetchOEmbed("https://www.reddit.com/oembed?url=", cleanUrl)
            cleanUrl.contains("twitter.com", ignoreCase = true) || cleanUrl.contains("x.com", ignoreCase = true) ->
                fetchOEmbed("https://publish.twitter.com/oembed?url=", cleanUrl)
            else -> null
        }

        if (preview != null) {
            previewCache[cleanUrl] = preview
            previewCache[url] = preview
        }
        preview
    }

    private fun fetchYouTubeOEmbed(url: String): InstantPreview? {
        return runCatching {
            val encoded = URLEncoder.encode(url, "UTF-8")
            val request = Request.Builder()
                .url("https://www.youtube.com/oembed?url=$encoded&format=json")
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val obj = json.parseToJsonElement(body).jsonObject
                val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: return null
                val author = obj["author_name"]?.jsonPrimitive?.contentOrNull
                var thumb = obj["thumbnail_url"]?.jsonPrimitive?.contentOrNull

                // Extract video ID for high-res thumbnail if possible
                val videoId = Regex("(?:v=|/v/|youtu\\.be/|/embed/|/shorts/)([a-zA-Z0-9_-]{11})")
                    .find(url)?.groupValues?.get(1)
                if (!videoId.isNullOrBlank()) {
                    thumb = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                }

                InstantPreview(
                    url = url,
                    title = title,
                    uploader = author,
                    thumbnailUrl = thumb,
                )
            }
        }.getOrNull()
    }

    private fun fetchOEmbed(endpoint: String, url: String): InstantPreview? {
        return runCatching {
            val encoded = URLEncoder.encode(url, "UTF-8")
            val request = Request.Builder()
                .url(endpoint + encoded)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val obj = json.parseToJsonElement(body).jsonObject
                val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: return null
                val author = obj["author_name"]?.jsonPrimitive?.contentOrNull
                val thumb = obj["thumbnail_url"]?.jsonPrimitive?.contentOrNull

                InstantPreview(
                    url = url,
                    title = title,
                    uploader = author,
                    thumbnailUrl = thumb,
                )
            }
        }.getOrNull()
    }
}
