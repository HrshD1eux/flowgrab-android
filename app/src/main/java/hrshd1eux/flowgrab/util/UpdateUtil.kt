package hrshd1eux.flowgrab.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import hrshd1eux.flowgrab.App
import hrshd1eux.flowgrab.App.Companion.applicationScope
import hrshd1eux.flowgrab.App.Companion.context
import hrshd1eux.flowgrab.MainActivity
import hrshd1eux.flowgrab.R
import hrshd1eux.flowgrab.util.FileUtil.getFileProvider
import hrshd1eux.flowgrab.util.PreferenceUtil.getInt
import hrshd1eux.flowgrab.util.PreferenceUtil.updateLong
import com.yausername.youtubedl_android.YoutubeDL
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody

object UpdateUtil {

    private const val OWNER = "HrshD1eux"
    private const val REPO = "flowgrab-android"
    private const val ARM64 = "arm64-v8a"
    private const val ARM32 = "armeabi-v7a"
    private const val X86 = "x86"
    private const val X64 = "x86_64"
    private const val TAG = "UpdateUtil"

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

    private val requestForLatestRelease =
        Request.Builder()
            .url("https://api.github.com/repos/${OWNER}/${REPO}/releases/latest")
            .build()

    private val requestForReleases =
        Request.Builder()
            .url("https://api.github.com/repos/${OWNER}/${REPO}/releases")
            .header("User-Agent", "FlowGrab-Android")
            .header("Accept", "application/vnd.github.v3+json")
            .build()

    private const val ytdlpNightlyBuildRelease =
        "https://api.github.com/repos/yt-dlp/yt-dlp-nightly-builds/releases/latest"

    private val jsonFormat = Json { ignoreUnknownKeys = true }

    val downloadStatus = MutableStateFlow<DownloadStatus>(DownloadStatus.NotYet)
    var currentDownloadingRelease: Release? = null
        private set
    var readyToInstallRelease: Release? = null

    fun isDownloading(): Boolean =
        downloadStatus.value is DownloadStatus.Progress || downloadStatus.value is DownloadStatus.Connecting

    fun startBackgroundDownload(context: Context = App.context, release: Release) {
        val current = downloadStatus.value
        if (current is DownloadStatus.Progress || current is DownloadStatus.Connecting) {
            Log.d(TAG, "Download already in progress")
            return
        }

        currentDownloadingRelease = release
        downloadStatus.value = DownloadStatus.Connecting
        val versionName = (release.name?.takeIf { it.isNotBlank() } ?: release.tagName ?: "Update").toString()

        applicationScope.launch(Dispatchers.IO) {
            try {
                downloadApk(context, release).collect { status ->
                    downloadStatus.value = status
                    if (status is DownloadStatus.Finished) {
                        readyToInstallRelease = release
                        currentDownloadingRelease = null

                        val installIntent = Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra("EXTRA_INSTALL_UPDATE", true)
                        }
                        val pendingIntent = PendingIntent.getActivity(
                            context,
                            0,
                            installIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        NotificationUtil.notifyUpdateFinished(versionName, pendingIntent)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Background update download failed", e)
                downloadStatus.value = DownloadStatus.Failed(e.message ?: "Download failed")
                currentDownloadingRelease = null
                NotificationUtil.cancelUpdateNotification()
                ToastUtil.makeToastSuspend(context.getString(R.string.app_update_failed))
            }
        }
    }

    suspend fun updateYtDlp(): YoutubeDL.UpdateStatus? =
        withContext(Dispatchers.IO) {
            val channel =
                when (YT_DLP_UPDATE_CHANNEL.getInt()) {
                    YT_DLP_NIGHTLY -> YoutubeDL.UpdateChannel.NIGHTLY
                    else -> YoutubeDL.UpdateChannel.STABLE
                }

            YoutubeDL.getInstance()
                .updateYoutubeDL(appContext = context, updateChannel = channel)
                .also {
                    if (it == YoutubeDL.UpdateStatus.DONE) {
                        YoutubeDL.getInstance().version(context)?.let {
                            PreferenceUtil.encodeString(YT_DLP_VERSION, it)
                        }
                    }
                    val now = System.currentTimeMillis()
                    YT_DLP_UPDATE_TIME.updateLong(now)
                }
        }

    private fun getLatestRelease(): Release? =
        try {
            val response = client.newCall(requestForReleases).execute()
            if (!response.isSuccessful) null
            else {
                response.body.use { body ->
                    val releaseList = jsonFormat.decodeFromString<List<Release>>(body.string())
                    if (releaseList.isEmpty()) return null
                    val stable = UPDATE_CHANNEL.getInt() == STABLE
                    releaseList
                        .filter { release ->
                            val ver = (release.tagName ?: release.name).toVersion()
                            if (stable) ver is Version.Stable else true
                        }
                        .maxByOrNull { release -> (release.tagName ?: release.name).toVersion() }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

    fun checkForUpdate(context: Context = App.context): Release? {
        val currentVersion = context.getCurrentVersion()
        val latestRelease = getLatestRelease() ?: return null
        val latestVersion = (latestRelease.tagName ?: latestRelease.name).toVersion()
        return if (currentVersion < latestVersion) latestRelease else null
    }

    private fun Context.getCurrentVersion(): Version =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager
                .getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
                .versionName
                .toVersion()
        } else {
            packageManager.getPackageInfo(packageName, 0).versionName.toVersion()
        }

    fun getLatestApk(context: Context = App.context) = File(context.getExternalFilesDir("apk") ?: File(context.filesDir, "apk"), "latest.apk")

    fun installLatestApk(context: Context = App.context) {
        val apkFile = getLatestApk(context)
        if (!apkFile.exists() || apkFile.length() == 0L) {
            Log.e(TAG, "installLatestApk: APK file does not exist or is empty at ${apkFile.absolutePath}")
            ToastUtil.makeToast(R.string.app_update_failed)
            return
        }

        val archiveInfo = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
        if (archiveInfo == null) {
            Log.e(TAG, "installLatestApk: APK file is corrupt, deleting it")
            apkFile.delete()
            ToastUtil.makeToast(R.string.app_update_failed)
            return
        }

        // On Android 8.0+ (Oreo, API 26+), check REQUEST_INSTALL_PACKAGES permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                runCatching {
                    val settingsIntent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                    ToastUtil.makeToast("Please allow FlowGrab to install apps, then tap Install again")
                }.onFailure { e ->
                    Log.e(TAG, "Failed to launch ACTION_MANAGE_UNKNOWN_APP_SOURCES", e)
                }
                return
            }
        }

        runCatching {
            val contentUri = FileProvider.getUriForFile(context, context.getFileProvider(), apkFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setDataAndType(contentUri, "application/vnd.android.package-archive")
            }
            context.startActivity(intent)
        }.onFailure { throwable ->
            Log.e(TAG, "installLatestApk failed", throwable)
            ToastUtil.makeToast(R.string.app_update_failed)
        }
    }

    suspend fun deleteOutdatedApk(context: Context = App.context) =
        context.runCatching {
            val apkFile = getLatestApk(context)
            if (apkFile.exists()) {
                val apkVersion =
                    context.packageManager
                        .getPackageArchiveInfo(apkFile.absolutePath, 0)
                        ?.versionName
                        .toVersion()
                if (apkVersion <= context.getCurrentVersion()) {
                    apkFile.delete()
                }
            }
        }

    suspend fun downloadApk(
        context: Context = App.context,
        release: Release,
    ): Flow<DownloadStatus> =
        flow {
            emit(DownloadStatus.Connecting)

            val apkDir = context.getExternalFilesDir("apk")
                ?: File(context.filesDir, "apk")
            apkDir.mkdirs()

            val finalFile = File(apkDir, "latest.apk")
            val tempFile = File(apkDir, "latest.apk.part")
            val targetVersion = (release.tagName ?: release.name).toVersion()
            val versionName = (release.name?.takeIf { it.isNotBlank() } ?: release.tagName ?: "Update").toString()

            // If the latest APK already exists, is complete and valid, skip re-download
            if (finalFile.exists() && finalFile.length() > 0L) {
                val existingApkInfo = context.packageManager.getPackageArchiveInfo(finalFile.absolutePath, 0)
                val existingVersion = existingApkInfo?.versionName.toVersion()
                if (existingApkInfo != null && existingVersion != EMPTY_VERSION && existingVersion >= targetVersion) {
                    emit(DownloadStatus.Finished(finalFile))
                    return@flow
                }
            }

            // Find matching asset prioritizing device supported ABIs in order
            var matchedAsset: AssetsItem? = null
            for (abi in Build.SUPPORTED_ABIS) {
                matchedAsset = release.assets?.find { asset ->
                    val name = asset.name?.lowercase() ?: ""
                    name.endsWith(".apk") && name.contains(abi.lowercase())
                }
                if (matchedAsset != null) break
            }

            // Fallback to universal APK
            if (matchedAsset == null) {
                matchedAsset = release.assets?.find { asset ->
                    val name = asset.name?.lowercase() ?: ""
                    name.endsWith(".apk") && name.contains("universal")
                }
            }

            // Fallback to any APK asset
            if (matchedAsset == null) {
                matchedAsset = release.assets?.find { asset ->
                    asset.name?.endsWith(".apk", ignoreCase = true) == true
                }
            }

            val targetUrl = matchedAsset?.browserDownloadUrl
                ?: throw IllegalStateException("No compatible APK asset found in release")

            Log.d(TAG, "Starting download for ${matchedAsset.name} from $targetUrl")
            NotificationUtil.notifyUpdateProgress(versionName, 0)
            emit(DownloadStatus.Progress(0))

            val maxRetries = 5
            var attempt = 0
            var completed = false

            while (!completed && attempt < maxRetries) {
                attempt++
                var downloadedBytes = if (tempFile.exists()) tempFile.length() else 0L

                try {
                    val requestBuilder = Request.Builder()
                        .url(targetUrl)
                        .header("User-Agent", "FlowGrab-Android")

                    if (downloadedBytes > 0) {
                        requestBuilder.header("Range", "bytes=$downloadedBytes-")
                        Log.d(TAG, "Attempt $attempt: Requesting range from $downloadedBytes bytes")
                    } else {
                        Log.d(TAG, "Attempt $attempt: Starting fresh download")
                    }

                    val response = client.newCall(requestBuilder.build()).execute()
                    val responseCode = response.code

                    if (responseCode == 416) {
                        Log.w(TAG, "HTTP 416 Range Not Satisfiable, resetting temp file")
                        response.close()
                        tempFile.delete()
                        downloadedBytes = 0L
                        continue
                    }

                    if (!response.isSuccessful) {
                        response.close()
                        throw IOException("HTTP error $responseCode: ${response.message}")
                    }

                    val responseBody = response.body
                        ?: throw IOException("Empty response body from release download")

                    val isAppend = (responseCode == 206)
                    if (!isAppend && downloadedBytes > 0) {
                        Log.d(TAG, "Server returned 200, restarting from byte 0")
                        downloadedBytes = 0L
                    }

                    val remainingBytes = responseBody.contentLength()
                    val totalBytes = if (remainingBytes > 0) downloadedBytes + remainingBytes else -1L

                    responseBody.byteStream().use { inputStream ->
                        FileOutputStream(tempFile, isAppend).use { outputStream ->
                            val buffer = ByteArray(65_536) // 64KB high-throughput buffer
                            var lastEmitTime = 0L

                            while (true) {
                                val bytesRead = inputStream.read(buffer)
                                if (bytesRead == -1) break

                                outputStream.write(buffer, 0, bytesRead)
                                downloadedBytes += bytesRead

                                val now = System.currentTimeMillis()
                                if (totalBytes > 0) {
                                    val percent = ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                                    if (now - lastEmitTime > 300 || percent == 100) {
                                        lastEmitTime = now
                                        emit(DownloadStatus.Progress(percent))
                                        NotificationUtil.notifyUpdateProgress(versionName, percent)
                                    }
                                } else {
                                    if (now - lastEmitTime > 500) {
                                        lastEmitTime = now
                                        emit(DownloadStatus.Progress(-1))
                                    }
                                }
                            }
                            outputStream.flush()
                        }
                    }

                    if (totalBytes > 0 && downloadedBytes < totalBytes) {
                        throw IOException("Incomplete transfer: $downloadedBytes / $totalBytes bytes")
                    }

                    if (downloadedBytes == 0L) {
                        throw IOException("Downloaded 0 bytes")
                    }

                    val archiveInfo = context.packageManager.getPackageArchiveInfo(tempFile.absolutePath, 0)
                    if (archiveInfo == null) {
                        tempFile.delete()
                        throw IOException("Downloaded APK archive is corrupted or invalid")
                    }

                    if (finalFile.exists()) {
                        finalFile.delete()
                    }
                    if (!tempFile.renameTo(finalFile)) {
                        tempFile.copyTo(finalFile, overwrite = true)
                        tempFile.delete()
                    }

                    completed = true
                    Log.i(TAG, "APK successfully downloaded and validated at ${finalFile.absolutePath}")
                    emit(DownloadStatus.Finished(finalFile))

                } catch (e: Exception) {
                    Log.w(TAG, "Download attempt $attempt failed: ${e.message}")
                    if (attempt >= maxRetries) {
                        throw e
                    }
                    delay(1500L * attempt)
                }
            }

            if (!completed) {
                throw IOException("Failed to complete APK download after $maxRetries attempts")
            }
        }
        .flowOn(Dispatchers.IO)
        .distinctUntilChanged()

    @Serializable
    data class Release(
        @SerialName("html_url") val htmlUrl: String? = null,
        @SerialName("tag_name") val tagName: String? = null,
        val name: String? = null,
        val draft: Boolean? = null,
        @SerialName("prerelease") val preRelease: Boolean? = null,
        @SerialName("created_at") val createdAt: String? = null,
        @SerialName("published_at") val publishedAt: String? = null,
        val assets: List<AssetsItem>? = null,
        val body: String? = null,
    )

    @Serializable
    data class AssetsItem(
        val name: String? = null,
        @SerialName("content_type") val contentType: String? = null,
        val size: Int? = null,
        @SerialName("download_count") val downloadCount: Int? = null,
        @SerialName("created_at") val createdAt: String? = null,
        @SerialName("updated_at") val updatedAt: String? = null,
        @SerialName("browser_download_url") val browserDownloadUrl: String? = null,
    )

    sealed class DownloadStatus {
        object NotYet : DownloadStatus()
        object Connecting : DownloadStatus()
        data class Progress(val percent: Int) : DownloadStatus()
        data class Finished(val file: File) : DownloadStatus()
        data class Failed(val error: String) : DownloadStatus()
    }

    private val pattern = Pattern.compile("""v?(\d+)\.(\d+)\.(\d+)(-(\w+)\.(\d+))?""")
    private val EMPTY_VERSION = Version.Stable()

    fun String?.toVersion(): Version =
        this?.run {
            val matcher = pattern.matcher(this)
            if (matcher.find()) {
                val major = matcher.group(1)?.toInt() ?: 0
                val minor = matcher.group(2)?.toInt() ?: 0
                val patch = matcher.group(3)?.toInt() ?: 0
                val buildNumber = matcher.group(6)?.toInt() ?: 0
                when (matcher.group(5)) {
                    "alpha" -> Version.Alpha(major, minor, patch, buildNumber)
                    "beta" -> Version.Beta(major, minor, patch, buildNumber)
                    "rc" -> Version.ReleaseCandidate(major, minor, patch, buildNumber)
                    else -> Version.Stable(major, minor, patch)
                }
            } else EMPTY_VERSION
        } ?: EMPTY_VERSION

    sealed class Version(val major: Int, val minor: Int, val patch: Int, val build: Int = 0) :
        Comparable<Version> {
        companion object {
            // private const val ABI = 1L
            private const val BUILD = 10L
            private const val VARIANT = 100L
            private const val PATCH = 10_000L
            private const val MINOR = 1_000_000L
            private const val MAJOR = 100_000_000L

            private const val STABLE = VARIANT * 4
            private const val ALPHA = VARIANT * 1
            private const val BETA = VARIANT * 2
            private const val RELEASE_CANDIDATE = VARIANT * 3
        }

        abstract fun toVersionName(): String

        abstract fun toNumber(): Long

        class Alpha(
            versionMajor: Int = 0,
            versionMinor: Int = 0,
            versionPatch: Int = 0,
            versionBuild: Int = 0,
        ) : Version(versionMajor, versionMinor, versionPatch, versionBuild) {
            override fun toVersionName(): String = "${major}.${minor}.${patch}-alpha.$build"

            override fun toNumber(): Long =
                major * MAJOR + minor * MINOR + patch * PATCH + build * BUILD + ALPHA
        }

        class Beta(versionMajor: Int, versionMinor: Int, versionPatch: Int, versionBuild: Int) :
            Version(versionMajor, versionMinor, versionPatch, versionBuild) {
            override fun toVersionName(): String = "${major}.${minor}.${patch}-beta.$build"

            override fun toNumber(): Long =
                major * MAJOR + minor * MINOR + patch * PATCH + build * BUILD + BETA
        }

        class ReleaseCandidate(
            versionMajor: Int,
            versionMinor: Int,
            versionPatch: Int,
            versionBuild: Int,
        ) : Version(versionMajor, versionMinor, versionPatch, versionBuild) {
            override fun toVersionName(): String = "${major}.${minor}.${patch}-rc.$build"

            override fun toNumber(): Long =
                major * MAJOR + minor * MINOR + patch * PATCH + build * BUILD + RELEASE_CANDIDATE
        }

        class Stable(versionMajor: Int = 0, versionMinor: Int = 0, versionPatch: Int = 0) :
            Version(versionMajor, versionMinor, versionPatch) {
            override fun toVersionName(): String = "${major}.${minor}.${patch}"

            override fun toNumber(): Long =
                major * MAJOR + minor * MINOR + patch * PATCH + build * BUILD + STABLE
            // Prioritize stable versions

        }

        override operator fun compareTo(other: Version): Int =
            this.toNumber().compareTo(other.toNumber())
    }
}
