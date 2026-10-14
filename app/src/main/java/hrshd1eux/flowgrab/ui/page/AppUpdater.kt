package hrshd1eux.flowgrab.ui.page

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hrshd1eux.flowgrab.R
import hrshd1eux.flowgrab.util.PreferenceUtil
import hrshd1eux.flowgrab.util.UpdateUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppUpdater() {
    val context = LocalContext.current
    var showUpdateDialog by rememberSaveable { mutableStateOf(false) }
    var release by remember { mutableStateOf(UpdateUtil.Release()) }
    val downloadStatus by UpdateUtil.downloadStatus.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (!PreferenceUtil.isNetworkAvailableForDownload() || !PreferenceUtil.isAutoUpdateEnabled())
            return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching {
                UpdateUtil.checkForUpdate()?.let {
                    release = it
                    showUpdateDialog = true
                }
            }.onFailure { it.printStackTrace() }
        }
    }

    if (showUpdateDialog) {
        UpdateDialog(
            onDismissRequest = { showUpdateDialog = false },
            release = release,
        )
    }

    // When background download completes, ask the user to press the Install button via pop-up dialog
    val readyRelease = UpdateUtil.readyToInstallRelease
    if (downloadStatus is UpdateUtil.DownloadStatus.Finished && readyRelease != null && !showUpdateDialog) {
        InstallConfirmationDialog(
            onDismissRequest = {
                UpdateUtil.readyToInstallRelease = null
            },
            release = readyRelease,
            onInstall = {
                UpdateUtil.readyToInstallRelease = null
                UpdateUtil.installLatestApk(context)
            }
        )
    }
}

@Composable
fun InstallConfirmationDialog(
    onDismissRequest: () -> Unit,
    release: UpdateUtil.Release,
    onInstall: () -> Unit,
) {
    val versionName = (release.name?.takeIf { it.isNotBlank() } ?: release.tagName ?: "").toString()
    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = { Icon(Icons.Outlined.NewReleases, contentDescription = null) },
        title = { Text(stringResource(R.string.update_ready_title)) },
        text = { Text(stringResource(R.string.update_ready_desc, versionName)) },
        confirmButton = {
            Button(onClick = onInstall) {
                Text(stringResource(R.string.install))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.later))
            }
        }
    )
}
