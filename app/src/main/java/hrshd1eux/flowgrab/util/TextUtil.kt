package hrshd1eux.flowgrab.util

import android.content.Context
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.core.text.isDigitsOnly
import hrshd1eux.flowgrab.App
import hrshd1eux.flowgrab.App.Companion.applicationScope
import hrshd1eux.flowgrab.App.Companion.context
import hrshd1eux.flowgrab.R
import java.util.regex.Pattern
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object ToastUtil {
    fun makeToast(text: String) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            Toast.makeText(context.applicationContext, text, Toast.LENGTH_SHORT).show()
        } else {
            applicationScope.launch(Dispatchers.Main) {
                Toast.makeText(context.applicationContext, text, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun makeToastSuspend(text: String) {
        applicationScope.launch(Dispatchers.Main) { makeToast(text) }
    }

    fun makeToast(stringId: Int) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            Toast.makeText(context.applicationContext, context.getString(stringId), Toast.LENGTH_SHORT).show()
        } else {
            applicationScope.launch(Dispatchers.Main) {
                Toast.makeText(context.applicationContext, context.getString(stringId), Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@MainThread
fun Context.makeToast(stringId: Int) {
    Toast.makeText(applicationContext, getString(stringId), Toast.LENGTH_SHORT).show()
}

@MainThread
fun Context.makeToast(message: String) {
    Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
}

private const val GIGA_BYTES = 1024f * 1024f * 1024f
private const val MEGA_BYTES = 1024f * 1024f

@Composable
fun Number?.toFileSizeText(): String {
    if (this == null) return stringResource(id = R.string.unknown)

    return this.toFloat().run {
        if (this > GIGA_BYTES) stringResource(R.string.filesize_gb).format(this / GIGA_BYTES)
        else stringResource(R.string.filesize_mb).format(this / MEGA_BYTES)
    }
}

/** Convert time in **seconds** to `hh:mm:ss` or `mm:ss` */
fun Int.toDurationText(): String =
    this.run {
        if (this > 3600) "%d:%02d:%02d".format(this / 3600, (this % 3600) / 60, this % 60)
        else "%02d:%02d".format(this / 60, this % 60)
    }

fun String.isNumberInRange(start: Int, end: Int): Boolean {
    return this.isNotEmpty() &&
        this.isDigitsOnly() &&
        this.length < 10 &&
        this.toInt() >= start &&
        this.toInt() <= end
}

private const val URL_REGEX_PATTERN =
    "(http|https)://[\\w\\-_]+(\\.[\\w\\-_]+)+([\\w\\-.,@?^=%&:/~+#]*[\\w\\-@?^=%&/~+#])?"

fun String.isNumberInRange(range: IntRange): Boolean = this.isNumberInRange(range.first, range.last)

fun ClosedFloatingPointRange<Float>.toIntRange() =
    IntRange(start.roundToInt(), endInclusive.roundToInt())

fun String?.toHttpsUrl(): String =
    this?.run { if (matches(Regex("^(http:).*"))) replaceFirst("http", "https") else this } ?: ""

fun matchUrlFromClipboard(string: String, isMatchingMultiLink: Boolean = false): String {
    findURLsFromString(string, !isMatchingMultiLink).joinToString(separator = "\n").run {
        if (isEmpty()) ToastUtil.makeToast(R.string.paste_fail_msg)
        else ToastUtil.makeToast(R.string.paste_msg)
        return this
    }
}

fun matchUrlFromSharedText(s: String): String {
    findURLsFromString(s, true).joinToString(separator = "\n").run {
        if (isEmpty()) ToastUtil.makeToast(R.string.share_fail_msg)
        //            else makeToast(R.string.share_success_msg)
        return this
    }
}

fun Number?.toBitrateText(): String {
    val br = this?.toFloat() ?: return ""
    return when {
        br <= 0f -> "" // i don't care
        br < 1024f -> "%.1f Kbps".format(br)

        else -> "%.2f Mbps".format(br / 1024f)
    }
}

fun Throwable.extractErrorMessage(): String {
    val raw = message?.trim().takeIf { !it.isNullOrBlank() && it != "null" }
        ?: cause?.extractErrorMessage()
        ?: localizedMessage?.trim().takeIf { !it.isNullOrBlank() && it != "null" }
        ?: toString().trim().takeIf { !it.isNullOrBlank() && it != "null" }
        ?: "Unknown error occurred"
    return formatHumanReadableError(raw)
}

fun formatHumanReadableError(rawMsg: String): String {
    val lower = rawMsg.lowercase()
    val humanPrefix = when {
        lower.contains("there is no video in this post") ->
            "This Instagram post contains only static photos/images and does not have any video or audio stream to download."

        lower.contains("empty media response") ||
        (lower.contains("instagram") && (lower.contains("login") || lower.contains("checkpoint") || lower.contains("private"))) ->
            "Instagram login required: This media may be private or restricted by Instagram. Please log into Instagram in Settings -> Network -> Cookies to import session cookies."

        lower.contains("confirm you're not a bot") || lower.contains("sign in to confirm") ->
            "Bot verification required: The platform requested human verification. Please log in or import cookies via Settings -> Network -> Cookies."

        lower.contains("video unavailable") || lower.contains("this video is unavailable") ->
            "Media unavailable: This video has been removed, made private, or is restricted in your region."

        lower.contains("private video") ->
            "Private video: This content is private. Please sign in with an authorized account in Settings -> Network -> Cookies."

        lower.contains("members-only") ->
            "Members-only content: This video requires channel membership. Please sign in with an active member account in Settings -> Network -> Cookies."

        lower.contains("age-restricted") ->
            "Age-restricted content: Please log into an adult account in Settings -> Network -> Cookies."

        lower.contains("403: forbidden") || lower.contains("http error 403") ->
            "Access forbidden (HTTP 403): The server blocked the request. Please check cookies or network connection."

        lower.contains("404: not found") || lower.contains("http error 404") ->
            "Media not found (HTTP 404): The requested URL does not exist or has been deleted."

        lower.contains("429: too many requests") || lower.contains("http error 429") ->
            "Rate limited (HTTP 429): Too many requests sent to the server. Please wait a few minutes before trying again."

        lower.contains("no space left on device") ->
            "Storage full: Not enough free disk space on device to complete download."

        lower.contains("postprocessing: conversion failed") || lower.contains("conversion failed") ->
            "Post-processing conversion failed. Check storage permissions and free space."

        lower.contains("no media formats or video streams found") || lower.contains("no downloadable video or audio streams found") ->
            "No downloadable media streams found for this URL."

        else -> null
    }

    return if (humanPrefix != null) {
        "$humanPrefix\n\n[Details]: $rawMsg"
    } else {
        rawMsg
    }
}

fun getErrorReport(th: Throwable, url: String): String =
    App.getVersionReport() + "\nURL: ${url}\n${th.extractErrorMessage()}"

fun matchUrlFromString(s: String, isMatchingMultiLink: Boolean = false): String =
    findURLsFromString(s, !isMatchingMultiLink).joinToString(separator = "\n")

fun sanitizeUrl(rawUrl: String): String {
    return try {
        val trimmed = rawUrl.trim()
        val uri = android.net.Uri.parse(trimmed)
        val host = uri.host?.lowercase() ?: return trimmed
        val isYouTube = host.endsWith("youtube.com") || host == "youtu.be"

        val queryParamNames = uri.queryParameterNames
        if (queryParamNames.isEmpty()) {
            return trimmed
        }

        val trackingParams = setOf(
            "si", "feature", "pp", "source_ve_path",
            "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
            "fbclid", "igshid"
        )

        val newUriBuilder = uri.buildUpon().clearQuery()
        for (key in queryParamNames) {
            val lowerKey = key.lowercase()
            if (lowerKey in trackingParams) continue

            // Remove synthetic radio / autoplay mix playlists attached to single video shares
            if (isYouTube && lowerKey == "list") {
                val listVal = uri.getQueryParameter(key).orEmpty()
                if (listVal.startsWith("RD", ignoreCase = true) || listVal.startsWith("UL", ignoreCase = true)) {
                    continue
                }
            }
            if (isYouTube && (lowerKey == "start_radio" || lowerKey == "rv")) {
                continue
            }

            for (value in uri.getQueryParameters(key)) {
                newUriBuilder.appendQueryParameter(key, value)
            }
        }
        newUriBuilder.build().toString()
    } catch (e: Exception) {
        rawUrl.trim()
    }
}

fun findURLsFromString(input: String, firstMatchOnly: Boolean = false): List<String> {
    val result = mutableListOf<String>()
    val pattern = Pattern.compile(URL_REGEX_PATTERN)

    with(pattern.matcher(input)) {
        if (!firstMatchOnly) {
            while (find()) {
                result += sanitizeUrl(group())
            }
        } else {
            if (find()) result += sanitizeUrl(group())
        }
    }
    return result
}

fun connectWithDelimiter(vararg strings: String?, delimiter: String): String =
    strings
        .toList()
        .filter { !it.isNullOrBlank() }
        .joinToString(separator = delimiter) { it.toString() }

fun connectWithBlank(s1: String, s2: String): String {
    val blank = if (s1.isEmpty() || s2.isEmpty()) "" else " "
    return s1 + blank + s2
}
