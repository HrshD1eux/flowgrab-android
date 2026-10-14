package hrshd1eux.flowgrab.util

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.OPEN_READONLY
import android.media.MediaCodecList
import android.os.Build
import android.util.Log
import android.webkit.CookieManager
import androidx.annotation.CheckResult
import hrshd1eux.flowgrab.App
import hrshd1eux.flowgrab.App.Companion.audioDownloadDir
import hrshd1eux.flowgrab.App.Companion.context
import hrshd1eux.flowgrab.App.Companion.videoDownloadDir
import hrshd1eux.flowgrab.R
import hrshd1eux.flowgrab.database.objects.CommandTemplate
import hrshd1eux.flowgrab.database.objects.DownloadedVideoInfo
import hrshd1eux.flowgrab.ui.page.settings.network.Cookie
import hrshd1eux.flowgrab.util.FileUtil.getArchiveFile
import hrshd1eux.flowgrab.util.FileUtil.getConfigFile
import hrshd1eux.flowgrab.util.FileUtil.getCookiesFile
import hrshd1eux.flowgrab.util.FileUtil.getExternalTempDir
import hrshd1eux.flowgrab.util.FileUtil.getFileName
import hrshd1eux.flowgrab.util.FileUtil.getSdcardTempDir
import hrshd1eux.flowgrab.util.FileUtil.moveFilesToSdcard
import hrshd1eux.flowgrab.util.PreferenceUtil.COOKIE_HEADER
import hrshd1eux.flowgrab.util.PreferenceUtil.getBoolean
import hrshd1eux.flowgrab.util.PreferenceUtil.getInt
import hrshd1eux.flowgrab.util.PreferenceUtil.getString
import hrshd1eux.flowgrab.util.PreferenceUtil.updateBoolean
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale

object DownloadUtil {

    object CookieScheme {
        const val NAME = "name"
        const val VALUE = "value"
        const val SECURE = "is_secure"
        const val EXPIRY = "expires_utc"
        const val HOST = "host_key"
        const val PATH = "path"
    }

    private val jsonFormat = Json { ignoreUnknownKeys = true }

    private const val TAG = "DownloadUtil"

    const val BASENAME = "%(title).200B"

    const val EXTENSION = ".%(ext)s"

    private const val ID = "[%(id)s]"

    private const val CLIP_TIMESTAMP = "%(section_start)d-%(section_end)d"

    const val OUTPUT_TEMPLATE_DEFAULT = BASENAME + EXTENSION

    const val OUTPUT_TEMPLATE_ID = "$BASENAME $ID$EXTENSION"

    private const val OUTPUT_TEMPLATE_CLIPS = "$BASENAME [$CLIP_TIMESTAMP]$EXTENSION"

    private const val OUTPUT_TEMPLATE_CHAPTERS =
        "chapter:$BASENAME/%(section_number)d - %(section_title).200B$EXTENSION"

    private const val OUTPUT_TEMPLATE_SPLIT = "$BASENAME/$OUTPUT_TEMPLATE_DEFAULT"

    private const val PLAYLIST_TITLE_SUBDIRECTORY_PREFIX = "%(playlist)s/"

    private const val CROP_ARTWORK_COMMAND =
        """--ppa "ffmpeg: -c:v mjpeg -vf crop=\"'if(gt(ih,iw),iw,ih)':'if(gt(iw,ih),ih,iw)'\"""""

    @CheckResult
    fun getPlaylistOrVideoInfo(
        playlistURL: String,
        downloadPreferences: DownloadPreferences = DownloadPreferences.createFromPreferences(),
    ): Result<YoutubeDLInfo> =
        YoutubeDL.runCatching {
            if (!App.isInitialized()) {
                kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                    App.awaitInitialization()
                }
            }
            ToastUtil.makeToastSuspend(context.getString(R.string.fetching_playlist_info))
            val request = YoutubeDLRequest(playlistURL)
            with(request) {
                //            addOption("--compat-options", "no-youtube-unavailable-videos")
                addOption("--flat-playlist")
                addOption("--dump-single-json")
                addOption("-o", BASENAME)
                addOption("-R", "3")
                addOption("--socket-timeout", "20")
                addOption("--no-check-certificates")
                addOption("--no-warnings")
                applyClientExtractorArgs()
                downloadPreferences.run {
                    if (proxy) {
                        enableProxy(proxyUrl)
                    }
                    if (forceIpv4) {
                        addOption("-4")
                    }
                    if (cookies) {
                        enableCookies(userAgentString)
                    }
                    if (restrictFilenames) {
                        addOption("--restrict-filenames")
                    }
                }
            }
            val responseOut = execute(request, playlistURL).out.trim()
            val jsonStartIndex = responseOut.indexOf('{')
            val jsonEndIndex = responseOut.lastIndexOf('}')
            val cleanJson =
                if (jsonStartIndex != -1 && jsonEndIndex != -1 && jsonEndIndex >= jsonStartIndex) {
                    responseOut.substring(jsonStartIndex, jsonEndIndex + 1)
                } else {
                    responseOut
                }
            val playlistInfo = jsonFormat.decodeFromString<PlaylistResult>(cleanJson)
            if (playlistInfo.type != "playlist") {
                jsonFormat.decodeFromString<VideoInfo>(cleanJson)
            } else playlistInfo
        }

    @CheckResult
    private fun getVideoInfo(
        request: YoutubeDLRequest,
        taskKey: String? = null,
    ): Result<VideoInfo> =
        request.runCatching {
            if (!App.isInitialized()) {
                kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                    App.awaitInitialization()
                }
            }
            val response: YoutubeDLResponse =
                YoutubeDL.getInstance().execute(request, taskKey, null)
            val responseOut = response.out.trim()
            val jsonStartIndex = responseOut.indexOf('{')
            val jsonEndIndex = responseOut.lastIndexOf('}')
            val cleanJson =
                if (jsonStartIndex != -1 && jsonEndIndex != -1 && jsonEndIndex >= jsonStartIndex) {
                    responseOut.substring(jsonStartIndex, jsonEndIndex + 1)
                } else {
                    responseOut
                }
            jsonFormat.decodeFromString<VideoInfo>(cleanJson)
        }

    val videoInfoCache = java.util.concurrent.ConcurrentHashMap<String, VideoInfo>()

    fun getCachedVideoInfo(url: String): VideoInfo? = videoInfoCache[url]

    @CheckResult
    fun fetchVideoInfoFromUrl(
        url: String,
        playlistIndex: Int? = null,
        taskKey: String? = null,
        preferences: DownloadPreferences = DownloadPreferences.createFromPreferences(),
    ): Result<VideoInfo> {
        if (playlistIndex == null) {
            videoInfoCache[url]?.let { return Result.success(it) }
        }
        with(preferences) {
            fun buildRequest(clientExtractorArgs: String? = null): YoutubeDLRequest =
                YoutubeDLRequest(url).apply {
                    addOption("-o", BASENAME)
                    if (restrictFilenames) {
                        addOption("--restrict-filenames")
                    }
                    if (cookies) {
                        enableCookies(userAgentString)
                    }
                    if (proxy) {
                        enableProxy(proxyUrl)
                    }
                    if (forceIpv4) {
                        addOption("-4")
                    }
                    if (autoSubtitle) {
                        addOption("--write-auto-subs")
                    }
                    if (clientExtractorArgs != null) {
                        addOption("--extractor-args", "youtube:$clientExtractorArgs")
                    } else {
                        applyClientExtractorArgs(autoSubtitle, autoTranslatedSubtitles)
                    }
                    if (playlistIndex != null) {
                        addOption("--playlist-items", playlistIndex)
                        addOption("--dump-json")
                    } else {
                        addOption("--dump-single-json")
                    }
                    addOption("-R", "2")
                    addOption("--no-playlist")
                    addOption("--socket-timeout", "20")
                    addOption("--no-check-certificates")
                    addOption("--no-warnings")
                }

            val request = buildRequest()
            var res = getVideoInfo(request, taskKey)

            // If primary YouTube extraction fails (e.g., player client blocked), attempt fallback client
            if (res.isFailure && url.contains("youtu", ignoreCase = true)) {
                Log.w(TAG, "Primary extractor attempt failed for $url, retrying with visionos/mweb client fallback...")
                val fallbackArgs =
                    if (autoSubtitle && !autoTranslatedSubtitles) {
                        "player_client=visionos,mweb;skip=translated_subs"
                    } else {
                        "player_client=visionos,mweb"
                    }
                val fallbackRequest = buildRequest(fallbackArgs)
                val fallbackRes = getVideoInfo(fallbackRequest, taskKey)
                if (fallbackRes.isSuccess) {
                    res = fallbackRes
                }
            }

            return res.onSuccess { info ->
                if (playlistIndex == null) {
                    videoInfoCache[url] = info
                }
            }
        }
    }

    @Serializable
    data class DownloadPreferences(
        val extractAudio: Boolean,
        val createThumbnail: Boolean,
        val downloadPlaylist: Boolean,
        val subdirectoryExtractor: Boolean,
        val subdirectoryPlaylistTitle: Boolean,
        val commandDirectory: String,
        val downloadSubtitle: Boolean,
        val embedSubtitle: Boolean,
        val keepSubtitle: Boolean,
        val subtitleLanguage: String,
        val autoSubtitle: Boolean,
        val autoTranslatedSubtitles: Boolean,
        val convertSubtitle: Int,
        val concurrentFragments: Int,
        val sponsorBlock: Boolean,
        val sponsorBlockCategory: String,
        val cookies: Boolean,
        val aria2c: Boolean,
        val useCustomAudioPreset: Boolean,
        val audioFormat: Int,
        val audioQuality: Int,
        val convertAudio: Boolean,
        val formatSorting: Boolean,
        val sortingFields: String,
        val audioConvertFormat: Int,
        val videoFormat: Int,
        val formatIdString: String,
        val videoResolution: Int,
        val privateMode: Boolean,
        val rateLimit: Boolean,
        val maxDownloadRate: String,
        val privateDirectory: Boolean,
        val cropArtwork: Boolean,
        val sdcard: Boolean,
        val sdcardUri: String,
        val embedThumbnail: Boolean,
        val videoClips: List<VideoClip>,
        val splitByChapter: Boolean,
        val debug: Boolean,
        val proxy: Boolean,
        val proxyUrl: String,
        val newTitle: String,
        val userAgentString: String,
        val outputTemplate: String,
        val useDownloadArchive: Boolean,
        val embedMetadata: Boolean,
        val restrictFilenames: Boolean,
        val supportAv1HardwareDecoding: Boolean,
        val forceIpv4: Boolean,
        val mergeAudioStream: Boolean,
        val mergeToMkv: Boolean,
        val videoContainerFormat: String = "",
    ) {
        companion object {
            val EMPTY =
                DownloadPreferences(
                    videoContainerFormat = "",
                    extractAudio = false,
                    createThumbnail = false,
                    downloadPlaylist = false,
                    subdirectoryExtractor = false,
                    subdirectoryPlaylistTitle = false,
                    commandDirectory = "",
                    downloadSubtitle = false,
                    embedSubtitle = false,
                    keepSubtitle = false,
                    subtitleLanguage = "",
                    autoSubtitle = false,
                    autoTranslatedSubtitles = false,
                    convertSubtitle = 0,
                    concurrentFragments = 0,
                    sponsorBlock = false,
                    sponsorBlockCategory = "",
                    cookies = false,
                    aria2c = false,
                    audioFormat = 0,
                    audioQuality = 0,
                    convertAudio = false,
                    formatSorting = false,
                    sortingFields = "",
                    audioConvertFormat = 0,
                    videoFormat = 0,
                    formatIdString = "",
                    videoResolution = 0,
                    privateMode = false,
                    rateLimit = false,
                    maxDownloadRate = "",
                    privateDirectory = false,
                    cropArtwork = false,
                    sdcard = false,
                    sdcardUri = "",
                    embedThumbnail = false,
                    videoClips = emptyList(),
                    splitByChapter = false,
                    debug = false,
                    proxy = false,
                    proxyUrl = "",
                    newTitle = "",
                    userAgentString = "",
                    outputTemplate = "",
                    useDownloadArchive = false,
                    embedMetadata = false,
                    restrictFilenames = false,
                    supportAv1HardwareDecoding = false,
                    forceIpv4 = false,
                    mergeAudioStream = false,
                    mergeToMkv = false,
                    useCustomAudioPreset = false,
                )

            fun createFromPreferences(): DownloadPreferences {
                val downloadSubtitle = SUBTITLE.getBoolean()
                val embedSubtitle = EMBED_SUBTITLE.getBoolean()
                return DownloadPreferences(
                    extractAudio = EXTRACT_AUDIO.getBoolean(),
                    createThumbnail = THUMBNAIL.getBoolean(),
                    downloadPlaylist = PLAYLIST.getBoolean(),
                    subdirectoryExtractor = SUBDIRECTORY_EXTRACTOR.getBoolean(),
                    subdirectoryPlaylistTitle = SUBDIRECTORY_PLAYLIST_TITLE.getBoolean(),
                    commandDirectory = COMMAND_DIRECTORY.getString(),
                    downloadSubtitle = downloadSubtitle,
                    embedSubtitle = embedSubtitle,
                    keepSubtitle = KEEP_SUBTITLE_FILES.getBoolean(),
                    subtitleLanguage = SUBTITLE_LANGUAGE.getString(),
                    autoSubtitle = AUTO_SUBTITLE.getBoolean(),
                    autoTranslatedSubtitles = AUTO_TRANSLATED_SUBTITLES.getBoolean(),
                    convertSubtitle = CONVERT_SUBTITLE.getInt(),
                    concurrentFragments = CONCURRENT.getInt(),
                    sponsorBlock = SPONSORBLOCK.getBoolean(),
                    sponsorBlockCategory = PreferenceUtil.getSponsorBlockCategories(),
                    cookies = COOKIES.getBoolean(),
                    aria2c = ARIA2C.getBoolean(),
                    useCustomAudioPreset = USE_CUSTOM_AUDIO_PRESET.getBoolean(),
                    audioFormat = AUDIO_FORMAT.getInt(),
                    audioQuality = AUDIO_QUALITY.getInt(),
                    convertAudio = AUDIO_CONVERT.getBoolean(),
                    formatSorting = FORMAT_SORTING.getBoolean(),
                    sortingFields = SORTING_FIELDS.getString(),
                    audioConvertFormat = PreferenceUtil.getAudioConvertFormat(),
                    videoFormat = PreferenceUtil.getVideoFormat(),
                    formatIdString = "",
                    videoResolution = PreferenceUtil.getVideoResolution(),
                    privateMode = PRIVATE_MODE.getBoolean(),
                    rateLimit = RATE_LIMIT.getBoolean(),
                    maxDownloadRate = PreferenceUtil.getMaxDownloadRate(),
                    privateDirectory = PRIVATE_DIRECTORY.getBoolean(),
                    cropArtwork = CROP_ARTWORK.getBoolean(),
                    sdcard = SDCARD_DOWNLOAD.getBoolean(),
                    sdcardUri = SDCARD_URI.getString(),
                    embedThumbnail = EMBED_THUMBNAIL.getBoolean(),
                    videoClips = emptyList(),
                    splitByChapter = false,
                    debug = DEBUG.getBoolean(),
                    proxy = PROXY.getBoolean(),
                    proxyUrl = PROXY_URL.getString(),
                    newTitle = "",
                    userAgentString =
                        USER_AGENT_STRING.run { if (USER_AGENT.getBoolean()) getString() else "" },
                    outputTemplate = OUTPUT_TEMPLATE.getString(),
                    useDownloadArchive = DOWNLOAD_ARCHIVE.getBoolean(),
                    embedMetadata = EMBED_METADATA.getBoolean(),
                    restrictFilenames = RESTRICT_FILENAMES.getBoolean(),
                    supportAv1HardwareDecoding = checkIfAv1HardwareAccelerated(),
                    forceIpv4 = FORCE_IPV4.getBoolean(),
                    mergeAudioStream = false,
                    mergeToMkv =
                        (downloadSubtitle && embedSubtitle) || MERGE_OUTPUT_MKV.getBoolean(),
                    videoContainerFormat = "",
                )
            }
        }
    }

    private fun YoutubeDLRequest.enableCookies(userAgentString: String): YoutubeDLRequest =
        this.addOption("--cookies", context.getCookiesFile().absolutePath).apply {
            if (userAgentString.isNotEmpty()) {
                addOption("--add-header", "User-Agent:$userAgentString")
            }
        }

    private fun YoutubeDLRequest.enableProxy(proxyUrl: String): YoutubeDLRequest =
        this.addOption("--proxy", proxyUrl)

    private fun YoutubeDLRequest.useDownloadArchive(): YoutubeDLRequest =
        this.addOption("--download-archive", context.getArchiveFile().absolutePath)

    @CheckResult
    fun getCookieListFromDatabase(): Result<List<Cookie>> = runCatching {
        CookieManager.getInstance().run {
            if (!hasCookies()) throw Exception("There is no cookies in the database!")
            flush()
        }
        SQLiteDatabase.openDatabase(
                context.dataDir.resolve("app_webview/Default/Cookies").absolutePath,
                null,
                OPEN_READONLY,
            )
            .run {
                val projection =
                    arrayOf(
                        CookieScheme.HOST,
                        CookieScheme.EXPIRY,
                        CookieScheme.PATH,
                        CookieScheme.NAME,
                        CookieScheme.VALUE,
                        CookieScheme.SECURE,
                    )
                val cookieList = mutableListOf<Cookie>()
                query("cookies", projection, null, null, null, null, null).run {
                    while (moveToNext()) {
                        val expiry = getLong(getColumnIndexOrThrow(CookieScheme.EXPIRY))
                        val name = getString(getColumnIndexOrThrow(CookieScheme.NAME))
                        val value = getString(getColumnIndexOrThrow(CookieScheme.VALUE))
                        val path = getString(getColumnIndexOrThrow(CookieScheme.PATH))
                        val secure = getLong(getColumnIndexOrThrow(CookieScheme.SECURE)) == 1L
                        val hostKey = getString(getColumnIndexOrThrow(CookieScheme.HOST))

                        val host = if (hostKey[0] != '.') ".$hostKey" else hostKey
                        cookieList.add(
                            Cookie(
                                domain = host,
                                name = name,
                                value = value,
                                path = path,
                                secure = secure,
                                expiry = expiry,
                            )
                        )
                    }
                    close()
                }
                close()
                cookieList
            }
    }

    fun List<Cookie>.toCookiesFileContent(): String =
        this.fold(StringBuilder(COOKIE_HEADER)) { acc, cookie ->
                acc.append(cookie.toNetscapeCookieString()).append("\n")
            }
            .toString()

    fun getCookiesContentFromDatabase(): Result<String> =
        getCookieListFromDatabase().mapCatching { it.toCookiesFileContent() }

    private fun YoutubeDLRequest.applyClientExtractorArgs(
        autoSubtitle: Boolean = false,
        autoTranslatedSubtitles: Boolean = false,
    ): YoutubeDLRequest = apply {
        val args = mutableListOf<String>()
        if (autoSubtitle && !autoTranslatedSubtitles) {
            args.add("skip=translated_subs")
        }
        if (args.isNotEmpty()) {
            addOption("--extractor-args", "youtube:${args.joinToString(";")}")
        }
    }

    private fun YoutubeDLRequest.enableAria2c(): YoutubeDLRequest =
        this.addOption("--downloader", "libaria2c.so")
            .addOption("--downloader-args", "aria2c:-j8 -x8 -s8 -k1M --file-allocation=none")

    private fun YoutubeDLRequest.addOptionsForVideoDownloads(
        downloadPreferences: DownloadPreferences
    ): YoutubeDLRequest =
        this.apply {
            downloadPreferences.run {
                addOption("--add-metadata")
                addOption("--no-embed-info-json")
                addOption("--postprocessor-args", "Merger:-c:v copy -c:a copy")
                if (formatIdString.isNotEmpty()) {
                    addOption("-f", "$formatIdString/bv*+ba/b/best")
                    if (mergeAudioStream) {
                        addOption("--audio-multistreams")
                    }
                } else {
                    addOption("-f", "bv*+ba/b/best")
                    applyFormatSorter(this, toFormatSorter())
                }
                if (downloadSubtitle) {
                    if (autoSubtitle) {
                        addOption("--write-auto-subs")
                    }
                    subtitleLanguage
                        .takeIf { it.isNotEmpty() }
                        ?.let { addOption("--sub-langs", it) }
                    if (embedSubtitle) {
                        addOption("--embed-subs")
                        if (keepSubtitle) {
                            addOption("--write-subs")
                        }
                    } else {
                        addOption("--write-subs")
                    }
                    when (convertSubtitle) {
                        CONVERT_ASS -> addOption("--convert-subs", "ass")
                        CONVERT_SRT -> addOption("--convert-subs", "srt")
                        CONVERT_VTT -> addOption("--convert-subs", "vtt")
                        CONVERT_LRC -> addOption("--convert-subs", "lrc")
                        else -> {}
                    }
                }
                applyClientExtractorArgs(autoSubtitle, autoTranslatedSubtitles)
                when (videoContainerFormat.lowercase()) {
                    "mp4" -> {
                        addOption("--remux-video", "mp4")
                        addOption("--merge-output-format", "mp4")
                    }
                    "mkv" -> {
                        addOption("--remux-video", "mkv")
                        addOption("--merge-output-format", "mkv")
                    }
                    "webm" -> {
                        addOption("--remux-video", "webm")
                        addOption("--merge-output-format", "webm")
                    }
                    else -> {
                        if (mergeToMkv) {
                            addOption("--remux-video", "mkv")
                            addOption("--merge-output-format", "mkv")
                        }
                    }
                }
                if (embedThumbnail) {
                    addOption("--embed-thumbnail")
                }
                if (videoClips.isEmpty()) addOption("--embed-chapters")
            }
        }

    @CheckResult
    internal fun DownloadPreferences.toAudioFormatSorter(): String =
        this.run {
            if (!useCustomAudioPreset) return@run ""
            val format =
                when (audioFormat) {
                    M4A -> "acodec:aac"
                    OPUS -> "acodec:opus"
                    else -> ""
                }
            val quality =
                when (audioQuality) {
                    HIGH -> "abr~192"
                    MEDIUM -> "abr~128"
                    LOW -> "abr~64"
                    else -> ""
                }
            return@run connectWithDelimiter(format, quality, delimiter = ",")
        }

    @CheckResult
    internal fun DownloadPreferences.toVideoFormatSorter(): String =
        this.run {
            val format =
                when (videoFormat) {
                    FORMAT_COMPATIBILITY -> "proto,vcodec:h264,ext"
                    FORMAT_QUALITY ->
                        if (supportAv1HardwareDecoding) {
                            "vcodec:av01"
                        } else {
                            "vcodec:vp9.2"
                        }

                    else -> ""
                }
            val res =
                when (videoResolution) {
                    1 -> "res:2160"
                    2 -> "res:1440"
                    3 -> "res:1080"
                    4 -> "res:720"
                    5 -> "res:480"
                    6 -> "res:360"
                    7 -> "+res"
                    else -> ""
                }
            val sorter = if (videoFormat == FORMAT_COMPATIBILITY) {
                connectWithDelimiter(format, res, delimiter = ",")
            } else {
                connectWithDelimiter(res, format, delimiter = ",")
            }
            return@run sorter
        }

    private fun YoutubeDLRequest.applyFormatSorter(
        preferences: DownloadPreferences,
        sorter: String,
    ) =
        preferences.run {
            if (formatSorting && sortingFields.isNotEmpty()) addOption("-S", sortingFields)
            else if (sorter.isNotEmpty()) addOption("-S", sorter) else {}
        }

    @CheckResult
    fun DownloadPreferences.toFormatSorter(): String =
        connectWithDelimiter(
            this.toVideoFormatSorter(),
            this.toAudioFormatSorter(),
            delimiter = ",",
        )

    private fun YoutubeDLRequest.addOptionsForAudioDownloads(
        id: String,
        preferences: DownloadPreferences,
        playlistUrl: String,
    ): YoutubeDLRequest =
        this.apply {
            with(preferences) {
                addOption("-x")
                addOption("--no-keep-video")
                if (downloadSubtitle) {
                    addOption("--write-subs")

                    if (autoSubtitle) {
                        addOption("--write-auto-subs")
                    }
                    subtitleLanguage
                        .takeIf { it.isNotEmpty() }
                        ?.let { addOption("--sub-langs", it) }
                    when (convertSubtitle) {
                        CONVERT_ASS -> addOption("--convert-subs", "ass")
                        CONVERT_SRT -> addOption("--convert-subs", "srt")
                        CONVERT_VTT -> addOption("--convert-subs", "vtt")
                        CONVERT_LRC -> addOption("--convert-subs", "lrc")
                        else -> {}
                    }
                }
                applyClientExtractorArgs(autoSubtitle, autoTranslatedSubtitles)
                if (formatIdString.isNotEmpty()) {
                    addOption("-f", "$formatIdString/ba/ba*")
                    if (mergeAudioStream) {
                        addOption("--audio-multistreams")
                    }
                } else if (convertAudio) {
                    addOption("-f", "ba/ba*")
                    when (audioConvertFormat) {
                        CONVERT_MP3 -> {
                            addOption("--audio-format", "mp3")
                            addOption("--audio-quality", "0")
                        }

                        CONVERT_M4A -> {
                            addOption("--audio-format", "m4a")
                        }

                        CONVERT_OPUS -> {
                            addOption("--audio-format", "opus")
                        }

                        CONVERT_FLAC -> {
                            addOption("--audio-format", "flac")
                        }

                        CONVERT_WAV -> {
                            addOption("--audio-format", "wav")
                        }

                        else -> {
                            addOption("--audio-format", "mp3")
                        }
                    }
                } else {
                    addOption("-f", "ba/ba*")
                    applyFormatSorter(preferences, toAudioFormatSorter())
                }

                if (embedMetadata) {
                    addOption("--embed-metadata")
                    addOption("--embed-thumbnail")
                    addOption("--convert-thumbnails", "jpg")

                    if (cropArtwork) {
                        val configFile = context.getConfigFile(id)
                        FileUtil.writeContentToFile(CROP_ARTWORK_COMMAND, configFile)
                        addOption("--config", configFile.absolutePath)
                    }
                }
                addOption("--parse-metadata", "%(release_year,upload_date)s:%(meta_date)s")

                if (playlistUrl.isNotEmpty()) {
                    addOption("--parse-metadata", "%(album,playlist,title)s:%(meta_album)s")
                    addOption("--parse-metadata", "%(track_number,playlist_index)d:%(meta_track)s")
                } else {
                    addOption("--parse-metadata", "%(album,title)s:%(meta_album)s")
                }
            }
        }

    private fun insertInfoIntoDownloadHistory(
        videoInfo: VideoInfo,
        filePaths: List<String>,
    ): List<String> =
        filePaths.onEach {
            DatabaseUtil.insertInfo(videoInfo.toDownloadedVideoInfo(videoPath = it))
        }

    private fun VideoInfo.toDownloadedVideoInfo(
        id: Int = 0,
        videoPath: String,
    ): DownloadedVideoInfo =
        this.run {
            DownloadedVideoInfo(
                id = id,
                videoTitle = title,
                videoAuthor = uploader ?: channel ?: uploaderId.toString(),
                videoUrl = webpageUrl ?: originalUrl.toString(),
                thumbnailUrl = thumbnail.toHttpsUrl(),
                videoPath = videoPath,
                extractor = extractorKey,
            )
        }

    private fun insertSplitChapterIntoHistory(videoInfo: VideoInfo, filePaths: List<String>) =
        filePaths.onEach {
            DatabaseUtil.insertInfo(
                videoInfo.toDownloadedVideoInfo(videoPath = it).copy(videoTitle = it.getFileName())
            )
        }

    @CheckResult
    fun downloadVideo(
        videoInfo: VideoInfo? = null,
        playlistUrl: String = "",
        playlistItem: Int = 0,
        taskId: String,
        downloadPreferences: DownloadPreferences,
        progressCallback: ((Float, Long, String) -> Unit)?,
    ): Result<List<String>> {
        if (videoInfo == null)
            return Result.failure(Throwable(context.getString(R.string.fetch_info_error_msg)))

        with(downloadPreferences) {
            val url =
                playlistUrl.ifEmpty {
                    videoInfo.originalUrl
                        ?: videoInfo.webpageUrl
                        ?: return Result.failure(
                            Throwable(context.getString(R.string.fetch_info_error_msg))
                        )
                }
            val request = YoutubeDLRequest(url)
            val pathBuilder = StringBuilder()
            val outputBuilder = StringBuilder()

            request
                .apply {
                    addOption("--no-mtime")
                    //                addOption("-v")
                    if (cookies) {
                        enableCookies(userAgentString)
                    }
                    if (restrictFilenames) {
                        addOption("--restrict-filenames")
                    }
                    if (proxy) {
                        enableProxy(proxyUrl)
                    }
                    if (forceIpv4) {
                        addOption("-4")
                    }
                    if (debug) {
                        addOption("-v")
                    }
                    if (useDownloadArchive) {
                        val archiveFile = context.getArchiveFile()
                        val archiveFileContent = archiveFile.readText()
                        if (archiveFileContent.contains("${videoInfo.extractor} ${videoInfo.id}")) {
                            return Result.failure(
                                YoutubeDLException(
                                    context.getString(R.string.download_archive_error)
                                )
                            )
                        } else {
                            useDownloadArchive()
                        }
                    }

                    if (rateLimit && maxDownloadRate.isNumberInRange(1, 1000000)) {
                        addOption("-r", "${maxDownloadRate}K")
                    }

                    if (playlistItem != 0 && downloadPlaylist) {
                        addOption("--playlist-items", playlistItem)
                        if (subdirectoryPlaylistTitle && !videoInfo.playlist.isNullOrEmpty()) {
                            outputBuilder.append(PLAYLIST_TITLE_SUBDIRECTORY_PREFIX)
                        }
                        //                    addOption("--compat-options",
                        // "no-youtube-unavailable-videos")
                    } else {
                        addOption("--no-playlist")
                    }

                    if (aria2c) {
                        enableAria2c()
                    } else {
                        val fragments = maxOf(concurrentFragments, 5)
                        addOption("--concurrent-fragments", fragments)
                    }
                    addOption("--buffer-size", "64K")
                    addOption("--http-chunk-size", "10M")
                    addOption("--retries", "3")
                    addOption("--fragment-retries", "5")
                    addOption("--socket-timeout", "30")
                    addOption("--no-check-certificates")

                    if (extractAudio || (videoInfo.vcodec == "none")) {
                        if (privateDirectory) pathBuilder.append(App.privateDownloadDir)
                        else pathBuilder.append(audioDownloadDir)
                        addOptionsForAudioDownloads(
                            id = videoInfo.id,
                            preferences = downloadPreferences,
                            playlistUrl = playlistUrl,
                        )
                    } else {
                        if (privateDirectory) pathBuilder.append(App.privateDownloadDir)
                        else pathBuilder.append(videoDownloadDir)
                        addOptionsForVideoDownloads(downloadPreferences)
                    }
                    if (sponsorBlock) {
                        addOption("--sponsorblock-remove", sponsorBlockCategory)
                    }

                    if (createThumbnail) {
                        addOption("--write-thumbnail")
                        addOption("--convert-thumbnails", "png")
                    }
                    if (subdirectoryExtractor) {
                        pathBuilder.append("/${videoInfo.extractorKey}")
                    }

                    if (sdcard) {
                        addOption("-P", context.getSdcardTempDir(videoInfo.id).absolutePath)
                    } else {
                        addOption("-P", pathBuilder.toString())
                    }

                    videoClips.forEach {
                        addOption(
                            "--download-sections",
                            "*%d-%d".format(locale = Locale.US, it.start, it.end),
                        )
                    }
                    if (newTitle.isNotEmpty()) {
                        addCommands(listOf("--replace-in-metadata", "title", ".+", newTitle))
                    }
                    if (!sdcard) {
                        addOption("-P", "temp:" + getExternalTempDir().absolutePath)
                    }

                    if (splitByChapter) {
                        addOption("-o", OUTPUT_TEMPLATE_CHAPTERS)
                        addOption("--split-chapters")
                    }

                    val output =
                        if (splitByChapter) {
                            OUTPUT_TEMPLATE_SPLIT
                        } else if (videoClips.isEmpty()) {
                            outputTemplate
                        } else {
                            OUTPUT_TEMPLATE_CLIPS
                        }

                    addOption("-o", outputBuilder.append(output).toString())

                    for (s in request.buildCommand()) Log.d(TAG, s)
                }
                .runCatching {
                    if (!App.isInitialized()) {
                        kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                            App.awaitInitialization()
                        }
                    }
                    YoutubeDL.getInstance()
                        .execute(request = this, processId = taskId, callback = progressCallback)
                }
                .onFailure { th ->
                    return if (
                        sponsorBlock &&
                            th.message?.contains("Unable to communicate with SponsorBlock API") ==
                                true
                    ) {
                        th.printStackTrace()
                        onFinishDownloading(
                            preferences = this,
                            videoInfo = videoInfo,
                            downloadPath = pathBuilder.toString(),
                            sdcardUri = sdcardUri,
                        )
                    } else Result.failure(th)
                }
            return onFinishDownloading(
                preferences = this,
                videoInfo = videoInfo,
                downloadPath = pathBuilder.toString(),
                sdcardUri = sdcardUri,
            )
        }
    }

    private fun onFinishDownloading(
        preferences: DownloadPreferences,
        videoInfo: VideoInfo,
        downloadPath: String,
        sdcardUri: String,
    ): Result<List<String>> =
        preferences.run {
            val fileName =
                preferences.newTitle.ifEmpty {
                    videoInfo.filename
                        ?: videoInfo.requestedDownloads?.firstOrNull()?.filename
                        ?: videoInfo.title
                }

            Log.d(TAG, "onFinishDownloading: $fileName")
            val isAudio = extractAudio || (videoInfo.vcodec == "none")
            if (sdcard) {
                moveFilesToSdcard(
                        sdcardUri = sdcardUri,
                        tempPath = context.getSdcardTempDir(videoInfo.id),
                        isAudioOnly = isAudio,
                    )
                    .onSuccess {
                        if (privateMode) {
                            return Result.success(emptyList())
                        } else if (splitByChapter) {
                            insertSplitChapterIntoHistory(videoInfo, it)
                        } else {
                            insertInfoIntoDownloadHistory(videoInfo, it)
                        }
                    }
            } else {
                FileUtil.scanFileToMediaLibraryPostDownload(
                        title = fileName,
                        downloadDir = downloadPath,
                        isAudioOnly = isAudio,
                    )
                    .run {
                        if (privateMode) Result.success(emptyList())
                        else
                            Result.success(
                                if (splitByChapter) {
                                    insertSplitChapterIntoHistory(videoInfo, this)
                                } else {
                                    insertInfoIntoDownloadHistory(videoInfo, this)
                                }
                            )
                    }
            }
        }

    @CheckResult
    fun executeCustomCommandTask(
        urlString: String,
        taskId: String,
        template: CommandTemplate,
        preferences: DownloadPreferences,
        progressCallback: ((Float, Long, String) -> Unit),
    ): Result<YoutubeDLResponse> {
        val urlList = urlString.split(Regex("[\n ]")).filter { it.isNotBlank() }

        val request =
            with(preferences) {
                YoutubeDLRequest(urlList).apply {
                    applyClientExtractorArgs()
                    commandDirectory.takeIf { it.isNotEmpty() }?.let { addOption("-P", it) }
                    addOption("--newline")
                    if (aria2c) {
                        enableAria2c()
                    }
                    if (useDownloadArchive) {
                        useDownloadArchive()
                    }
                    if (restrictFilenames) {
                        addOption("--restrict-filenames")
                    }
                    addOption(
                        "--config-locations",
                        FileUtil.writeContentToFile(template.template, context.getConfigFile())
                            .absolutePath,
                    )
                    if (cookies) {
                        enableCookies(userAgentString)
                    }
                }
            }

        return runCatching {
            if (!App.isInitialized()) {
                kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                    App.awaitInitialization()
                }
            }
            YoutubeDL.getInstance()
                .execute(request = request, processId = taskId, callback = progressCallback)
        }
    }

    private fun checkIfAv1HardwareAccelerated(): Boolean {
        if (PreferenceUtil.containsKey(AV1_HARDWARE_ACCELERATED)) {
            return AV1_HARDWARE_ACCELERATED.getBoolean()
        } else {
            val res =
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    false
                } else {
                    MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
                        info.supportedTypes.any { it.equals("video/av01", ignoreCase = true) } &&
                            info.isHardwareAccelerated
                    }
                }
            AV1_HARDWARE_ACCELERATED.updateBoolean(res)
            return res
        }
    }
}
