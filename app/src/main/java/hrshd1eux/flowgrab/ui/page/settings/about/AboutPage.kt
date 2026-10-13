@file:Suppress("DEPRECATION")

package hrshd1eux.flowgrab.ui.page.settings.about

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.UpdateDisabled
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.UrlAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import hrshd1eux.flowgrab.App
import hrshd1eux.flowgrab.App.Companion.packageInfo
import hrshd1eux.flowgrab.R
import hrshd1eux.flowgrab.ui.common.booleanState
import hrshd1eux.flowgrab.ui.component.BackButton
import hrshd1eux.flowgrab.ui.component.ConfirmButton
import hrshd1eux.flowgrab.ui.component.PreferenceItem
import hrshd1eux.flowgrab.ui.component.PreferenceSwitchWithDivider
import hrshd1eux.flowgrab.util.AUTO_UPDATE
import hrshd1eux.flowgrab.util.PreferenceUtil
import hrshd1eux.flowgrab.util.ToastUtil

private const val releaseURL = "https://github.com/HrshD1eux/flowgrab-android/releases"
private const val repoUrl = "https://github.com/HrshD1eux/flowgrab-android"
private const val developerUrl = "https://github.com/HrshD1eux"
const val weblate = "https://hosted.weblate.org/engage/seal/"
const val YtdlpRepository = "https://github.com/yt-dlp/yt-dlp"
private const val TAG = "AboutPage"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutPage(
    onNavigateBack: () -> Unit,
    onNavigateToCreditsPage: () -> Unit = {},
    onNavigateToUpdatePage: () -> Unit,
    onNavigateToDonatePage: () -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var isAutoUpdateEnabled by AUTO_UPDATE.booleanState

    val info = App.getVersionReport()
    val versionName = packageInfo.versionName

    val uriHandler = LocalUriHandler.current
    fun openUrl(url: String) {
        uriHandler.openUri(url)
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(modifier = Modifier, text = stringResource(id = R.string.about)) },
                navigationIcon = { BackButton { onNavigateBack() } },
                scrollBehavior = scrollBehavior,
            )
        },
        content = {
            LazyColumn(modifier = Modifier.padding(it)) {
                item {
                    PreferenceItem(
                        title = stringResource(R.string.readme),
                        description = stringResource(R.string.readme_desc),
                        icon = Icons.Outlined.Description,
                    ) {
                        openUrl(repoUrl)
                    }
                }
                item {
                    PreferenceItem(
                        title = stringResource(R.string.release),
                        description = stringResource(R.string.release_desc),
                        icon = Icons.Outlined.NewReleases,
                    ) {
                        openUrl(releaseURL)
                    }
                }
                item {
                    PreferenceItem(
                        title = "Developer",
                        description = "HrshD1eux (GitHub Profile)",
                        icon = Icons.Outlined.Person,
                    ) {
                        openUrl(developerUrl)
                    }
                }
                item {
                    PreferenceSwitchWithDivider(
                        title = stringResource(R.string.auto_update),
                        description = stringResource(R.string.check_for_updates_desc),
                        icon =
                            if (isAutoUpdateEnabled) Icons.Outlined.Update
                            else Icons.Outlined.UpdateDisabled,
                        isChecked = isAutoUpdateEnabled,
                        isSwitchEnabled = !App.isFDroidBuild(),
                        onClick = onNavigateToUpdatePage,
                        onChecked = {
                            isAutoUpdateEnabled = !isAutoUpdateEnabled
                        },
                    )
                }
                item {
                    PreferenceItem(
                        title = stringResource(R.string.version),
                        description = versionName,
                        icon = Icons.Outlined.Info,
                    ) {
                        clipboardManager.setText(AnnotatedString(info))
                        ToastUtil.makeToast(R.string.info_copied)
                    }
                }
                item {
                    PreferenceItem(title = "Package name", description = context.packageName) {
                        clipboardManager.setText(AnnotatedString(context.packageName))
                        ToastUtil.makeToast(R.string.info_copied)
                    }
                }
            }
        },
    )
}

@OptIn(ExperimentalTextApi::class)
@Composable
@Preview
fun AutoUpdateUnavailableDialog(onDismissRequest: () -> Unit = {}) {
    val uriHandler = LocalUriHandler.current
    val hapticFeedback = LocalHapticFeedback.current
    val hyperLinkText = stringResource(id = R.string.switch_to_github_builds)
    val text = stringResource(id = R.string.auto_update_disabled_msg, "F-Droid", hyperLinkText)

    val annotatedString = buildAnnotatedString {
        append(text)
        val startIndex = text.indexOf(hyperLinkText)
        val endIndex = startIndex + hyperLinkText.length
        addUrlAnnotation(
            UrlAnnotation("https://github.com/HrshD1eux/flowgrab-android/releases/latest"),
            start = startIndex,
            end = endIndex,
        )
        addStyle(
            SpanStyle(
                color = MaterialTheme.colorScheme.tertiary,
                textDecoration = TextDecoration.Underline,
            ),
            start = startIndex,
            end = endIndex,
        )
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            ConfirmButton(stringResource(id = R.string.got_it)) { onDismissRequest() }
        },
        icon = { Icon(Icons.Outlined.UpdateDisabled, null) },
        title = {
            Text(
                text = stringResource(id = R.string.feature_unavailable),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            ClickableText(
                text = annotatedString,
                onClick = { index ->
                    annotatedString.getUrlAnnotations(index, index).firstOrNull()?.let {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        uriHandler.openUri(it.item.url)
                    }
                },
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        MaterialTheme.colorScheme.onSurfaceVariant
                    ),
            )
        },
    )
}
