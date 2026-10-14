package hrshd1eux.flowgrab.download

import android.app.PendingIntent
import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateMap
import hrshd1eux.flowgrab.App
import hrshd1eux.flowgrab.R
import hrshd1eux.flowgrab.download.Task.DownloadState
import hrshd1eux.flowgrab.download.Task.DownloadState.Canceled
import hrshd1eux.flowgrab.download.Task.DownloadState.Completed
import hrshd1eux.flowgrab.download.Task.DownloadState.Error
import hrshd1eux.flowgrab.download.Task.DownloadState.FetchingInfo
import hrshd1eux.flowgrab.download.Task.DownloadState.Idle
import hrshd1eux.flowgrab.download.Task.DownloadState.ReadyWithInfo
import hrshd1eux.flowgrab.download.Task.DownloadState.Running
import hrshd1eux.flowgrab.download.Task.RestartableAction.Download
import hrshd1eux.flowgrab.download.Task.RestartableAction.FetchInfo
import hrshd1eux.flowgrab.download.Task.TypeInfo
import hrshd1eux.flowgrab.database.objects.DownloadedVideoInfo
import hrshd1eux.flowgrab.util.DatabaseUtil
import hrshd1eux.flowgrab.util.DownloadUtil
import hrshd1eux.flowgrab.util.FileUtil
import hrshd1eux.flowgrab.util.NotificationUtil
import hrshd1eux.flowgrab.util.PreferenceUtil
import hrshd1eux.flowgrab.util.ToastUtil
import hrshd1eux.flowgrab.util.UpdateUtil
import hrshd1eux.flowgrab.util.VideoInfo
import com.yausername.youtubedl_android.YoutubeDL
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import org.koin.core.component.KoinComponent

private const val TAG = "DownloaderV2"

private const val MAX_CONCURRENCY = 3

interface DownloaderV2 {
    fun getTaskStateMap(): SnapshotStateMap<Task, Task.State>

    fun cancel(task: Task): Boolean

    fun cancel(taskId: String): Boolean {
        return getTaskStateMap().keys.find { it.id == taskId }?.let { cancel(it) } ?: false
    }

    fun restart(task: Task)

    /** Enqueue a [Task] with an empty [Task.State] */
    fun enqueue(task: Task)

    fun enqueue(task: Task, state: Task.State)

    fun enqueue(taskWithState: TaskFactory.TaskWithState) {
        val (task, state) = taskWithState
        enqueue(task, state)
    }

    fun remove(task: Task): Boolean

    fun getTaskOutput(task: Task): String

    fun isBusy(): Boolean
}

internal object FakeDownloaderV2 : DownloaderV2 {
    override fun getTaskStateMap(): SnapshotStateMap<Task, Task.State> {
        return mutableStateMapOf()
    }

    override fun cancel(task: Task): Boolean {
        return false
    }

    override fun restart(task: Task) {}

    override fun enqueue(task: Task) {}

    override fun enqueue(task: Task, state: Task.State) {}

    override fun remove(task: Task): Boolean {
        return true
    }

    override fun getTaskOutput(task: Task): String = ""

    override fun isBusy(): Boolean = false
}

/**
 * TODO:
 *     - Notification
 *     - Custom commands
 *     - States for ViewModels
 */
@OptIn(FlowPreview::class)
class DownloaderV2Impl(private val appContext: Context) : DownloaderV2, KoinComponent {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val taskStateMap = mutableStateMapOf<Task, Task.State>()
    private val taskOutputMap = java.util.concurrent.ConcurrentHashMap<String, StringBuilder>()
    private val snapshotFlow = snapshotFlow { taskStateMap.toMap() }

    init {
        scope.launch(Dispatchers.Default) {
            snapshotFlow
                .onEach { doYourWork() }
                .map { it.countRunning() }
                .distinctUntilChanged()
                .collect { if (it > 0) App.startService() else App.stopService() }
        }

        scope.launch(Dispatchers.IO) {
            // don't write before we read
            enqueueFromBackup()

            snapshotFlow
                .distinctUntilChanged()
                .collect {
                    it.forEach { Log.d(TAG, it.value.viewState.title) }
                    PreferenceUtil.encodeTaskListBackup(it)
                }
        }
    }

    private fun enqueueFromBackup() {
        val taskList =
            PreferenceUtil.decodeTaskListBackup()
                .mapValues { (_, state) ->
                    val preState = state.downloadState
                    val downloadState =
                        when (preState) {
                            is FetchingInfo,
                            Idle -> {
                                Canceled(action = FetchInfo)
                            }
                            is Running -> {
                                Canceled(action = Download, progress = preState.progress)
                            }

                            ReadyWithInfo -> {
                                Canceled(action = Download, progress = null)
                            }
                            else -> {
                                preState
                            }
                        }
                    var updatedViewState = state.viewState
                    if (downloadState is Completed) {
                        val path = downloadState.filePath
                        if (!path.isNullOrBlank()) {
                            val f = File(path)
                            if (f.exists() && f.length() > 0L) {
                                updatedViewState = updatedViewState.copy(fileSizeApprox = f.length().toDouble())
                            }
                        }
                    }
                    state.copy(downloadState = downloadState, viewState = updatedViewState)
                }
        taskList.forEach(::enqueue)
    }

    private fun Map<Task, Task.State>.countRunning(): Int = count { (_, state) ->
        state.downloadState is Running || state.downloadState is FetchingInfo
    }

    private val autoHealedTaskIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private fun Throwable.isExtractorOrBotIssue(): Boolean {
        val msg = (message ?: localizedMessage ?: "").lowercase()
        return msg.contains("confirm you're not a bot") ||
            msg.contains("sign in to confirm") ||
            msg.contains("403: forbidden") ||
            msg.contains("signature extraction failed") ||
            msg.contains("n challenge") ||
            msg.contains("player version") ||
            msg.contains("unable to extract") ||
            msg.contains("the page needs to be reloaded") ||
            msg.contains("unsupported client") ||
            msg.contains("unplayable") ||
            msg.contains("video unavailable") ||
            msg.contains("requested format is not available")
    }

    private fun autoHealAndRetry(task: Task, action: Task.RestartableAction) {
        if (!autoHealedTaskIds.add(task.id)) return
        if (task.downloadState is DownloadState.Canceled) return
        Log.i(TAG, "Extractor or bot issue detected for task ${task.id}. Attempting self-healing OTA update...")
        scope.launch(Dispatchers.IO) {
            val updateSuccess = runCatching {
                ToastUtil.makeToastSuspend("Updating download engine...")
                UpdateUtil.updateYtDlp()
            }.isSuccess
            withContext(Dispatchers.Main) {
                if (task.downloadState is DownloadState.Canceled) {
                    return@withContext
                }
                if (updateSuccess) {
                    ToastUtil.makeToastSuspend("Engine updated, retrying...")
                    task.restartImpl(action)
                } else {
                    task.downloadState = Error(
                        throwable = Exception("Download failed: please check network connection or retry"),
                        action = action,
                    )
                }
            }
        }
    }

    override fun getTaskStateMap(): SnapshotStateMap<Task, Task.State> {
        return taskStateMap
    }

    override fun getTaskOutput(task: Task): String {
        return taskOutputMap[task.id]?.toString().orEmpty()
    }

    override fun isBusy(): Boolean {
        return taskStateMap.any { (_, state) ->
            state.downloadState is Running || state.downloadState is FetchingInfo
        }
    }

    private fun updateTaskState(task: Task, transform: (Task.State) -> Task.State) {
        synchronized(taskStateMap) {
            val current = taskStateMap[task] ?: return
            taskStateMap[task] = transform(current)
        }
    }

    override fun enqueue(task: Task) {
        synchronized(taskStateMap) {
            taskStateMap[task] =
                Task.State(Idle, null, Task.ViewState(url = task.url, title = task.url))
        }
    }

    override fun enqueue(task: Task, state: Task.State) {
        synchronized(taskStateMap) {
            taskStateMap[task] = state
        }
    }

    /**
     * Noted the caller is responsible for stopping the [task] before removing it
     *
     * @return true if the task was removed
     */
    override fun remove(task: Task): Boolean {
        autoHealedTaskIds.remove(task.id)
        taskOutputMap.remove(task.id)
        return synchronized(taskStateMap) {
            taskStateMap.remove(task) != null
        }
    }

    override fun cancel(task: Task): Boolean = task.cancelImpl()

    override fun restart(task: Task) {
        autoHealedTaskIds.remove(task.id)
        task.restartImpl()
    }

    private var Task.state: Task.State?
        get() = synchronized(taskStateMap) { taskStateMap[this] }
        set(value) {
            if (value != null) {
                synchronized(taskStateMap) {
                    taskStateMap[this] = value
                }
            }
        }

    private var Task.downloadState: DownloadState
        get() = state?.downloadState ?: DownloadState.Canceled(Task.RestartableAction.FetchInfo, null)
        set(value) {
            updateTaskState(this) { it.copy(downloadState = value) }
        }

    private var Task.info: VideoInfo?
        get() = state?.videoInfo
        set(value) {
            updateTaskState(this) { it.copy(videoInfo = value) }
        }

    private var Task.viewState: Task.ViewState
        get() = state?.viewState ?: Task.ViewState(url = url)
        set(value) {
            updateTaskState(this) { it.copy(viewState = value) }
        }

    private val Task.notificationId: Int
        get() = id.hashCode()

    /** Processes pending tasks, prioritizing downloads. */
    private fun doYourWork() {
        if (taskStateMap.countRunning() >= MAX_CONCURRENCY) return

        taskStateMap.entries
            .sortedBy { (_, state) -> state.downloadState }
            .firstOrNull { (_, state) ->
                state.downloadState == ReadyWithInfo || state.downloadState == Idle
            }
            ?.let { (task, state) ->
                when (state.downloadState) {
                    Idle -> task.prepare()
                    ReadyWithInfo -> task.download()
                    else -> {
                        throw IllegalStateException()
                    }
                }
            }
    }

    private fun Task.prepare() {
        check(downloadState == Idle)
        if (type is TypeInfo.CustomCommand) {
            execute()
        } else {
            fetchInfo()
        }
    }

    private fun Task.fetchInfo() {
        check(downloadState == Idle)
        val task = this
        val taskInfo = task.type
        val playlistIndex = if (taskInfo is TypeInfo.Playlist) taskInfo.index else null
        scope
            .launch(Dispatchers.Default) {
                DownloadUtil.fetchVideoInfoFromUrl(
                        url = url,
                        playlistIndex = playlistIndex,
                        preferences = preferences,
                        taskKey = id,
                    )
                    .onSuccess {
                        if (task.downloadState is DownloadState.Canceled) {
                            return@onSuccess
                        }
                        info = it
                        downloadState = ReadyWithInfo
                        viewState = Task.ViewState.fromVideoInfo(it, preferences)
                    }
                    .onFailure { throwable ->
                        Log.e(TAG, "Task ${task.id} (${task.url}) fetchInfo failed", throwable)
                        val isCancelled = task.downloadState is DownloadState.Canceled ||
                            throwable is YoutubeDL.CanceledException ||
                            throwable is kotlinx.coroutines.CancellationException ||
                            throwable.message?.contains("destroy", ignoreCase = true) == true ||
                            throwable.message?.contains("cancel", ignoreCase = true) == true
                        if (isCancelled) {
                            return@onFailure
                        }
                        if (throwable.isExtractorOrBotIssue() && !autoHealedTaskIds.contains(task.id)) {
                            autoHealAndRetry(task, FetchInfo)
                            return@onFailure
                        }
                        task.downloadState = Error(throwable = throwable, action = FetchInfo)
                        NotificationUtil.notifyError(
                            title = viewState.title,
                            textId = R.string.download_error_msg,
                            notificationId = notificationId,
                            report = throwable.stackTraceToString(),
                        )
                    }
            }
            .also { job -> downloadState = FetchingInfo(job = job, taskId = id) }
    }

    private fun Task.download() {
        if (type is TypeInfo.CustomCommand) {
            execute()
            return
        }
        check(downloadState == ReadyWithInfo && info != null)
        scope
            .launch(Dispatchers.Default) {
                DownloadUtil.downloadVideo(
                        videoInfo = info,
                        taskId = id,
                        downloadPreferences = preferences,
                        progressCallback = { progressPercentage, _, text ->
                            val progress = progressPercentage / 100f
                            when (val preState = downloadState) {
                                is Running -> {
                                    downloadState =
                                        preState.copy(progress = progress, progressText = text)
                                    NotificationUtil.notifyProgress(
                                        notificationId = notificationId,
                                        progress = progressPercentage.toInt(),
                                        text = text,
                                        title = viewState.title,
                                        taskId = id,
                                    )
                                }
                                else -> {}
                            }
                        },
                    )
                    .onSuccess { pathList ->
                        if (downloadState is DownloadState.Canceled) {
                            return@onSuccess
                        }
                        val completedPath = pathList.firstOrNull()
                        downloadState = Completed(completedPath)

                        if (!completedPath.isNullOrBlank()) {
                            val actualFile = File(completedPath)
                            if (actualFile.exists() && actualFile.length() > 0L) {
                                viewState = viewState.copy(fileSizeApprox = actualFile.length().toDouble())
                            }
                            runCatching {
                                DatabaseUtil.insertInfo(
                                    DownloadedVideoInfo(
                                        id = 0,
                                        videoTitle = viewState.title,
                                        videoAuthor = viewState.uploader,
                                        videoUrl = url,
                                        thumbnailUrl = viewState.thumbnailUrl.orEmpty(),
                                        videoPath = completedPath,
                                        extractor = info?.extractorKey.orEmpty(),
                                    )
                                )
                            }
                        }

                        val text =
                            appContext.getString(
                                if (pathList.isEmpty()) R.string.status_completed
                                else R.string.download_finish_notification
                            )
                        FileUtil.createIntentForOpeningFile(pathList.firstOrNull()).run {
                            NotificationUtil.finishNotification(
                                notificationId,
                                title = viewState.title,
                                text = text,
                                intent =
                                    if (this != null)
                                        PendingIntent.getActivity(
                                            appContext,
                                            0,
                                            this,
                                            PendingIntent.FLAG_IMMUTABLE,
                                        )
                                    else null,
                            )
                        }
                    }
                    .onFailure { throwable ->
                        Log.e(TAG, "Task ${id} (${url}) download failed", throwable)
                        val isCancelled = downloadState is DownloadState.Canceled ||
                            throwable is YoutubeDL.CanceledException ||
                            throwable is kotlinx.coroutines.CancellationException ||
                            throwable.message?.contains("destroy", ignoreCase = true) == true ||
                            throwable.message?.contains("cancel", ignoreCase = true) == true
                        if (isCancelled) {
                            return@onFailure
                        }
                        if (throwable.isExtractorOrBotIssue() && !autoHealedTaskIds.contains(id)) {
                            autoHealAndRetry(this@download, Download)
                            return@onFailure
                        }
                        downloadState = Error(throwable = throwable, action = Download)
                        NotificationUtil.notifyError(
                            title = viewState.title,
                            textId = R.string.download_error_msg,
                            notificationId = notificationId,
                            report = throwable.stackTraceToString(),
                        )
                    }
            }
            .also { job -> downloadState = Running(job = job, taskId = id) }
    }

    private fun Task.cancelImpl(): Boolean {
        when (val preState = downloadState) {
            is DownloadState.Cancelable -> {
                runCatching { YoutubeDL.destroyProcessById(preState.taskId) }
                preState.job.cancel()
                val progress = if (preState is Running) preState.progress else null
                NotificationUtil.cancelNotification(notificationId)
                downloadState =
                    DownloadState.Canceled(action = preState.action, progress = progress)
                return true
            }
            Idle -> {
                downloadState = DownloadState.Canceled(action = FetchInfo)
                return true
            }
            ReadyWithInfo -> {
                downloadState = DownloadState.Canceled(action = Download)
                return true
            }

            else -> {
                return false
            }
        }
    }

    private fun Task.restartImpl(targetAction: Task.RestartableAction? = null) {
        val action = targetAction ?: when (val preState = downloadState) {
            is DownloadState.Restartable -> preState.action
            is FetchingInfo -> FetchInfo
            is Running -> Download
            is ReadyWithInfo -> Download
            is Idle -> FetchInfo
            else -> if (info != null) Download else FetchInfo
        }
        downloadState =
            if (type is TypeInfo.CustomCommand) {
                Idle
            } else {
                when (action) {
                    Download -> if (info != null) ReadyWithInfo else Idle
                    FetchInfo -> Idle
                }
            }
    }

    /**
     * Execute a custom command task
     *
     * @see Task.TypeInfo.CustomCommand
     */
    private fun Task.execute() {
        check(downloadState == Idle || downloadState == ReadyWithInfo)
        check(type is TypeInfo.CustomCommand)
        val template = type.template
        val outputBuilder = taskOutputMap.computeIfAbsent(id) { StringBuilder() }
        outputBuilder.setLength(0)
        scope
            .launch {
                DownloadUtil.executeCustomCommandTask(url, id, template, preferences) {
                        progressPercentage,
                        _,
                        text ->
                        outputBuilder.appendLine(text)
                        val progress = progressPercentage / 100f
                        when (val preState = downloadState) {
                            is Running -> {
                                downloadState =
                                    preState.copy(progress = progress, progressText = text)
                                NotificationUtil.makeNotificationForCustomCommand(
                                    notificationId = notificationId,
                                    taskId = id,
                                    progress = progressPercentage.toInt(),
                                    templateName = template.name,
                                    taskUrl = url,
                                    text = text,
                                )
                            }
                            else -> {}
                        }
                    }
                    .onFailure { throwable ->
                        if (downloadState is DownloadState.Canceled ||
                            throwable is YoutubeDL.CanceledException ||
                            throwable is kotlinx.coroutines.CancellationException
                        ) {
                            return@onFailure
                        }
                        outputBuilder.appendLine("\nError: ${throwable.message}\n${throwable.stackTraceToString()}")
                        if (throwable.isExtractorOrBotIssue() && !autoHealedTaskIds.contains(id)) {
                            autoHealAndRetry(this@execute, Download)
                            return@onFailure
                        }
                        downloadState = Error(throwable = throwable, action = Download)
                        NotificationUtil.notifyError(
                            title = viewState.title,
                            textId = R.string.download_error_msg,
                            notificationId = notificationId,
                            report = throwable.stackTraceToString(),
                        )
                    }
                    .onSuccess { response ->
                        if (downloadState is DownloadState.Canceled) {
                            return@onSuccess
                        }
                        if (response.out.isNotEmpty() && outputBuilder.isEmpty()) {
                            outputBuilder.append(response.out)
                        }
                        if (response.err.isNotEmpty()) {
                            outputBuilder.appendLine("\n${response.err}")
                        }
                        downloadState = Completed(null)

                        val text = appContext.getString(R.string.status_completed)

                        NotificationUtil.finishNotification(
                            notificationId = notificationId,
                            title = viewState.title,
                            text = text,
                            intent = null,
                        )
                    }
            }
            .also { downloadState = Running(job = it, taskId = id) }
    }
}
