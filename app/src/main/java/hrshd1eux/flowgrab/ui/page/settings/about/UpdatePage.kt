package hrshd1eux.flowgrab.ui.page.settings.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hrshd1eux.flowgrab.App
import hrshd1eux.flowgrab.R
import hrshd1eux.flowgrab.ui.common.booleanState
import hrshd1eux.flowgrab.ui.common.intState
import hrshd1eux.flowgrab.ui.component.BackButton
import hrshd1eux.flowgrab.ui.component.PreferenceInfo
import hrshd1eux.flowgrab.ui.component.PreferenceSingleChoiceItem
import hrshd1eux.flowgrab.ui.component.PreferenceSubtitle
import hrshd1eux.flowgrab.ui.component.PreferenceSwitchWithContainer
import hrshd1eux.flowgrab.ui.page.UpdateDialog
import hrshd1eux.flowgrab.util.AUTO_UPDATE
import hrshd1eux.flowgrab.util.PRE_RELEASE
import hrshd1eux.flowgrab.util.PreferenceUtil
import hrshd1eux.flowgrab.util.PreferenceUtil.updateBoolean
import hrshd1eux.flowgrab.util.PreferenceUtil.updateInt
import hrshd1eux.flowgrab.util.STABLE
import hrshd1eux.flowgrab.util.ToastUtil
import hrshd1eux.flowgrab.util.UPDATE_CHANNEL
import hrshd1eux.flowgrab.util.UpdateUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatePage(onNavigateBack: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var autoUpdate by AUTO_UPDATE.booleanState
    var updateChannel by UPDATE_CHANNEL.intState
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var release by remember { mutableStateOf(UpdateUtil.Release()) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showUnavailableDialog by remember { mutableStateOf(App.isFDroidBuild()) }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(modifier = Modifier, text = stringResource(id = R.string.auto_update))
                },
                navigationIcon = { BackButton { onNavigateBack() } },
                scrollBehavior = scrollBehavior,
            )
        },
        content = { paddings ->
            LazyColumn(modifier = Modifier.padding(paddings)) {
                item {
                    PreferenceSwitchWithContainer(
                        title = stringResource(id = R.string.enable_auto_update),
                        icon = null,
                        isChecked = autoUpdate,
                    ) {
                        autoUpdate = !autoUpdate
                    }
                }
                item {
                    PreferenceSubtitle(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        text = stringResource(id = R.string.update_channel),
                    )
                }
                item {
                    PreferenceSingleChoiceItem(
                        text = stringResource(id = R.string.stable_channel),
                        selected = updateChannel == STABLE,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    ) {
                        updateChannel = STABLE
                        UPDATE_CHANNEL.updateInt(updateChannel)
                    }
                }

                item {
                    PreferenceSingleChoiceItem(
                        text = stringResource(id = R.string.pre_release_channel),
                        selected = updateChannel == PRE_RELEASE,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    ) {
                        updateChannel = PRE_RELEASE
                        UPDATE_CHANNEL.updateInt(updateChannel)
                    }
                }
                item {
                    var isLoading by remember { mutableStateOf(false) }
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ProgressIndicatorButton(
                            modifier =
                                Modifier.padding(horizontal = 24.dp)
                                    .padding(top = 6.dp)
                                    .padding(bottom = 12.dp),
                            text = stringResource(id = R.string.check_for_updates),
                            icon = Icons.Outlined.Update,
                            isLoading = isLoading,
                        ) {
                            if (!isLoading)
                                scope.launch {
                                    runCatching {
                                            isLoading = true
                                            withContext(Dispatchers.IO) {
                                                UpdateUtil.checkForUpdate()?.let {
                                                    release = it
                                                    UpdateUtil.prefetchLatestApk(context, it)
                                                    showUpdateDialog = true
                                                }
                                                    ?: ToastUtil.makeToastSuspend(
                                                        context.getString(R.string.app_up_to_date)
                                                    )
                                            }
                                            isLoading = false
                                        }
                                        .onFailure {
                                            it.printStackTrace()
                                            ToastUtil.makeToastSuspend(
                                                context.getString(R.string.app_update_failed)
                                            )
                                            isLoading = false
                                        }
                                }
                        }
                    }
                    androidx.compose.material3.HorizontalDivider()
                }
                item {
                    PreferenceInfo(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        text = stringResource(id = R.string.update_channel_desc),
                    )
                }
            }
        },
    )
    if (showUpdateDialog)
        UpdateDialog(onDismissRequest = { showUpdateDialog = false }, release = release)

    if (showUnavailableDialog) {
        AutoUpdateUnavailableDialog {
            showUnavailableDialog = false
            onNavigateBack()
        }
    }
}

@Composable
fun ProgressIndicatorButton(
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        modifier = modifier,
        onClick = onClick,
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
    ) {
        if (isLoading)
            Box(modifier = Modifier.size(18.dp)) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp).align(Alignment.Center),
                    strokeWidth = 3.dp,
                )
            }
        else Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(text = text, modifier = Modifier.padding(start = 8.dp))
    }
}
