package hrshd1eux.flowgrab.ui.page

import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hrshd1eux.flowgrab.R
import hrshd1eux.flowgrab.util.ToastUtil
import hrshd1eux.flowgrab.util.UpdateUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Composable
fun UpdateDialog(onDismissRequest: () -> Unit, release: UpdateUtil.Release) {
    val context = LocalContext.current
    val downloadStatus by UpdateUtil.downloadStatus.collectAsStateWithLifecycle()

    UpdateDialogImpl(
        onDismissRequest = onDismissRequest,
        title = (release.name?.takeIf { it.isNotBlank() } ?: release.tagName ?: "Update").toString(),
        onConfirmUpdate = {
            UpdateUtil.startBackgroundDownload(context = context, release = release)
        },
        releaseNote = release.body.orEmpty(),
        downloadStatus = downloadStatus,
    )
}

@Composable
fun UpdateDialogImpl(
    onDismissRequest: () -> Unit,
    title: String,
    onConfirmUpdate: () -> Unit,
    releaseNote: String,
    downloadStatus: UpdateUtil.DownloadStatus,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        icon = { Icon(Icons.Outlined.NewReleases, null) },
        confirmButton = {
            Button(
                onClick = {
                    if (downloadStatus is UpdateUtil.DownloadStatus.Finished) {
                        UpdateUtil.installLatestApk(context)
                    } else if (downloadStatus !is UpdateUtil.DownloadStatus.Progress && downloadStatus !is UpdateUtil.DownloadStatus.Connecting) {
                        onConfirmUpdate()
                    }
                }
            ) {
                Text(
                    when (downloadStatus) {
                        is UpdateUtil.DownloadStatus.Connecting -> stringResource(R.string.connecting)
                        is UpdateUtil.DownloadStatus.Progress -> {
                            if (downloadStatus.percent >= 0) "${downloadStatus.percent} %"
                            else stringResource(R.string.downloading_indicator_text)
                        }
                        is UpdateUtil.DownloadStatus.Finished -> stringResource(R.string.install)
                        is UpdateUtil.DownloadStatus.Failed -> "Retry"
                        else -> stringResource(R.string.update)
                    },
                    modifier = Modifier.animateContentSize(),
                )
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismissRequest) {
                Text(text = stringResource(id = R.string.dismiss))
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (downloadStatus is UpdateUtil.DownloadStatus.Failed) {
                    Text(
                        text = "Error: ${downloadStatus.error}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Text(releaseNote)
            }
        },
    )
}

@Preview
@Composable
private fun Preview() {
    var b by remember { mutableStateOf(false) }
    val flow: MutableStateFlow<UpdateUtil.DownloadStatus> = remember {
        MutableStateFlow(UpdateUtil.DownloadStatus.NotYet)
    }

    LaunchedEffect(b) {
        if (b) {
            repeat(100) { i ->
                flow.update { UpdateUtil.DownloadStatus.Progress(percent = i) }
                delay(50)
            }
        } else {
            flow.update { UpdateUtil.DownloadStatus.NotYet }
        }
    }

    val status by flow.collectAsStateWithLifecycle()

    UpdateDialogImpl(
        onDismissRequest = { b = false },
        title = "v1.12.0",
        onConfirmUpdate = { b = true },
        releaseNote = "ReleaseNoteHTML",
        downloadStatus = status,
    )
}
