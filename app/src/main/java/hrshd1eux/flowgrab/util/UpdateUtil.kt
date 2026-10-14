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
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.regex.Pattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
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
            .dispatcher(Dispatcher().apply {
                maxRequests = 32
                maxRequestsPerHost = 16
            })
            .connectionPool(ConnectionPool(8, 2, TimeUnit.MINUTES))
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

    fun prefetchLatestApk(context: Context = App.context, release: Release) {
        val current = downloadStatus.value
        if (current is DownloadStatus.Progress || current is DownloadStatus.Connecting || current is DownloadStatus.Finished) {
            return
        }

        val apkDir = context.getExternalFilesDir("apk") ?: File(context.filesDir, "apk")
        val finalFile = File(apkDir, "latest.apk")
        val targetVersion = (release.tagName ?: release.name).toVersion()

        // If already downloaded and valid, mark ready immediately
        if (finalFile.exists() && finalFile.length() > 0L) {
            val existingApkInfo = context.packageManager.getPackageArchiveInfo(finalFile.absolutePath, 0)
            val existingVersion = existingApkInfo?.versionName.toVersion()
            if (existingApkInfo != null && existingVersion != EMPTY_VERSION && existingVersion >= targetVersion) {
                downloadStatus.value = DownloadStatus.Finished(finalFile)
                readyToInstallRelease = release
                return
            }
        }

        Log.d(TAG, "Starting silent background pre-fetch for release ${release.tagName}")
        startBackgroundDownload(context, release, silent = true)
    }

    fun startBackgroundDownload(
        context: Context = App.context,
        release: Release,
        silent: Boolean = false,
    ) {
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
                if (!silent) {
                    ToastUtil.makeToastSuspend(context.getString(R.string.app_update_failed))
                }
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

            // 1. Instant check: If the latest APK already exists, is complete and valid, skip re-download
            if (finalFile.exists() && finalFile.length() > 0L) {
                val existingApkInfo = context.packageManager.getPackageArchiveInfo(finalFile.absolutePath, 0)
                val existingVersion = existingApkInfo?.versionName.toVersion()
                if (existingApkInfo != null && existingVersion != EMPTY_VERSION && existingVersion >= targetVersion) {
                    Log.i(TAG, "Instant install ready: Valid cached APK found at ${finalFile.absolutePath}")
                    emit(DownloadStatus.Progress(100))
                    emit(DownloadStatus.Finished(finalFile))
                    return@flow
                }
            }

            // 2. Strict ABI selection: Find matching asset prioritizing device supported ABIs in order
            var matchedAsset: AssetsItem? = null
            for (abi in Build.SUPPORTED_ABIS) {
                matchedAsset = release.assets?.find { asset ->
                    val name = asset.name?.lowercase() ?: ""
                    name.endsWith(".apk") && name.contains(abi.lowercase())
                }
                if (matchedAsset != null) break
            }

            // Fallback to universal APK only if no ABI-specific APK matched
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

            Log.d(TAG, "Starting parallel download for ${matchedAsset.name} from $targetUrl")
            NotificationUtil.notifyUpdateProgress(versionName, 0)
            emit(DownloadStatus.Progress(0))

            // 3. Probe file size and Range header support with HEAD request
            var contentLength = -1L
            var supportsRange = false
            try {
                val headReq = Request.Builder()
                    .url(targetUrl)
                    .header("User-Agent", "FlowGrab-Android")
                    .head()
                    .build()
                client.newCall(headReq).execute().use { headResp ->
                    if (headResp.isSuccessful) {
                        contentLength = headResp.header("Content-Length")?.toLongOrNull() ?: -1L
                        val acceptRanges = headResp.header("Accept-Ranges")
                        supportsRange = acceptRanges?.contains("bytes", ignoreCase = true) == true
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "HEAD probe failed, will attempt Range probe with GET", e)
            }

            // If HEAD didn't return Content-Length, probe with a tiny range GET (0-1)
            if (contentLength <= 0L) {
                try {
                    val probeReq = Request.Builder()
                        .url(targetUrl)
                        .header("User-Agent", "FlowGrab-Android")
                        .header("Range", "bytes=0-1")
                        .build()
                    client.newCall(probeReq).execute().use { probeResp ->
                        if (probeResp.code == 206) {
                            supportsRange = true
                            val contentRange = probeResp.header("Content-Range")
                            contentRange?.substringAfterLast('/')?.toLongOrNull()?.let {
                                contentLength = it
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "GET range probe failed", e)
                }
            }

            Log.i(TAG, "APK size: $contentLength bytes, supportsRange: $supportsRange")

            // 4. Execution: Parallel Chunked Download if Range is supported and file is > 4MB
            if (supportsRange && contentLength > 4 * 1024 * 1024) {
                downloadParallelChunks(
                    context = context,
                    targetUrl = targetUrl,
                    contentLength = contentLength,
                    tempFile = tempFile,
                    finalFile = finalFile,
                    versionName = versionName,
                    onProgress = { percent ->
                        emit(DownloadStatus.Progress(percent))
                    }
                )
            } else {
                // Fallback to single-connection download with Range resume
                downloadSingleStream(
                    context = context,
                    targetUrl = targetUrl,
                    tempFile = tempFile,
                    finalFile = finalFile,
                    versionName = versionName,
                    onProgress = { percent ->
                        emit(DownloadStatus.Progress(percent))
                    }
                )
            }

            emit(DownloadStatus.Finished(finalFile))
        }
        .flowOn(Dispatchers.IO)
        .distinctUntilChanged()

    private suspend fun downloadParallelChunks(
        context: Context,
        targetUrl: String,
        contentLength: Long,
        tempFile: File,
        finalFile: File,
        versionName: String,
        onProgress: suspend (Int) -> Unit,
    ) {
        val numWorkers = 6
        val chunkSize = (contentLength + numWorkers - 1) / numWorkers

        RandomAccessFile(tempFile, "rw").use { raf ->
            raf.setLength(contentLength)
        }

        val downloadedBytes = AtomicLong(0L)
        val lastEmitTime = AtomicLong(0L)

        coroutineScope {
            val deferreds = (0 until numWorkers).map { workerIndex ->
                async(Dispatchers.IO) {
                    val start = workerIndex * chunkSize
                    val end = minOf((workerIndex + 1) * chunkSize - 1, contentLength - 1)
                    val expectedBytes = end - start + 1

                    var success = false
                    var attempt = 0
                    val maxRetries = 5

                    while (!success && attempt < maxRetries) {
                        attempt++
                        try {
                            val request = Request.Builder()
                                .url(targetUrl)
                                .header("User-Agent", "FlowGrab-Android")
                                .header("Range", "bytes=$start-$end")
                                .build()

                            client.newCall(request).execute().use { response ->
                                if (response.code != 206 && response.code != 200) {
                                    throw IOException("Worker $workerIndex received HTTP ${response.code}")
                                }

                                val body = response.body
                                    ?: throw IOException("Worker $workerIndex received empty body")

                                body.byteStream().use { inputStream ->
                                    RandomAccessFile(tempFile, "rw").use { raf ->
                                        raf.seek(start)
                                        val buffer = ByteArray(65_536)
                                        var bytesRead: Int
                                        var workerWritten = 0L

                                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                            raf.write(buffer, 0, bytesRead)
                                            workerWritten += bytesRead
                                            val totalNow = downloadedBytes.addAndGet(bytesRead.toLong())

                                            val now = System.currentTimeMillis()
                                            if (now - lastEmitTime.get() > 250 || totalNow >= contentLength) {
                                                lastEmitTime.set(now)
                                                val percent = ((totalNow * 100) / contentLength).toInt().coerceIn(0, 100)
                                                onProgress(percent)
                                                NotificationUtil.notifyUpdateProgress(versionName, percent)
                                            }
                                        }

                                        if (workerWritten < expectedBytes) {
                                            throw IOException("Worker $workerIndex incomplete: wrote $workerWritten of $expectedBytes")
                                        }
                                    }
                                }
                            }
                            success = true
                        } catch (e: Exception) {
                            Log.w(TAG, "Worker $workerIndex attempt $attempt failed: ${e.message}")
                            if (attempt >= maxRetries) throw e
                            delay(1000L * attempt)
                        }
                    }
                }
            }

            deferreds.awaitAll()
        }

        if (tempFile.length() != contentLength) {
            tempFile.delete()
            throw IOException("File size mismatch after parallel download: ${tempFile.length()} vs $contentLength")
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

        Log.i(TAG, "Parallel download complete & validated at ${finalFile.absolutePath}")
    }

    private suspend fun downloadSingleStream(
        context: Context,
        targetUrl: String,
        tempFile: File,
        finalFile: File,
        versionName: String,
        onProgress: suspend (Int) -> Unit,
    ) {
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
                }

                val response = client.newCall(requestBuilder.build()).execute()
                val responseCode = response.code

                if (responseCode == 416) {
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
                    downloadedBytes = 0L
                }

                val remainingBytes = responseBody.contentLength()
                val totalBytes = if (remainingBytes > 0) downloadedBytes + remainingBytes else -1L

                responseBody.byteStream().use { inputStream ->
                    FileOutputStream(tempFile, isAppend).use { outputStream ->
                        val buffer = ByteArray(65_536)
                        var lastEmitTime = 0L

                        while (true) {
                            val bytesRead = inputStream.read(buffer)
                            if (bytesRead == -1) break

                            outputStream.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            val now = System.currentTimeMillis()
                            if (totalBytes > 0) {
                                val percent = ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                                if (now - lastEmitTime > 250 || percent == 100) {
                                    lastEmitTime = now
                                    onProgress(percent)
                                    NotificationUtil.notifyUpdateProgress(versionName, percent)
                                }
                            } else {
                                if (now - lastEmitTime > 500) {
                                    lastEmitTime = now
                                    onProgress(-1)
                                }
                            }
                        }
                        outputStream.flush()
                    }
                }

                if (totalBytes > 0 && downloadedBytes < totalBytes) {
                    throw IOException("Incomplete transfer: $downloadedBytes / $totalBytes bytes")
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
            } catch (e: Exception) {
                Log.w(TAG, "Single-stream attempt $attempt failed: ${e.message}")
                if (attempt >= maxRetries) throw e
                delay(1500L * attempt)
            }
        }

        if (!completed) {
            throw IOException("Failed to complete APK download after $maxRetries attempts")
        }
    }

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
