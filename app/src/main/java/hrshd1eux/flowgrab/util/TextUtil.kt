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

fun getErrorReport(th: Throwable, url: String): String =
    App.getVersionReport() + "\nURL: ${url}\n${th.message}"

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
