package hrshd1eux.flowgrab.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.annotation.CheckResult
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import hrshd1eux.flowgrab.App.Companion.context
import hrshd1eux.flowgrab.R
import java.io.File
import okhttp3.internal.closeQuietly

const val AUDIO_REGEX = "\\.(mp3|aac|opus|m4a|flac|wav|ogg)$"
const val VIDEO_REGEX = "\\.(mp4|mkv|webm|avi|mov|flv|3gp)$"
const val THUMBNAIL_REGEX = "\\.(jpg|png|webp)$"
const val SUBTITLE_REGEX = "\\.(lrc|vtt|srt|ass|json3|srv.|ttml)$"
private const val PRIVATE_DIRECTORY_SUFFIX = ".FlowGrab"

object FileUtil {
    fun openFileFromResult(downloadResult: Result<List<String>>) {
        val filePaths = downloadResult.getOrNull()
        if (filePaths.isNullOrEmpty()) return
        openFile(filePaths.first()) {
            ToastUtil.makeToastSuspend(context.getString(R.string.file_unavailable))
        }
    }

    inline fun openFile(path: String, onFailureCallback: (Throwable) -> Unit) =
        path
            .runCatching {
                createIntentForOpeningFile(this)?.run { context.startActivity(this) }
                    ?: throw Exception()
            }
            .onFailure { onFailureCallback(it) }

    private fun createIntentForFile(path: String?): Intent? {
        if (path == null) return null

        val uri =
            path
                .runCatching {
                    DocumentFile.fromSingleUri(context, Uri.parse(path)).run {
                        if (this?.exists() == true) {
                            this.uri
                        } else if (File(this@runCatching).exists()) {
                            FileProvider.getUriForFile(
                                context,
                                context.getFileProvider(),
                                File(this@runCatching),
                            )
                        } else null
                    }
                }
                .getOrNull() ?: return null

        return Intent().apply {
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            data = uri
        }
    }

    fun createIntentForOpeningFile(path: String?): Intent? =
        createIntentForFile(path)?.let {
            it.apply {
                action = (Intent.ACTION_VIEW)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

    fun createIntentForSharingFile(path: String?): Intent? =
        createIntentForFile(path)?.apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, data)
            val mimeType = data?.let { context.contentResolver.getType(it) } ?: "media/*"
            setDataAndType(this.data, mimeType)
            clipData = ClipData(null, arrayOf(mimeType), ClipData.Item(data))
        }

    fun Context.getFileProvider() = "$packageName.provider"

    fun String.getFileSize(): Long =
        this.run {
            val length = File(this).length()
            if (length == 0L) DocumentFile.fromSingleUri(context, Uri.parse(this))?.length() ?: 0L
            else length
        }

    fun String.getFileName(): String =
        this.run {
            File(this).nameWithoutExtension.ifEmpty {
                DocumentFile.fromSingleUri(context, Uri.parse(this))?.name ?: "video"
            }
        }

    fun deleteFile(path: String) =
        path.runCatching {
            if (!File(path).delete()) DocumentFile.fromSingleUri(context, Uri.parse(this))?.delete()
        }

    @CheckResult
    fun scanFileToMediaLibraryPostDownload(
        title: String,
        downloadDir: String,
        isAudioOnly: Boolean = false,
        videoId: String = "",
    ): List<String> {
        val dir = File(downloadDir)
        if (!dir.exists()) return emptyList()

        val id = videoId.ifEmpty {
            Regex("\\[([a-zA-Z0-9_-]{11})\\]").find(title)?.groupValues?.get(1).orEmpty()
        }
        val sanitizedTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim()
        val titleWords = sanitizedTitle.split(Regex("\\s+")).filter { it.length > 2 }

        fun matchesMedia(fileName: String): Boolean {
            if (id.isNotEmpty() && fileName.contains(id, ignoreCase = true)) return true
            if (title.isNotEmpty() && fileName.contains(title, ignoreCase = true)) return true
            if (sanitizedTitle.isNotEmpty() && fileName.contains(sanitizedTitle, ignoreCase = true)) return true
            if (titleWords.isNotEmpty() && titleWords.count { fileName.contains(it, ignoreCase = true) } >= titleWords.size.coerceAtMost(2)) return true
            return false
        }

        // Clean up any dangling intermediate video files produced during audio extraction
        if (isAudioOnly) {
            dir.listFiles()
                ?.filter {
                    it.isFile &&
                        matchesMedia(it.name) &&
                        it.name.contains(Regex(VIDEO_REGEX, RegexOption.IGNORE_CASE))
                }
                ?.forEach { videoFile ->
                    runCatching { videoFile.delete() }
                }
        }

        val matchingFiles =
            dir.walkTopDown()
                .filter { file ->
                    file.isFile &&
                        file.length() > 0L &&
                        matchesMedia(file.name) &&
                        !file.name.contains(Regex(THUMBNAIL_REGEX, RegexOption.IGNORE_CASE)) &&
                        !file.name.contains(Regex(SUBTITLE_REGEX, RegexOption.IGNORE_CASE)) &&
                        !file.name.endsWith(".part", ignoreCase = true) &&
                        !file.name.endsWith(".ytdl", ignoreCase = true) &&
                        !file.name.endsWith(".temp", ignoreCase = true) &&
                        !file.name.endsWith(".aria2", ignoreCase = true) &&
                        if (isAudioOnly) {
                            file.name.contains(Regex(AUDIO_REGEX, RegexOption.IGNORE_CASE))
                        } else {
                            file.name.contains(Regex(VIDEO_REGEX, RegexOption.IGNORE_CASE))
                        }
                }
                .sortedByDescending { it.lastModified() }
                .map { it.absolutePath }
                .toList()

        val finalFiles =
            if (matchingFiles.isNotEmpty()) {
                matchingFiles
            } else {
                dir.walkTopDown()
                    .filter { file ->
                        file.isFile &&
                            file.length() > 0L &&
                            matchesMedia(file.name) &&
                            !file.name.contains(Regex(THUMBNAIL_REGEX, RegexOption.IGNORE_CASE)) &&
                            !file.name.contains(Regex(SUBTITLE_REGEX, RegexOption.IGNORE_CASE)) &&
                            !file.name.endsWith(".part", ignoreCase = true) &&
                            !file.name.endsWith(".ytdl", ignoreCase = true) &&
                            !file.name.endsWith(".temp", ignoreCase = true) &&
                            !file.name.endsWith(".aria2", ignoreCase = true)
                    }
                    .sortedByDescending { it.lastModified() }
                    .map { it.absolutePath }
                    .toList()
            }

        MediaScannerConnection.scanFile(context, finalFiles.toTypedArray(), null, null)
        return finalFiles
    }

    fun scanDownloadDirectoryToMediaLibrary(downloadDir: String) =
        File(downloadDir)
            .walkTopDown()
            .filter { it.isFile }
            .map { it.absolutePath }
            .run {
                MediaScannerConnection.scanFile(context, this.toList().toTypedArray(), null, null)
            }

    @CheckResult
    fun moveFilesToSdcard(
        tempPath: File,
        sdcardUri: String,
        isAudioOnly: Boolean = false,
    ): Result<List<String>> {
        val uriList = mutableListOf<String>()
        val destDir =
            Uri.parse(sdcardUri).run {
                DocumentsContract.buildDocumentUriUsingTree(
                    this,
                    DocumentsContract.getTreeDocumentId(this),
                )
            }
        val res =
            tempPath.runCatching {
                walkTopDown().forEach {
                    if (it.isDirectory) return@forEach
                    if (isAudioOnly && it.name.contains(Regex(VIDEO_REGEX, RegexOption.IGNORE_CASE))) {
                        // Skip and discard intermediate video file
                        return@forEach
                    }
                    val mimeType =
                        MimeTypeMap.getSingleton().getMimeTypeFromExtension(it.extension) ?: "*/*"

                    val destUri =
                        DocumentsContract.createDocument(
                            context.contentResolver,
                            destDir,
                            mimeType,
                            it.name,
                        ) ?: return@forEach

                    val inputStream = it.inputStream()
                    val outputStream =
                        context.contentResolver.openOutputStream(destUri) ?: return@forEach
                    inputStream.copyTo(outputStream)
                    inputStream.closeQuietly()
                    outputStream.closeQuietly()
                    uriList.add(destUri.toString())
                }
                uriList
            }
        tempPath.deleteRecursively()
        return res
    }

    fun clearTempFiles(downloadDir: File): Int {
        var count = 0
        downloadDir.walkTopDown().forEach {
            if (it.isFile && !it.isHidden) {
                if (it.delete()) count++
            }
        }
        return count
    }

    fun Context.getConfigDirectory(): File = cacheDir

    fun Context.getConfigFile(suffix: String = "") = File(getConfigDirectory(), "config$suffix.txt")

    fun Context.getCookiesFile() = File(getConfigDirectory(), "cookies.txt")

    fun getExternalTempDir() =
        File(getExternalDownloadDirectory(), "tmp").apply {
            mkdirs()
            createEmptyFile(".nomedia")
        }

    fun Context.getSdcardTempDir(child: String?): File =
        getExternalTempDir().run { child?.let { resolve(it) } ?: this }

    fun Context.getArchiveFile(): File = filesDir.createEmptyFile("archive.txt").getOrThrow()

    fun Context.getInternalTempDir() = File(filesDir, "tmp")

    internal fun getExternalDownloadDirectory() =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "FlowGrab")
            .also { it.mkdir() }

    internal fun getExternalPrivateDownloadDirectory() =
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            PRIVATE_DIRECTORY_SUFFIX,
        )

    fun File.createEmptyFile(fileName: String): Result<File> =
        this.runCatching {
                mkdirs()
                resolve(fileName).apply { this@apply.createNewFile() }
            }
            .onFailure { it.printStackTrace() }

    fun writeContentToFile(content: String, file: File): File = file.apply { writeText(content) }

    fun getRealPath(treeUri: Uri): String {
        val path: String = treeUri.path.toString()
        Log.d(TAG, path)
        if (!path.contains("primary:")) {
            ToastUtil.makeToast("This directory is not supported")
            return getExternalDownloadDirectory().absolutePath
        }
        val last: String = path.split("primary:").last()
        return Environment.getExternalStorageDirectory().absolutePath + "/$last"
    }

    private const val TAG = "FileUtil"
}
