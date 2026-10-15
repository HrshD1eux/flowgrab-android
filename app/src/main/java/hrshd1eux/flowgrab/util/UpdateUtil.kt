package hrshd1eux.flowgrab.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
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
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.regex.Pattern
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
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
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

    private val requestForLatestRelease =
        Request.Builder()
            .url("https://api.github.com/repos/${OWNER}/${REPO}/releases/latest")
            .header("User-Agent", "FlowGrab-Android")
            .header("Accept", "application/vnd.github.v3+json")
            .build()

    private val requestForReleases =
        Request.Builder()
            .url("https://api.github.com/repos/${OWNER}/${REPO}/releases")
            .header("User-Agent", "FlowGrab-Android")
            .header("Accept", "application/vnd.github.v3+json")
            .build()

    private const val ytdlpNightlyBuildRelease =
        "https://api.github.com/repos/yt-dlp/yt-dlp-nightly-builds/releases/latest"

    private val jsonFormat = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

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
            val isValid = isApkValid(context, finalFile)
            val existingApkInfo = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageArchiveInfo(finalFile.absolutePath, PackageManager.PackageInfoFlags.of(0))
                } else {
                    context.packageManager.getPackageArchiveInfo(finalFile.absolutePath, 0)
                }
            }.getOrNull()
            val existingVersion = existingApkInfo?.versionName.toVersion()
            if (isValid && (existingVersion == EMPTY_VERSION || existingVersion >= targetVersion)) {
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

        App.startService()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = runCatching {
            powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FlowGrab:UpdateWakeLock")?.apply {
                acquire(15 * 60 * 1000L /* 15 mins max */)
            }
        }.getOrNull()

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
            } finally {
                runCatching {
                    if (wakeLock?.isHeld == true) wakeLock.release()
                }
                App.stopService()
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
            client.newCall(requestForReleases).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.let { body ->
                        val releaseList = jsonFormat.decodeFromString<List<Release>>(body.string())
                        if (releaseList.isNotEmpty()) {
                            val stable = UPDATE_CHANNEL.getInt() == STABLE
                            return releaseList
                                .filter { release ->
                                    val ver = (release.tagName ?: release.name).toVersion()
                                    if (stable) ver is Version.Stable else true
                                }
                                .maxByOrNull { release -> (release.tagName ?: release.name).toVersion() }
                        }
                    }
                }
            }
            // Fallback to releases/latest if list endpoint is throttled or empty
            client.newCall(requestForLatestRelease).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.let { body ->
                        return jsonFormat.decodeFromString<Release>(body.string())
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "getLatestRelease failed", e)
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

    fun isApkValid(context: Context, apkFile: File): Boolean {
        if (!apkFile.exists() || apkFile.length() == 0L) return false
        val archiveInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageArchiveInfo(
                    apkFile.absolutePath,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
            }
        }.getOrNull()

        if (archiveInfo != null && (archiveInfo.packageName?.startsWith("hrshd1eux.flowgrab") == true || archiveInfo.packageName == context.packageName)) {
            return true
        }

        // Fallback: Verify file is a valid zip archive containing AndroidManifest.xml and classes.dex
        return runCatching {
            java.util.zip.ZipFile(apkFile).use { zip ->
                zip.getEntry("AndroidManifest.xml") != null && zip.getEntry("classes.dex") != null
            }
        }.getOrDefault(false)
    }

    enum class SignatureMatch {
        MATCH,
        MISMATCH,
        UNKNOWN
    }

    fun checkSignatureMatch(context: Context, apkFile: File): SignatureMatch {
        return runCatching {
            val pm = context.packageManager
            val installedSignatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
            }?.map { it.toByteArray() }

            if (installedSignatures.isNullOrEmpty()) return SignatureMatch.UNKNOWN

            val archiveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNATURES)
            }

            val apkSignatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                archiveInfo?.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                archiveInfo?.signatures
            }?.map { it.toByteArray() }

            if (apkSignatures.isNullOrEmpty()) return SignatureMatch.UNKNOWN

            val installedHashes = installedSignatures.map { it.contentHashCode() }.toSet()
            val apkHashes = apkSignatures.map { it.contentHashCode() }.toSet()

            if (installedHashes == apkHashes) SignatureMatch.MATCH else SignatureMatch.MISMATCH
        }.getOrDefault(SignatureMatch.UNKNOWN)
    }

    fun installLatestApk(context: Context = App.context) {
        val apkFile = getLatestApk(context)
        if (!apkFile.exists() || apkFile.length() == 0L) {
            Log.e(TAG, "installLatestApk: APK file does not exist or is empty at ${apkFile.absolutePath}")
            ToastUtil.makeToast(R.string.app_update_failed)
            return
        }

        if (!isApkValid(context, apkFile)) {
            Log.e(TAG, "installLatestApk: APK file is corrupt, deleting it")
            apkFile.delete()
            ToastUtil.makeToast(R.string.app_update_failed)
            return
        }

        val sigMatch = checkSignatureMatch(context, apkFile)
        if (sigMatch == SignatureMatch.MISMATCH) {
            Log.w(TAG, "Signature mismatch detected between installed app and update APK")
            ToastUtil.makeToast("Signature mismatch: Installed build uses a different signing key. Please uninstall the debug build first.")
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
            val resInfoList = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            for (resolveInfo in resInfoList) {
                context.grantUriPermission(resolveInfo.activityInfo.packageName, contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
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
                val apkInfo = runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.PackageInfoFlags.of(0))
                    } else {
                        context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
                    }
                }.getOrNull()
                val apkVersion = apkInfo?.versionName.toVersion()
                if (apkVersion != EMPTY_VERSION && apkVersion <= context.getCurrentVersion()) {
                    apkFile.delete()
                }
            }
        }

    suspend fun downloadApk(
        context: Context = App.context,
        release: Release,
    ): Flow<DownloadStatus> =
        channelFlow {
            send(DownloadStatus.Connecting)

            val apkDir = context.getExternalFilesDir("apk")
                ?: File(context.filesDir, "apk")
            apkDir.mkdirs()

            val finalFile = File(apkDir, "latest.apk")
            val tempFile = File(apkDir, "latest.apk.part")
            val targetVersion = (release.tagName ?: release.name).toVersion()
            val versionName = (release.name?.takeIf { it.isNotBlank() } ?: release.tagName ?: "Update").toString()

            // 1. Instant check: If the latest APK already exists, is complete and valid, skip re-download
            if (finalFile.exists() && finalFile.length() > 0L) {
                val isValid = isApkValid(context, finalFile)
                val existingApkInfo = runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        context.packageManager.getPackageArchiveInfo(finalFile.absolutePath, PackageManager.PackageInfoFlags.of(0))
                    } else {
                        context.packageManager.getPackageArchiveInfo(finalFile.absolutePath, 0)
                    }
                }.getOrNull()
                val existingVersion = existingApkInfo?.versionName.toVersion()
                if (isValid && (existingVersion == EMPTY_VERSION || existingVersion >= targetVersion)) {
                    Log.i(TAG, "Instant install ready: Valid cached APK found at ${finalFile.absolutePath}")
                    send(DownloadStatus.Progress(100))
                    send(DownloadStatus.Finished(finalFile))
                    return@channelFlow
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

            Log.d(TAG, "Starting download for ${matchedAsset.name} from $targetUrl")
            NotificationUtil.notifyUpdateProgress(versionName, 0)
            send(DownloadStatus.Progress(0))

            // 3. Fast-path: GitHub API already returns the exact asset size, eliminating redundant network probe delays
            var contentLength = matchedAsset.size ?: -1L
            var supportsRange = (contentLength > 4 * 1024 * 1024)
            var directUrl = targetUrl

            // Only probe over network if GitHub API did not include size
            if (contentLength <= 0L) {
                try {
                    val probeReq = Request.Builder()
                        .url(targetUrl)
                        .header("User-Agent", "FlowGrab-Android")
                        .header("Range", "bytes=0-0")
                        .build()
                    client.newCall(probeReq).execute().use { probeResp ->
                        directUrl = probeResp.request.url.toString()
                        if (probeResp.code == 206) {
                            supportsRange = true
                            val contentRange = probeResp.header("Content-Range")
                            contentRange?.substringAfterLast('/')?.toLongOrNull()?.let {
                                contentLength = it
                            }
                        } else if (probeResp.isSuccessful) {
                            val acceptRanges = probeResp.header("Accept-Ranges")
                            supportsRange = acceptRanges?.contains("bytes", ignoreCase = true) == true
                            contentLength = probeResp.header("Content-Length")?.toLongOrNull() ?: -1L
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Range probe failed, attempting HEAD request", e)
                    try {
                        val headReq = Request.Builder()
                            .url(targetUrl)
                            .header("User-Agent", "FlowGrab-Android")
                            .head()
                            .build()
                        client.newCall(headReq).execute().use { headResp ->
                            directUrl = headResp.request.url.toString()
                            if (headResp.isSuccessful) {
                                contentLength = headResp.header("Content-Length")?.toLongOrNull() ?: -1L
                                val acceptRanges = headResp.header("Accept-Ranges")
                                supportsRange = acceptRanges?.contains("bytes", ignoreCase = true) == true
                            }
                        }
                    } catch (headEx: Exception) {
                        Log.w(TAG, "HEAD probe failed as well", headEx)
                    }
                }
            }

            Log.i(TAG, "APK size: $contentLength bytes, supportsRange: $supportsRange, directUrl: $directUrl")

            var downloadedSuccessfully = false

            // 4. Execution: Parallel Isolated Chunks if Range is supported and file is > 4MB
            if (supportsRange && contentLength > 4 * 1024 * 1024) {
                try {
                    downloadParallelIsolatedChunks(
                        context = context,
                        targetUrl = directUrl,
                        contentLength = contentLength,
                        apkDir = apkDir,
                        finalFile = finalFile,
                        versionName = versionName,
                    )
                    downloadedSuccessfully = true
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w(TAG, "Parallel isolated chunks failed, falling back to single stream", e)
                }
            }

            // Fallback to single-connection download if parallel wasn't attempted or failed
            if (!downloadedSuccessfully) {
                downloadSingleStream(
                    context = context,
                    targetUrl = directUrl,
                    tempFile = tempFile,
                    finalFile = finalFile,
                    versionName = versionName,
                )
            }

            send(DownloadStatus.Finished(finalFile))
        }
        .flowOn(Dispatchers.IO)
        .distinctUntilChanged()

    private fun cleanChunkFiles(apkDir: File) {
        runCatching {
            apkDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("latest.apk.chunk_") || file.name == "latest.apk.temp" || file.name == "latest.apk.part") {
                    file.delete()
                }
            }
        }
    }

    private suspend fun ProducerScope<DownloadStatus>.downloadParallelIsolatedChunks(
        context: Context,
        targetUrl: String,
        contentLength: Long,
        apkDir: File,
        finalFile: File,
        versionName: String,
    ) {
        val numWorkers = 4
        val chunkSize = (contentLength + numWorkers - 1) / numWorkers
        val downloadedBytes = AtomicLong(0L)
        val chunkFiles = (0 until numWorkers).map { File(apkDir, "latest.apk.chunk_$it") }
        val metaFile = File(apkDir, "latest.apk.meta")
        val targetMeta = "$targetUrl\n$contentLength"

        val metaMatches = metaFile.exists() && runCatching { metaFile.readText().trim() == targetMeta.trim() }.getOrDefault(false)
        if (!metaMatches) {
            cleanChunkFiles(apkDir)
            runCatching { metaFile.writeText(targetMeta) }
        }

        // 1. Calculate bytes already safely persisted to disk across existing chunks
        var initialDownloaded = 0L
        for (i in 0 until numWorkers) {
            val partFile = chunkFiles[i]
            val start = i * chunkSize
            val end = minOf((i + 1) * chunkSize - 1, contentLength - 1)
            val expected = end - start + 1
            if (partFile.exists()) {
                val len = partFile.length()
                if (len in 1..expected) {
                    initialDownloaded += len
                } else if (len > expected) {
                    partFile.delete()
                }
            }
        }
        downloadedBytes.set(initialDownloaded)
        if (initialDownloaded > 0) {
            val initialPercent = ((initialDownloaded * 100) / contentLength).toInt().coerceIn(0, 99)
            Log.i(TAG, "Resuming parallel isolated chunks from $initialDownloaded bytes ($initialPercent%)")
            trySend(DownloadStatus.Progress(initialPercent))
            NotificationUtil.notifyUpdateProgress(versionName, initialPercent)
        }

        coroutineScope {
            val progressJob = launch(Dispatchers.IO) {
                var lastPercent = -1
                while (isActive) {
                    delay(150)
                    val totalNow = downloadedBytes.get()
                    val percent = ((totalNow * 100) / contentLength).toInt().coerceIn(0, 100)
                    if (percent != lastPercent) {
                        lastPercent = percent
                        trySend(DownloadStatus.Progress(percent))
                        NotificationUtil.notifyUpdateProgress(versionName, percent)
                    }
                }
            }

            try {
                val deferreds = (0 until numWorkers).map { workerIndex ->
                    async(Dispatchers.IO) {
                        val start = workerIndex * chunkSize
                        val end = minOf((workerIndex + 1) * chunkSize - 1, contentLength - 1)
                        val expectedBytes = end - start + 1
                        val partFile = chunkFiles[workerIndex]

                        var success = false
                        var attempt = 0
                        val maxRetries = 6

                        while (!success && attempt < maxRetries && isActive) {
                            attempt++
                            val existingBytes = if (partFile.exists()) partFile.length() else 0L
                            if (existingBytes == expectedBytes) {
                                success = true
                                break
                            }
                            if (existingBytes > expectedBytes) {
                                partFile.delete()
                                downloadedBytes.addAndGet(-existingBytes)
                            }

                            val currentOffset = if (partFile.exists()) partFile.length() else 0L
                            val requestStart = start + currentOffset
                            var bytesWrittenThisAttempt = 0L

                            try {
                                val request = Request.Builder()
                                    .url(targetUrl)
                                    .header("User-Agent", "FlowGrab-Android")
                                    .header("Range", "bytes=$requestStart-$end")
                                    .build()

                                client.newCall(request).execute().use { response ->
                                    if (response.code == 416) {
                                        partFile.delete()
                                        downloadedBytes.addAndGet(-currentOffset)
                                        throw IOException("Worker $workerIndex range 416, resetting chunk")
                                    }
                                    if (response.code != 206) {
                                        throw IOException("Worker $workerIndex received HTTP ${response.code} instead of 206")
                                    }

                                    val body = response.body
                                        ?: throw IOException("Worker $workerIndex received empty body")

                                    body.byteStream().use { inputStream ->
                                        BufferedOutputStream(FileOutputStream(partFile, true), 131_072).use { outputStream ->
                                            val buffer = ByteArray(65_536)
                                            var bytesRead: Int
                                            val remainingBytes = expectedBytes - currentOffset

                                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                                val toWrite = minOf(bytesRead.toLong(), remainingBytes - bytesWrittenThisAttempt).toInt()
                                                if (toWrite <= 0) break

                                                outputStream.write(buffer, 0, toWrite)
                                                bytesWrittenThisAttempt += toWrite
                                                downloadedBytes.addAndGet(toWrite.toLong())

                                                if (bytesWrittenThisAttempt >= remainingBytes) break
                                            }
                                            outputStream.flush()
                                        }
                                    }
                                }

                                if (partFile.length() == expectedBytes) {
                                    success = true
                                } else {
                                    throw IOException("Worker $workerIndex chunk incomplete: ${partFile.length()} vs $expectedBytes")
                                }
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                Log.w(TAG, "Worker $workerIndex attempt $attempt failed: ${e.message}")
                                if (attempt >= maxRetries) throw e
                                delay(1000L * attempt)
                            }
                        }
                    }
                }

                deferreds.awaitAll()
            } finally {
                progressJob.cancel()
            }
        }

        // Concatenate all isolated chunk files into finalFile
        val tempMergedFile = File(apkDir, "latest.apk.temp")
        if (tempMergedFile.exists()) tempMergedFile.delete()

        FileOutputStream(tempMergedFile).channel.use { outChannel ->
            for (chunkFile in chunkFiles) {
                if (!chunkFile.exists() || chunkFile.length() == 0L) {
                    throw IOException("Chunk file ${chunkFile.name} is missing or empty")
                }
                FileInputStream(chunkFile).channel.use { inChannel ->
                    var transferred = 0L
                    val size = inChannel.size()
                    while (transferred < size) {
                        val bytes = inChannel.transferTo(transferred, size - transferred, outChannel)
                        if (bytes <= 0) break
                        transferred += bytes
                    }
                }
            }
        }

        if (tempMergedFile.length() != contentLength) {
            tempMergedFile.delete()
            throw IOException("Merged file size mismatch: ${tempMergedFile.length()} vs $contentLength")
        }

        if (finalFile.exists()) finalFile.delete()
        if (!tempMergedFile.renameTo(finalFile)) {
            tempMergedFile.copyTo(finalFile, overwrite = true)
            tempMergedFile.delete()
        }

        if (!isApkValid(context, finalFile)) {
            finalFile.delete()
            cleanChunkFiles(apkDir)
            metaFile.delete()
            throw IOException("Downloaded APK archive is corrupted or invalid")
        }

        cleanChunkFiles(apkDir)
        metaFile.delete()

        send(DownloadStatus.Progress(100))
        NotificationUtil.notifyUpdateProgress(versionName, 100)
        Log.i(TAG, "Parallel isolated chunks download complete & validated at ${finalFile.absolutePath}")
    }

    private suspend fun ProducerScope<DownloadStatus>.downloadSingleStream(
        context: Context,
        targetUrl: String,
        tempFile: File,
        finalFile: File,
        versionName: String,
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
                    val code = responseCode
                    val msg = response.message
                    response.close()
                    throw IOException("HTTP error $code: $msg")
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
                    BufferedOutputStream(FileOutputStream(tempFile, isAppend), 131_072).use { outputStream ->
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
                                if (now - lastEmitTime > 200 || percent == 100) {
                                    lastEmitTime = now
                                    trySend(DownloadStatus.Progress(percent))
                                    NotificationUtil.notifyUpdateProgress(versionName, percent)
                                }
                            } else {
                                if (now - lastEmitTime > 500) {
                                    lastEmitTime = now
                                    trySend(DownloadStatus.Progress(-1))
                                }
                            }
                        }
                        outputStream.flush()
                    }
                }

                if (totalBytes > 0 && downloadedBytes < totalBytes) {
                    throw IOException("Incomplete transfer: $downloadedBytes / $totalBytes bytes")
                }

                if (finalFile.exists()) {
                    finalFile.delete()
                }
                if (!tempFile.renameTo(finalFile)) {
                    tempFile.copyTo(finalFile, overwrite = true)
                    tempFile.delete()
                }

                if (!isApkValid(context, finalFile)) {
                    finalFile.delete()
                    throw IOException("Downloaded APK archive is corrupted or invalid")
                }

                send(DownloadStatus.Progress(100))
                NotificationUtil.notifyUpdateProgress(versionName, 100)
                completed = true
            } catch (e: Exception) {
                if (e is CancellationException) throw e
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
        val size: Long? = null,
        @SerialName("download_count") val downloadCount: Long? = null,
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
