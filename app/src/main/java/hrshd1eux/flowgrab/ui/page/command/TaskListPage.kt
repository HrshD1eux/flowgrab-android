@file:Suppress("DEPRECATION")

package hrshd1eux.flowgrab.ui.page.command

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.NewLabel
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hrshd1eux.flowgrab.R
import hrshd1eux.flowgrab.database.objects.CommandTemplate
import hrshd1eux.flowgrab.download.DownloaderV2
import hrshd1eux.flowgrab.download.Task
import hrshd1eux.flowgrab.download.TaskFactory
import hrshd1eux.flowgrab.ui.common.HapticFeedback.slightHapticFeedback
import hrshd1eux.flowgrab.ui.common.intState
import hrshd1eux.flowgrab.ui.component.BackButton
import hrshd1eux.flowgrab.ui.component.ClearButton
import hrshd1eux.flowgrab.ui.component.CustomCommandTaskItem
import hrshd1eux.flowgrab.ui.component.DismissButton
import hrshd1eux.flowgrab.ui.component.FilledButtonWithIcon
import hrshd1eux.flowgrab.ui.component.OutlinedButtonChip
import hrshd1eux.flowgrab.ui.component.OutlinedButtonWithIcon
import hrshd1eux.flowgrab.ui.component.PasteFromClipBoardButton
import hrshd1eux.flowgrab.ui.component.SealDialog
import hrshd1eux.flowgrab.ui.component.SealModalBottomSheetM2
import hrshd1eux.flowgrab.ui.component.TaskStatus
import hrshd1eux.flowgrab.ui.page.settings.command.CommandTemplateDialog
import hrshd1eux.flowgrab.util.PreferenceUtil
import hrshd1eux.flowgrab.util.PreferenceUtil.updateInt
import hrshd1eux.flowgrab.util.TEMPLATE_ID
import hrshd1eux.flowgrab.util.ToastUtil
import hrshd1eux.flowgrab.util.matchUrlFromString
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TaskListPage(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (Int) -> Unit,
    downloader: DownloaderV2 = koinInject(),
) {
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState =
        androidx.compose.material.rememberModalBottomSheetState(
            skipHalfExpanded = true,
            initialValue = ModalBottomSheetValue.Hidden,
        )

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.running_tasks),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                    )
                },
                navigationIcon = { BackButton { onNavigateBack() } },
                actions = {},
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    scope.launch {
                        showBottomSheet = true
                        delay(50)
                        sheetState.show()
                    }
                },
                modifier = Modifier.padding(vertical = 18.dp, horizontal = 6.dp),
            ) {
                Icon(Icons.Outlined.Add, stringResource(id = R.string.new_task))
            }
        },
    ) { paddings ->
        val clipboardManager = LocalClipboardManager.current
        val taskStateMap = downloader.getTaskStateMap()
        val commandTasks =
            remember(taskStateMap.size) {
                androidx.compose.runtime.derivedStateOf {
                    taskStateMap.entries
                        .filter { it.key.type is Task.TypeInfo.CustomCommand }
                        .sortedBy { (_, state) -> state.downloadState.toStatus() }
                }
            }.value

        LazyColumn(
            modifier = Modifier.padding(paddings),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                commandTasks,
                key = { it.key.id },
            ) { entry ->
                val task = entry.key
                val state = entry.value
                val downloadState = state.downloadState
                val template = (task.type as Task.TypeInfo.CustomCommand).template
                val progress =
                    if (downloadState is Task.DownloadState.Running) downloadState.progress else 0f
                val progressText =
                    if (downloadState is Task.DownloadState.Running) downloadState.progressText else ""

                CustomCommandTaskItem(
                    status = downloadState.toStatus(),
                    progress = progress,
                    progressText = progressText,
                    url = task.url,
                    templateName = template.name,
                    onCancel = { downloader.cancel(task) },
                    onCopyError = {
                        val err =
                            if (downloadState is Task.DownloadState.Error) {
                                downloadState.throwable.message
                                    ?: downloadState.throwable.stackTraceToString()
                            } else {
                                ""
                            }
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(err))
                        ToastUtil.makeToast(R.string.error_copied)
                    },
                    onRestart = { downloader.restart(task) },
                    onCopyLog = {
                        clipboardManager.setText(
                            androidx.compose.ui.text.AnnotatedString(downloader.getTaskOutput(task))
                        )
                    },
                    onShowLog = { onNavigateToDetail(task.id.hashCode()) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
    val onDismissRequest: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { showBottomSheet = false }
    }

    BackHandler(showBottomSheet) { onDismissRequest() }

    if (showBottomSheet)
        SealModalBottomSheetM2(
            sheetState = sheetState,
            sheetContent = {
                val clipboardManager = LocalClipboardManager.current

                var showTemplateSelectionDialog by remember { mutableStateOf(false) }
                var showTemplateCreatorDialog by remember { mutableStateOf(false) }
                var showTemplateEditorDialog by remember { mutableStateOf(false) }

                val template by
                    remember(
                        showTemplateCreatorDialog,
                        showTemplateSelectionDialog,
                        showTemplateEditorDialog,
                    ) {
                        mutableStateOf(PreferenceUtil.getTemplate())
                    }

                var url by remember { mutableStateOf("") }

                LaunchedEffect(sheetState.targetValue) {
                    if (sheetState.targetValue == ModalBottomSheetValue.Expanded)
                        url = matchUrlFromString(clipboardManager.getText()?.text.toString(), true)
                }

                Column(Modifier.fillMaxWidth()) {
                    TaskCreatorDialogContent(
                        url = url,
                        onValueChange = { url = it },
                        template = template,
                        onTemplateSelectionClicked = { showTemplateSelectionDialog = true },
                        onNewTemplateClicked = { showTemplateCreatorDialog = true },
                        onEditClicked = { showTemplateEditorDialog = true },
                    )
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        item {
                            OutlinedButtonWithIcon(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                onClick = onDismissRequest,
                                icon = Icons.Outlined.Cancel,
                                text = stringResource(R.string.cancel),
                            )
                        }
                        item {
                            FilledButtonWithIcon(
                                onClick = {
                                    view.slightHapticFeedback()
                                    downloader.enqueue(
                                        TaskFactory.createWithCustomCommand(
                                            url = url,
                                            template = template,
                                        )
                                    )
                                    onDismissRequest()
                                },
                                icon = Icons.Outlined.DownloadDone,
                                text = stringResource(R.string.start),
                            )
                        }
                    }
                }
                if (showTemplateSelectionDialog) {
                    TemplatePickerDialog() { showTemplateSelectionDialog = false }
                }
                if (showTemplateCreatorDialog) {
                    CommandTemplateDialog(
                        onDismissRequest = { showTemplateCreatorDialog = false },
                        confirmationCallback = { scope.launch { TEMPLATE_ID.updateInt(it) } },
                    )
                }
                if (showTemplateEditorDialog) {
                    CommandTemplateDialog(
                        commandTemplate = template,
                        onDismissRequest = { showTemplateEditorDialog = false },
                    )
                }
            },
        )
}

private fun Task.DownloadState.toStatus(): TaskStatus =
    when (this) {
        is Task.DownloadState.Canceled -> TaskStatus.CANCELED
        is Task.DownloadState.Completed -> TaskStatus.FINISHED
        is Task.DownloadState.Error -> TaskStatus.ERROR
        is Task.DownloadState.Running,
        Task.DownloadState.Idle,
        is Task.DownloadState.FetchingInfo,
        Task.DownloadState.ReadyWithInfo -> TaskStatus.RUNNING
    }

@Composable
fun ColumnScope.TaskCreatorDialogContent(
    url: String,
    onValueChange: (String) -> Unit = {},
    template: CommandTemplate,
    onTemplateSelectionClicked: () -> Unit = {},
    onNewTemplateClicked: () -> Unit = {},
    onEditClicked: () -> Unit = {},
) {
    Icon(
        modifier = Modifier.align(Alignment.CenterHorizontally),
        imageVector = Icons.Outlined.Add,
        contentDescription = null,
    )
    Text(
        text = stringResource(id = R.string.new_task),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 16.dp),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
    )
    Text(
        text = stringResource(R.string.custom_command_desc),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 24.dp),
    )

    OutlinedTextField(
        value = url,
        onValueChange = onValueChange,
        label = { Text(text = stringResource(id = R.string.video_url)) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
        maxLines = 3,
        trailingIcon = {
            if (url.isNotEmpty()) {
                ClearButton { onValueChange("") }
            } else {
                PasteFromClipBoardButton(onPaste = onValueChange)
            }
        },
        textStyle = LocalTextStyle.current.merge(fontFamily = FontFamily.Monospace),
    )

    LazyRow(
        modifier = Modifier.padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            OutlinedButtonChip(
                icon = Icons.Outlined.Code,
                label = template.name,
                onClick = onTemplateSelectionClicked,
            )
        }
        item {
            OutlinedButtonChip(
                icon = Icons.Outlined.Edit,
                label = stringResource(id = R.string.edit_template, template.name),
                onClick = onEditClicked,
            )
        }
        item {
            OutlinedButtonChip(
                icon = Icons.Outlined.NewLabel,
                label = stringResource(id = R.string.new_template),
                onClick = onNewTemplateClicked,
            )
        }
    }
}

@Composable
fun TemplatePickerDialog(onDismissRequest: () -> Unit = {}) {
    val templateList by PreferenceUtil.templateListStateFlow.collectAsStateWithLifecycle()
    var selectedId by TEMPLATE_ID.intState
    val scrollState =
        rememberLazyListState(
            initialFirstVisibleItemIndex =
                templateList
                    .indexOfFirst { it.id == selectedId }
                    .run { if (this == -1) 0 else this }
        )

    SealDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = { DismissButton(onClick = onDismissRequest) },
        title = { Text(text = stringResource(id = R.string.template_selection)) },
        icon = { Icon(imageVector = Icons.Outlined.Code, contentDescription = null) },
        text = {
            Box(modifier = Modifier.heightIn(max = 450.dp)) {
                androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.align(Alignment.TopCenter)
                )
                LazyColumn(state = scrollState) {
                    item { Spacer(modifier = Modifier.height(4.dp)) }
                    items(templateList) {
                        TemplateSingleChoiceItem(
                            text = it.name,
                            supportingText = it.template,
                            selected = it.id == selectedId,
                        ) {
                            selectedId = it.id
                            TEMPLATE_ID.updateInt(it.id)
                            onDismissRequest()
                        }
                    }
                    item { Spacer(modifier = Modifier.height(4.dp)) }
                }
                androidx.compose.material3.HorizontalDivider(
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        },
    )
}

@Composable
fun TemplateSingleChoiceItem(
    modifier: Modifier = Modifier,
    text: String,
    supportingText: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            modifier
                .selectable(selected = selected, enabled = true, onClick = onClick)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        RadioButton(
            modifier = Modifier.padding(end = 8.dp).clearAndSetSemantics {},
            selected = selected,
            onClick = onClick,
        )
        Column {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = supportingText.replace("\n", " "),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
