package hrshd1eux.flowgrab.download

import hrshd1eux.flowgrab.database.objects.CommandTemplate
import hrshd1eux.flowgrab.download.Task.TypeInfo
import hrshd1eux.flowgrab.download.Task.ViewState
import hrshd1eux.flowgrab.util.DownloadUtil
import hrshd1eux.flowgrab.util.Format
import hrshd1eux.flowgrab.util.VideoInfo
import hrshd1eux.flowgrab.util.extractErrorMessage
import hrshd1eux.flowgrab.util.toHttpsUrl
import kotlinx.coroutines.Job
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.math.roundToInt

private val TypeInfo.id: String
    get() =
        when (this) {
            is TypeInfo.CustomCommand -> "${template.id}_${template.name}"
            is TypeInfo.Playlist -> "$index"
            TypeInfo.URL -> ""
        }

private fun makeId(url: String, type: TypeInfo, preferences: DownloadUtil.DownloadPreferences): String =
    "${url}_${type.id}_${preferences.hashCode()}"

@Serializable
data class Task(
    val url: String,
    val type: TypeInfo = TypeInfo.URL,
    val preferences: DownloadUtil.DownloadPreferences,
    val id: String = makeId(url, type, preferences),
) : Comparable<Task> {

    val timeCreated: Long = System.currentTimeMillis()

    override fun compareTo(other: Task): Int {
        return timeCreated.compareTo(other.timeCreated)
    }

    @Serializable
    sealed interface TypeInfo {

        @Serializable data class Playlist(val index: Int = 0) : TypeInfo

        @Serializable data class CustomCommand(val template: CommandTemplate) : TypeInfo

        @Serializable data object URL : TypeInfo
    }

    @Serializable
    data class State(
        val downloadState: DownloadState,
        val videoInfo: VideoInfo?,
        val viewState: ViewState,
    )

    @Serializable
    sealed interface DownloadState : Comparable<DownloadState> {

        interface Cancelable {
            val job: Job
            val taskId: String
            val action: RestartableAction
        }

        interface Restartable {
            val action: RestartableAction
        }

        @Serializable data object Idle : DownloadState

        @Serializable
        data class FetchingInfo(
            @Transient override val job: Job = Job(),
            override val taskId: String,
        ) : DownloadState, Cancelable {
            override val action: RestartableAction = RestartableAction.FetchInfo
        }

        @Serializable data object ReadyWithInfo : DownloadState

        @Serializable
        data class Running(
            @Transient override val job: Job = Job(),
            override val taskId: String,
            val progress: Float = PROGRESS_INDETERMINATE,
            val progressText: String = "",
        ) : DownloadState, Cancelable {
            override val action: RestartableAction = RestartableAction.Download
        }

        @Serializable
        data class Canceled(override val action: RestartableAction, val progress: Float? = null) :
            DownloadState, Restartable

        @Serializable
        data class Error(
            val errorMessage: String = "",
            @Transient private val _throwable: Throwable? = null,
            override val action: RestartableAction,
        ) : DownloadState, Restartable {
            val throwable: Throwable
                get() = _throwable ?: Throwable(errorMessage.ifBlank { "Download failed" })

            constructor(throwable: Throwable, action: RestartableAction) : this(
                errorMessage = throwable.extractErrorMessage(),
                _throwable = throwable,
                action = action,
            )
        }

        @Serializable data class Completed(val filePath: String?) : DownloadState

        override fun compareTo(other: DownloadState): Int {
            return ordinal - other.ordinal
        }

        private val ordinal: Int
            get() =
                when (this) {
                    is Canceled -> 4
                    is Error -> 5
                    is Completed -> 6
                    Idle -> 3
                    is FetchingInfo -> 2
                    ReadyWithInfo -> 1
                    is Running -> 0
                }
    }

    @Serializable
    sealed interface RestartableAction {
        @Serializable data object FetchInfo : RestartableAction

        @Serializable data object Download : RestartableAction
    }

    @Serializable
    data class ViewState(
        val url: String = "https://www.example.com",
        val title: String = "",
        val uploader: String = "",
        val extractorKey: String = "",
        val duration: Int = 0,
        val fileSizeApprox: Double = .0,
        val thumbnailUrl: String? = null,
        val videoFormats: List<Format>? = null,
        val audioOnlyFormats: List<Format>? = null,
    ) {
        companion object {
            fun fromVideoInfo(
                info: VideoInfo,
                preferences: DownloadUtil.DownloadPreferences? = null,
            ): ViewState {
                val formats =
                    info.requestedFormats
                        ?: info.requestedDownloads?.map { it.toFormat() }
                        ?: info.formats
                        ?: emptyList()

                val videoFormats = formats.filter { it.containsVideo() }
                val audioOnlyFormats = formats.filter { it.isAudioOnly() }

                val isAudioOnly = preferences?.extractAudio == true ||
                    (audioOnlyFormats.isNotEmpty() && videoFormats.isEmpty())

                val estimatedSize: Double = if (isAudioOnly) {
                    val allAudio = (info.formats ?: emptyList()).filter { it.isAudioOnly() }
                    val targetFormat = preferences?.formatIdString?.let { fid ->
                        allAudio.find { it.formatId == fid }
                    } ?: (info.requestedFormats ?: emptyList()).find { it.isAudioOnly() }
                      ?: audioOnlyFormats.firstOrNull()
                      ?: allAudio.maxByOrNull { it.abr ?: it.tbr ?: 0.0 }

                    val duration = info.duration ?: 0.0
                    val calcSize = targetFormat?.fileSize
                        ?: targetFormat?.fileSizeApprox
                        ?: if (duration > 0.0) {
                            val bitrate = targetFormat?.abr ?: targetFormat?.tbr ?: 128.0
                            (bitrate * 1000.0 / 8.0) * duration
                        } else null

                    calcSize ?: (if (duration > 0.0) (16_000.0 * duration) else 4_500_000.0)
                } else {
                    info.fileSize ?: info.fileSizeApprox ?: .0
                }

                return ViewState(
                    url = info.originalUrl.toString(),
                    title = info.title,
                    uploader = info.uploader ?: info.channel ?: info.uploaderId.toString(),
                    extractorKey = info.extractorKey,
                    duration = info.duration?.roundToInt() ?: 0,
                    thumbnailUrl = info.thumbnail.toHttpsUrl(),
                    fileSizeApprox = estimatedSize,
                    videoFormats = videoFormats,
                    audioOnlyFormats = audioOnlyFormats,
                )
            }
        }
    }

    companion object {
        private const val PROGRESS_INDETERMINATE = -1f
    }
}
