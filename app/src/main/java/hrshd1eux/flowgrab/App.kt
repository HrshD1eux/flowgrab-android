package hrshd1eux.flowgrab

import android.annotation.SuppressLint
import android.app.Application
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.content.getSystemService
import com.google.android.material.color.DynamicColors
import hrshd1eux.flowgrab.download.DownloaderV2
import hrshd1eux.flowgrab.download.DownloaderV2Impl
import hrshd1eux.flowgrab.ui.page.downloadv2.configure.DownloadDialogViewModel
import hrshd1eux.flowgrab.ui.page.settings.directory.Directory
import hrshd1eux.flowgrab.ui.page.settings.network.CookiesViewModel
import hrshd1eux.flowgrab.ui.page.videolist.VideoListViewModel
import hrshd1eux.flowgrab.util.AUDIO_DIRECTORY
import hrshd1eux.flowgrab.util.COMMAND_DIRECTORY
import hrshd1eux.flowgrab.util.DownloadUtil
import hrshd1eux.flowgrab.util.FileUtil
import hrshd1eux.flowgrab.util.FileUtil.createEmptyFile
import hrshd1eux.flowgrab.util.FileUtil.getCookiesFile
import hrshd1eux.flowgrab.util.FileUtil.getExternalDownloadDirectory
import hrshd1eux.flowgrab.util.FileUtil.getExternalPrivateDownloadDirectory
import hrshd1eux.flowgrab.util.NotificationUtil
import hrshd1eux.flowgrab.util.PreferenceUtil
import hrshd1eux.flowgrab.util.PreferenceUtil.getString
import hrshd1eux.flowgrab.util.PreferenceUtil.updateString
import hrshd1eux.flowgrab.util.SDCARD_URI
import hrshd1eux.flowgrab.util.UpdateUtil
import hrshd1eux.flowgrab.util.VIDEO_DIRECTORY
import hrshd1eux.flowgrab.util.YT_DLP_VERSION
import com.tencent.mmkv.MMKV
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        MMKV.initialize(this)

        startKoin {
            androidLogger(org.koin.core.logger.Level.ERROR)
            androidContext(this@App)
            modules(
                module {
                    single<DownloaderV2> { DownloaderV2Impl(androidContext()) }
                    viewModel { DownloadDialogViewModel(downloader = get()) }
                    viewModel { CookiesViewModel() }
                    viewModel { VideoListViewModel() }
                }
            )
        }

        context = applicationContext
        packageInfo =
            packageManager.run {
                if (Build.VERSION.SDK_INT >= 33)
                    getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
                else getPackageInfo(packageName, 0)
            }
        applicationScope = CoroutineScope(SupervisorJob())
        DynamicColors.applyToActivitiesIfAvailable(this)

        clipboard = getSystemService()!!
        connectivityManager = getSystemService()!!

        applicationScope.launch(Dispatchers.IO) {
            try {
                YoutubeDL.init(this@App)
                FFmpeg.init(this@App)
                Aria2c.init(this@App)
                runCatching {
                    val ver = YoutubeDL.getInstance().version(this@App)
                    if (!ver.isNullOrBlank()) {
                        PreferenceUtil.encodeString(YT_DLP_VERSION, ver)
                    }
                }
                initializationDeferred.complete(true)
                DownloadUtil.getCookiesContentFromDatabase().getOrNull()?.let {
                    FileUtil.writeContentToFile(it, getCookiesFile())
                }
                UpdateUtil.deleteOutdatedApk()
            } catch (th: Throwable) {
                initializationDeferred.completeExceptionally(th)
                withContext(Dispatchers.Main) { startCrashReportActivity(th) }
            }
        }

        videoDownloadDir = VIDEO_DIRECTORY.getString(getExternalDownloadDirectory().absolutePath)

        audioDownloadDir = AUDIO_DIRECTORY.getString(File(videoDownloadDir, "Audio").absolutePath)
        if (!PreferenceUtil.containsKey(COMMAND_DIRECTORY)) {
            COMMAND_DIRECTORY.updateString(videoDownloadDir)
        }
        if (Build.VERSION.SDK_INT >= 26) NotificationUtil.createNotificationChannel()

        Thread.setDefaultUncaughtExceptionHandler { _, e -> startCrashReportActivity(e) }
    }

    private fun startCrashReportActivity(th: Throwable) {
        th.printStackTrace()
        startActivity(
            Intent(this, CrashReportActivity::class.java)
                .setAction("$packageName.error_report")
                .apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra("error_report", getVersionReport() + "\n" + th.stackTraceToString())
                }
        )
    }

    companion object {
        private val initializationDeferred = kotlinx.coroutines.CompletableDeferred<Boolean>()

        suspend fun awaitInitialization() {
            try {
                initializationDeferred.await()
            } catch (e: Exception) {
                android.util.Log.e("App", "YoutubeDL initialization failed or delayed", e)
            }
        }

        fun isInitialized(): Boolean = initializationDeferred.isCompleted

        lateinit var clipboard: ClipboardManager
        lateinit var videoDownloadDir: String
        lateinit var audioDownloadDir: String
        lateinit var applicationScope: CoroutineScope
        lateinit var connectivityManager: ConnectivityManager
        lateinit var packageInfo: PackageInfo

        var isServiceRunning = false

        private val connection =
            object : ServiceConnection {
                override fun onServiceConnected(className: ComponentName, service: IBinder) {
                    val binder = service as DownloadService.DownloadServiceBinder
                    isServiceRunning = true
                }

                override fun onServiceDisconnected(arg0: ComponentName) {}
            }

        fun startService() {
            if (isServiceRunning) return
            try {
                isServiceRunning = true
                val intent = Intent(context.applicationContext, DownloadService::class.java)
                androidx.core.content.ContextCompat.startForegroundService(
                    context.applicationContext,
                    intent,
                )
            } catch (e: Exception) {
                isServiceRunning = false
                e.printStackTrace()
            }
        }

        fun stopService() {
            if (!isServiceRunning) return
            try {
                isServiceRunning = false
                val intent = Intent(context.applicationContext, DownloadService::class.java)
                context.applicationContext.stopService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val privateDownloadDir: String
            get() =
                getExternalPrivateDownloadDirectory().run {
                    createEmptyFile(".nomedia")
                    absolutePath
                }

        fun updateDownloadDir(uri: Uri, directoryType: Directory) {
            when (directoryType) {
                Directory.AUDIO -> {
                    val path = FileUtil.getRealPath(uri)
                    audioDownloadDir = path
                    PreferenceUtil.encodeString(AUDIO_DIRECTORY, path)
                }

                Directory.VIDEO -> {
                    val path = FileUtil.getRealPath(uri)
                    videoDownloadDir = path
                    PreferenceUtil.encodeString(VIDEO_DIRECTORY, path)
                }

                Directory.CUSTOM_COMMAND -> {
                    val path = FileUtil.getRealPath(uri)
                }

                Directory.SDCARD -> {
                    context.contentResolver?.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                    PreferenceUtil.encodeString(SDCARD_URI, uri.toString())
                }
            }
        }

        fun getVersionReport(): String {
            val versionName = packageInfo.versionName
            val page = packageInfo
            val versionCode = androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(packageInfo)
            val release =
                if (Build.VERSION.SDK_INT >= 30) {
                    Build.VERSION.RELEASE_OR_CODENAME
                } else {
                    Build.VERSION.RELEASE
                }
            val ytdlpVersion = YT_DLP_VERSION.getString().ifEmpty {
                runCatching { YoutubeDL.getInstance().version(context) }.getOrNull().orEmpty()
            }
            return StringBuilder()
                .append("App version: $versionName ($versionCode)\n")
                .append("Device information: Android $release (API ${Build.VERSION.SDK_INT})\n")
                .append("Supported ABIs: ${Build.SUPPORTED_ABIS.contentToString()}\n")
                .append("Yt-dlp version: $ytdlpVersion\n")
                .toString()
        }

        fun isFDroidBuild(): Boolean = BuildConfig.FLAVOR == "fdroid"

        fun isDebugBuild(): Boolean = BuildConfig.DEBUG

        @SuppressLint("StaticFieldLeak") lateinit var context: Context
    }
}
