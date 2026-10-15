package hrshd1eux.flowgrab.ui.page.downloadv2.configure

import androidx.activity.compose.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NewLabel
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hrshd1eux.flowgrab.App
import hrshd1eux.flowgrab.R
import hrshd1eux.flowgrab.ui.common.AsyncImageImpl
import hrshd1eux.flowgrab.ui.common.HapticFeedback.longPressHapticFeedback
import hrshd1eux.flowgrab.ui.common.motion.materialSharedAxisX
import hrshd1eux.flowgrab.ui.component.ButtonChip
import hrshd1eux.flowgrab.ui.component.DrawerSheetSubtitle
import hrshd1eux.flowgrab.ui.component.OutlinedButtonWithIcon
import hrshd1eux.flowgrab.ui.component.SealModalBottomSheet
import hrshd1eux.flowgrab.ui.component.SealModalBottomSheetM2Variant
import hrshd1eux.flowgrab.ui.component.SingleChoiceChip
import hrshd1eux.flowgrab.ui.component.SingleChoiceSegmentedButton
import hrshd1eux.flowgrab.ui.component.VideoFilterChip
import hrshd1eux.flowgrab.ui.page.command.TemplatePickerDialog
import hrshd1eux.flowgrab.ui.page.downloadv2.configure.ActionButton.Download
import hrshd1eux.flowgrab.ui.page.downloadv2.configure.ActionButton.FetchInfo
import hrshd1eux.flowgrab.ui.page.downloadv2.configure.ActionButton.StartTask
import hrshd1eux.flowgrab.ui.page.downloadv2.configure.DownloadDialogViewModel.Action
import hrshd1eux.flowgrab.ui.page.downloadv2.configure.DownloadDialogViewModel.SelectionState
import hrshd1eux.flowgrab.ui.page.downloadv2.configure.DownloadDialogViewModel.SheetState.Configure
import hrshd1eux.flowgrab.ui.page.downloadv2.configure.DownloadDialogViewModel.SheetState.Error
import hrshd1eux.flowgrab.ui.page.downloadv2.configure.DownloadDialogViewModel.SheetState.InputUrl
import hrshd1eux.flowgrab.ui.page.downloadv2.configure.DownloadDialogViewModel.SheetState.Loading
import hrshd1eux.flowgrab.ui.page.settings.command.CommandTemplateDialog
import hrshd1eux.flowgrab.ui.page.settings.format.AudioQuickSettingsDialog
import hrshd1eux.flowgrab.ui.page.settings.format.VideoQuickSettingsDialog
import hrshd1eux.flowgrab.ui.page.settings.network.CookiesQuickSettingsDialog
import hrshd1eux.flowgrab.ui.theme.SealTheme
import hrshd1eux.flowgrab.util.AUDIO_CONVERSION_FORMAT
import hrshd1eux.flowgrab.util.AUDIO_CONVERT
import hrshd1eux.flowgrab.util.AUDIO_FORMAT
import hrshd1eux.flowgrab.util.AUDIO_QUALITY
import hrshd1eux.flowgrab.util.CONVERT_FLAC
import hrshd1eux.flowgrab.util.CONVERT_M4A
import hrshd1eux.flowgrab.util.CONVERT_MP3
import hrshd1eux.flowgrab.util.CONVERT_OPUS
import hrshd1eux.flowgrab.util.CONVERT_WAV
import hrshd1eux.flowgrab.util.COOKIES
import hrshd1eux.flowgrab.util.CUSTOM_COMMAND
import hrshd1eux.flowgrab.util.DatabaseUtil
import hrshd1eux.flowgrab.util.DownloadType
import hrshd1eux.flowgrab.util.InstantPreviewUtil
import hrshd1eux.flowgrab.util.HIGH
import hrshd1eux.flowgrab.util.RES_1080P
import hrshd1eux.flowgrab.util.RES_1440P
import hrshd1eux.flowgrab.util.RES_2160P
import hrshd1eux.flowgrab.util.RES_360P
import hrshd1eux.flowgrab.util.RES_480P
import hrshd1eux.flowgrab.util.RES_720P
import hrshd1eux.flowgrab.util.RES_HIGHEST
import hrshd1eux.flowgrab.util.DownloadType.Audio
import hrshd1eux.flowgrab.util.DownloadType.Command
import hrshd1eux.flowgrab.util.DownloadType.Playlist
import hrshd1eux.flowgrab.util.DownloadType.Video
import hrshd1eux.flowgrab.util.DownloadType.entries
import hrshd1eux.flowgrab.util.DownloadUtil
import hrshd1eux.flowgrab.util.FORMAT_SELECTION
import hrshd1eux.flowgrab.util.PreferenceStrings
import hrshd1eux.flowgrab.util.PreferenceUtil
import hrshd1eux.flowgrab.util.PreferenceUtil.getBoolean
import hrshd1eux.flowgrab.util.PreferenceUtil.updateBoolean
import hrshd1eux.flowgrab.util.PreferenceUtil.updateInt
import hrshd1eux.flowgrab.util.SUBTITLE
import hrshd1eux.flowgrab.util.TEMPLATE_ID
import hrshd1eux.flowgrab.util.THUMBNAIL
import hrshd1eux.flowgrab.util.ToastUtil
import hrshd1eux.flowgrab.util.USE_CUSTOM_AUDIO_PRESET
import hrshd1eux.flowgrab.util.VIDEO_FORMAT
import hrshd1eux.flowgrab.util.VIDEO_QUALITY
import hrshd1eux.flowgrab.util.VideoInfo
import hrshd1eux.flowgrab.util.toDurationText
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun DownloadType.label(): String =
    stringResource(
        when (this) {
            Audio -> R.string.audio
            Video -> R.string.video
            Command -> R.string.commands
            Playlist -> R.string.playlist
        }
    )

val PreferencesMock = DownloadUtil.DownloadPreferences.EMPTY

data class Config(
    val downloadType: DownloadType? = PreferenceUtil.getDownloadType(),
    val typeEntries: List<DownloadType> =
        when (CUSTOM_COMMAND.getBoolean()) {
            true -> DownloadType.entries
            false -> DownloadType.entries - Command
        },
    val useFormatSelection: Boolean = FORMAT_SELECTION.getBoolean(),
    val savedLinks: Set<String> = PreferenceUtil.getSavedLinks(),
) {
    companion object {
        fun updatePreferences(newValue: Config, oldValue: Config) {
            with(newValue) {
                if (downloadType != oldValue.downloadType) {
                    downloadType?.let { PreferenceUtil.updateDownloadType(it) }
                }
                if (useFormatSelection != oldValue.useFormatSelection) {
                    FORMAT_SELECTION.updateBoolean(useFormatSelection)
                }
                if (savedLinks != oldValue.savedLinks) {
                    PreferenceUtil.updateSavedLinks(savedLinks)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadDialog(
    modifier: Modifier = Modifier,
    config: Config,
    sheetState: SheetState,
    preferences: DownloadUtil.DownloadPreferences,
    onPreferencesUpdate: (DownloadUtil.DownloadPreferences) -> Unit,
    state: DownloadDialogViewModel.SheetState = InputUrl,
    onActionPost: (Action) -> Unit = {},
) {
    var showVideoPresetDialog by remember { mutableStateOf(false) }
    var showAudioPresetDialog by remember { mutableStateOf(false) }

    SealModalBottomSheet(
        sheetState = sheetState,
        contentPadding = PaddingValues(),
        onDismissRequest = { onActionPost(Action.HideSheet) },
    ) {
        DownloadDialogContent(
            modifier = modifier,
            state = state,
            config = config,
            preferences = preferences,
            onPreferencesUpdate = onPreferencesUpdate,
            onPresetEdit = { type ->
                when (type) {
                    Audio -> showAudioPresetDialog = true

                    Video -> showVideoPresetDialog = true

                    else -> {}
                }
            },
            onActionPost = onActionPost,
        )
    }

    if (showVideoPresetDialog) {
        var res by remember(preferences) { mutableIntStateOf(preferences.videoResolution) }
        var format by remember(preferences) { mutableIntStateOf(preferences.videoFormat) }

        VideoQuickSettingsDialog(
            videoResolution = res,
            videoFormatPreference = format,
            onResolutionSelect = { res = it },
            onFormatSelect = { format = it },
            onDismissRequest = { showVideoPresetDialog = false },
            onSave = {
                VIDEO_FORMAT.updateInt(format)
                VIDEO_QUALITY.updateInt(res)
                onPreferencesUpdate(DownloadUtil.DownloadPreferences.createFromPreferences())
            },
        )
    }

    if (showAudioPresetDialog) {
        var quality by remember(preferences) { mutableIntStateOf(preferences.audioQuality) }
        var customPreset by
            remember(preferences) { mutableStateOf(preferences.useCustomAudioPreset) }
        var conversionFmt by
            remember(preferences) { mutableIntStateOf(preferences.audioConvertFormat) }
        var convertAudio by remember(preferences) { mutableStateOf(preferences.convertAudio) }
        var preferredFormat by remember(preferences) { mutableIntStateOf(preferences.audioFormat) }

        AudioQuickSettingsDialog(
            modifier = Modifier,
            preferences = preferences,
            audioQuality = quality,
            onQualitySelect = { quality = it },
            useCustomAudioPreset = customPreset,
            onCustomPresetToggle = { customPreset = it },
            convertAudio = convertAudio,
            onConvertToggled = { convertAudio = it },
            conversionFormat = conversionFmt,
            onConversionSelect = { conversionFmt = it },
            preferredFormat = preferredFormat,
            onPreferredSelect = { preferredFormat = it },
            onDismissRequest = { showAudioPresetDialog = false },
            onSave = {
                AUDIO_QUALITY.updateInt(quality)
                USE_CUSTOM_AUDIO_PRESET.updateBoolean(customPreset)
                AUDIO_CONVERSION_FORMAT.updateInt(conversionFmt)
                AUDIO_CONVERT.updateBoolean(convertAudio)
                AUDIO_FORMAT.updateInt(preferredFormat)
                onPreferencesUpdate(DownloadUtil.DownloadPreferences.createFromPreferences())
            },
        )
    }
}

@Composable
private fun ErrorPage(modifier: Modifier = Modifier, state: Error, onActionPost: (Action) -> Unit) {
    val view = LocalView.current
    val clipboardManager = LocalClipboardManager.current
    val url =
        state.action.run {
            when (this) {
                is Action.FetchFormats -> url
                is Action.FetchPlaylist -> url
                else -> {
                    throw IllegalArgumentException()
                }
            }
        }
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = stringResource(R.string.fetch_info_error_msg),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = state.throwable.message.toString(),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier.padding(vertical = 16.dp, horizontal = 20.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            maxLines = 20,
            overflow = TextOverflow.Clip,
        )

        Row(modifier = Modifier) {
            FilledTonalButton(onClick = { onActionPost(state.action) }) { Text("Retry") }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    view.longPressHapticFeedback()
                    clipboardManager.setText(
                        AnnotatedString(
                            App.getVersionReport() + "\nURL: ${url}\n${state.throwable.message}"
                        )
                    )
                    ToastUtil.makeToast(R.string.error_copied)
                }
            ) {
                Text(stringResource(R.string.copy_error_report))
            }
        }
    }
}

@Composable
private fun DownloadDialogContent(
    modifier: Modifier = Modifier,
    state: DownloadDialogViewModel.SheetState,
    config: Config,
    preferences: DownloadUtil.DownloadPreferences,
    onPreferencesUpdate: (DownloadUtil.DownloadPreferences) -> Unit,
    onPresetEdit: (DownloadType?) -> Unit,
    onActionPost: (Action) -> Unit,
) {
    AnimatedContent(
        modifier = modifier,
        targetState = state,
        label = "",
        transitionSpec = {
            materialSharedAxisX(initialOffsetX = { it / 4 }, targetOffsetX = { -it / 4 })
        },
    ) { state ->
        when (state) {
            is Configure -> {
                check(state.urlList.isNotEmpty())
                ConfigurePage(
                    urlList = state.urlList,
                    config = config,
                    preferences = preferences,
                    onPresetEdit = onPresetEdit,
                    onConfigSave = {
                        Config.updatePreferences(newValue = it, oldValue = config)
                    },
                    settingChips = {
                        AdditionalSettings(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            isQuickDownload = false,
                            preference = preferences,
                            selectedType = config.downloadType,
                            onPreferenceUpdate = {
                                onPreferencesUpdate(
                                    DownloadUtil.DownloadPreferences.createFromPreferences()
                                )
                            },
                        )
                    },
                    onActionPost = { onActionPost(it) },
                )
            }

            is Error -> {
                ErrorPage(state = state, onActionPost = onActionPost)
            }

            is Loading -> {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 120.dp)) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }

            InputUrl -> {
                InputUrlPage(
                    config = config,
                    onConfigUpdate = { Config.updatePreferences(newValue = it, oldValue = config) },
                    onActionPost = onActionPost,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun ErrorPreview() {
    SealModalBottomSheet(
        onDismissRequest = {},
        sheetState =
            with(LocalDensity.current) {
                SheetState(
                    initialValue = SheetValue.Expanded,
                    skipPartiallyExpanded = true,
                    velocityThreshold = { 56.dp.toPx() },
                    positionalThreshold = { 125.dp.toPx() },
                )
            },
    ) {
        ErrorPage(
            state =
                Error(
                    action =
                        Action.FetchFormats(
                            url = "",
                            audioOnly = true,
                            preferences = PreferencesMock,
                        ),
                    throwable = Exception("Not good"),
                ),
            onActionPost = {},
        )
    }
}

@Composable
fun FormatPage(
    modifier: Modifier = Modifier,
    state: SelectionState.FormatSelection,
    onDismissRequest: () -> Unit,
) {
    val sheetState =
        androidx.compose.material.rememberModalBottomSheetState(
            initialValue = ModalBottomSheetValue.Hidden,
            skipHalfExpanded = true,
        )

    LaunchedEffect(state) { sheetState.show() }
    val scope = rememberCoroutineScope()
    BackHandler { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismissRequest() } }

    SealModalBottomSheetM2Variant(sheetState = sheetState, sheetGesturesEnabled = false) {
        FormatPage(
            modifier = modifier,
            videoInfo = state.info,
            onNavigateBack = {
                scope.launch { sheetState.hide() }.invokeOnCompletion { onDismissRequest() }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
/*@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Light", uiMode = Configuration.UI_MODE_NIGHT_NO)*/
@Composable
private fun ConfigurePagePreview() {
    SealTheme() {
        SealModalBottomSheet(
            sheetState =
                with(LocalDensity.current) {
                    SheetState(
                        initialValue = SheetValue.Expanded,
                        skipPartiallyExpanded = true,
                        velocityThreshold = { 56.dp.toPx() },
                        positionalThreshold = { 125.dp.toPx() },
                    )
                },
            onDismissRequest = {},
            contentPadding = PaddingValues(),
        ) {
            ConfigurePage(
                config =
                    Config(
                        downloadType = Audio,
                        useFormatSelection = true,
                        typeEntries = entries - Command,
                    ),
                preferences = PreferencesMock,
                onConfigSave = {},
                settingChips = {},
            ) {}
        }
    }
}

private fun formatBytesToText(bytes: Double): String {
    if (bytes <= 0.0) return ""
    val gigaBytes = 1024.0 * 1024.0 * 1024.0
    val megaBytes = 1024.0 * 1024.0
    val kiloBytes = 1024.0
    return if (bytes >= gigaBytes) {
        "%.1f GB".format(Locale.US, bytes / gigaBytes)
    } else if (bytes >= megaBytes) {
        "%.1f MB".format(Locale.US, bytes / megaBytes)
    } else {
        "%.0f KB".format(Locale.US, bytes / kiloBytes)
    }
}

private fun calculateAudioSize(
    videoInfo: VideoInfo?,
    formatType: Int,
    quality: Int,
    fallbackText: String = "",
): String {
    if (videoInfo == null || (videoInfo.duration ?: 0.0) <= 0.0) {
        return when (formatType) {
            CONVERT_OPUS -> "~2.4 MB"
            CONVERT_MP3 -> if (quality == HIGH) "~7.2 MB" else "~3.8 MB"
            CONVERT_M4A -> "~3.5 MB"
            CONVERT_FLAC -> "~12.5 MB"
            CONVERT_WAV -> "~22.0 MB"
            else -> "~3.8 MB"
        }
    }
    val duration = videoInfo.duration!!

    val formats = videoInfo.formats ?: emptyList()
    val audioFormats = formats.filter { it.isAudioOnly() }

    return when (formatType) {
        CONVERT_OPUS -> {
            val opusFormat =
                audioFormats
                    .filter {
                        it.acodec?.contains("opus", ignoreCase = true) == true || it.ext == "webm"
                    }
                    .maxByOrNull { it.abr ?: it.tbr ?: 0.0 }
            val sizeBytes =
                opusFormat?.fileSize
                    ?: opusFormat?.fileSizeApprox
                    ?: (((opusFormat?.abr ?: 160.0) * 1000.0 / 8.0) * duration)
            formatBytesToText(sizeBytes)
        }
        CONVERT_MP3 -> {
            if (quality == HIGH) {
                val sizeBytes = 40_000.0 * duration
                formatBytesToText(sizeBytes)
            } else {
                val bestAudio = audioFormats.maxByOrNull { it.abr ?: it.tbr ?: 0.0 }
                val sizeBytes =
                    bestAudio?.fileSize
                        ?: bestAudio?.fileSizeApprox
                        ?: (((bestAudio?.abr ?: bestAudio?.tbr ?: 256.0) * 1000.0 / 8.0) * duration)
                formatBytesToText(sizeBytes)
            }
        }
        CONVERT_M4A -> {
            val m4aFormat =
                audioFormats
                    .filter {
                        it.acodec?.contains("mp4a", ignoreCase = true) == true || it.ext == "m4a"
                    }
                    .maxByOrNull { it.abr ?: it.tbr ?: 0.0 }
            val sizeBytes =
                m4aFormat?.fileSize
                    ?: m4aFormat?.fileSizeApprox
                    ?: (((m4aFormat?.abr ?: 256.0) * 1000.0 / 8.0) * duration)
            formatBytesToText(sizeBytes)
        }
        CONVERT_FLAC -> {
            val sizeBytes = 100_000.0 * duration
            formatBytesToText(sizeBytes)
        }
        CONVERT_WAV -> {
            val sizeBytes = 176_400.0 * duration + 44.0
            formatBytesToText(sizeBytes)
        }
        else -> fallbackText
    }
}

private fun calculateVideoSize(
    videoInfo: VideoInfo?,
    resCode: Int,
    fallbackText: String = "",
): String {
    if (videoInfo == null || (videoInfo.duration ?: 0.0) <= 0.0) {
        return when (resCode) {
            RES_2160P -> "~310 MB"
            RES_1440P -> "~150 MB"
            RES_1080P -> "~62 MB"
            RES_720P -> "~30 MB"
            RES_480P -> "~16 MB"
            RES_360P -> "~9 MB"
            else -> "~62 MB"
        }
    }
    val duration = videoInfo.duration!!

    val formats = videoInfo.formats ?: emptyList()
    val audioFormats = formats.filter { it.isAudioOnly() }
    val bestAudioSize =
        audioFormats.maxByOrNull { it.abr ?: it.tbr ?: 0.0 }?.let {
            it.fileSize ?: it.fileSizeApprox ?: (((it.abr ?: 128.0) * 1000.0 / 8.0) * duration)
        } ?: (16_000.0 * duration)

    val targetHeight =
        when (resCode) {
            RES_2160P -> 2160
            RES_1440P -> 1440
            RES_1080P -> 1080
            RES_720P -> 720
            RES_480P -> 480
            RES_360P -> 360
            else -> 0
        }

    val videoFormat =
        if (targetHeight > 0) {
            formats
                .filter { it.containsVideo() && it.height?.toInt() == targetHeight }
                .maxByOrNull { it.fileSize ?: it.fileSizeApprox ?: (it.tbr ?: 0.0) }
        } else {
            formats.filter { it.containsVideo() }.maxByOrNull { it.height ?: 0.0 }
        }

    return if (videoFormat != null) {
        val videoBytes =
            videoFormat.fileSize
                ?: videoFormat.fileSizeApprox
                ?: (((videoFormat.vbr ?: videoFormat.tbr ?: 0.0) * 1000.0 / 8.0) * duration)
        val totalBytes =
            if (videoFormat.containsAudio()) videoBytes else videoBytes + bestAudioSize
        formatBytesToText(totalBytes)
    } else {
        val estimatedBitrateKbps =
            when (resCode) {
                RES_2160P -> 20_000.0
                RES_1440P -> 10_000.0
                RES_1080P -> 4_000.0
                RES_720P -> 2_000.0
                RES_480P -> 1_000.0
                RES_360P -> 600.0
                else -> 4_000.0
            }
        val estimatedBytes = (estimatedBitrateKbps * 1000.0 / 8.0) * duration + bestAudioSize
        formatBytesToText(estimatedBytes)
    }
}

private data class AudioFormatOption(
    val title: String,
    val subtitle: String,
    val sizeText: String,
    val tag: String,
    val convertFormat: Int,
    val audioQuality: Int = 0,
)

private data class VideoQualityOption(
    val title: String,
    val subtitle: String,
    val sizeText: String,
    val resCode: Int,
)

@Composable
private fun AudioFormatCard(
    title: String,
    subtitle: String,
    sizeText: String,
    tag: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier =
            Modifier.fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color =
            if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = if (selected) 2.dp else 0.dp,
        border =
            androidx.compose.foundation.BorderStroke(
                width = if (selected) 2.dp else 1.dp,
                color =
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onClick, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color =
                        if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color =
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = tag.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color =
                            if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = sizeText,
                    style = MaterialTheme.typography.labelMedium,
                    color =
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun VideoQualityCard(
    title: String,
    subtitle: String,
    sizeText: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier =
            Modifier.fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color =
            if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = if (selected) 2.dp else 0.dp,
        border =
            androidx.compose.foundation.BorderStroke(
                width = if (selected) 2.dp else 1.dp,
                color =
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onClick, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color =
                        if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = sizeText,
                style = MaterialTheme.typography.labelMedium,
                color =
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConfigurePage(
    modifier: Modifier = Modifier,
    urlList: List<String> = emptyList(),
    config: Config,
    preferences: DownloadUtil.DownloadPreferences,
    settingChips: @Composable () -> Unit,
    onPresetEdit: (DownloadType?) -> Unit = {},
    onConfigSave: (Config) -> Unit,
    onActionPost: (Action) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var selectedType by remember(config) { mutableStateOf(config.downloadType ?: Audio) }
    var useFormatSelection by remember(config) { mutableStateOf(false) }
    val canProceed = selectedType in config.typeEntries || selectedType == Audio || selectedType == Video

    var showTemplateSelectionDialog by remember { mutableStateOf(false) }
    var showTemplateCreatorDialog by remember { mutableStateOf(false) }
    var showTemplateEditorDialog by remember { mutableStateOf(false) }
    val template by
        remember(showTemplateCreatorDialog, showTemplateSelectionDialog, showTemplateEditorDialog) {
            mutableStateOf(PreferenceUtil.getTemplate())
        }

    val firstUrl = urlList.firstOrNull()
    var fetchedVideoInfo by remember(firstUrl) {
        mutableStateOf(firstUrl?.let { DownloadUtil.getCachedVideoInfo(it) })
    }
    var instantPreview by remember(firstUrl) {
        mutableStateOf(firstUrl?.let { InstantPreviewUtil.getCached(it) })
    }
    var isFetchingInfo by remember(firstUrl) {
        mutableStateOf(fetchedVideoInfo == null && !firstUrl.isNullOrBlank())
    }

    LaunchedEffect(firstUrl) {
        if (!firstUrl.isNullOrBlank()) {
            if (instantPreview == null && fetchedVideoInfo == null) {
                launch {
                    val preview = InstantPreviewUtil.fetchInstantPreview(firstUrl)
                    if (preview != null && fetchedVideoInfo == null) {
                        instantPreview = preview
                    }
                }
            }
            if (fetchedVideoInfo == null) {
                isFetchingInfo = true
                withContext(Dispatchers.IO) {
                    DownloadUtil.fetchVideoInfoFromUrl(
                        url = firstUrl,
                        preferences = preferences,
                        taskKey = "ConfigureSize_${firstUrl.hashCode()}",
                    ).onSuccess { info ->
                        withContext(Dispatchers.Main) {
                            fetchedVideoInfo = info
                        }
                    }
                }
                isFetchingInfo = false
            }
        }
    }

    val displayInfo = fetchedVideoInfo ?: instantPreview?.toVideoInfo()

    val loadingSizeText = if (isFetchingInfo) "Calculating..." else ""

    val audioFormatOptions = remember(fetchedVideoInfo, isFetchingInfo) {
        listOf(
            AudioFormatOption("Best Audio (Auto Best Quality)", "Best", calculateAudioSize(fetchedVideoInfo, CONVERT_MP3, 0, loadingSizeText), "mp3", CONVERT_MP3, 0),
            AudioFormatOption("OPUS (Best Quality & Efficiency)", "Best Quality & Efficiency", calculateAudioSize(fetchedVideoInfo, CONVERT_OPUS, 0, loadingSizeText), "opus", CONVERT_OPUS),
            AudioFormatOption("MP3 320kbps (Universal Device Compatible)", "Universal Device Compatible", calculateAudioSize(fetchedVideoInfo, CONVERT_MP3, HIGH, loadingSizeText), "mp3", CONVERT_MP3, HIGH),
            AudioFormatOption("M4A / AAC (Apple & High Fidelity)", "Apple & High Fidelity", calculateAudioSize(fetchedVideoInfo, CONVERT_M4A, 0, loadingSizeText), "m4a", CONVERT_M4A),
            AudioFormatOption("FLAC (Lossless Studio Master)", "Lossless Studio Master", calculateAudioSize(fetchedVideoInfo, CONVERT_FLAC, 0, loadingSizeText), "flac", CONVERT_FLAC),
            AudioFormatOption("WAV (Uncompressed Studio PCM)", "Uncompressed Studio PCM", calculateAudioSize(fetchedVideoInfo, CONVERT_WAV, 0, loadingSizeText), "wav", CONVERT_WAV),
        )
    }
    var selectedAudioIndex by rememberSaveable { mutableIntStateOf(0) }

    val videoContainers = remember { listOf("mp4" to "MP4", "mkv" to "MKV", "webm" to "WEBM") }
    var selectedVideoContainer by rememberSaveable { mutableStateOf("mp4") }

    val videoQualityOptions = remember(fetchedVideoInfo, isFetchingInfo) {
        listOf(
            VideoQualityOption("Auto Best Quality", "Highest resolution available", calculateVideoSize(fetchedVideoInfo, RES_HIGHEST, loadingSizeText), RES_HIGHEST),
            VideoQualityOption("2160p (4K UHD)", "Ultra High Definition", calculateVideoSize(fetchedVideoInfo, RES_2160P, loadingSizeText), RES_2160P),
            VideoQualityOption("1440p (2K QHD)", "Quad High Definition", calculateVideoSize(fetchedVideoInfo, RES_1440P, loadingSizeText), RES_1440P),
            VideoQualityOption("1080p (FHD)", "Full High Definition", calculateVideoSize(fetchedVideoInfo, RES_1080P, loadingSizeText), RES_1080P),
            VideoQualityOption("720p (HD)", "High Definition", calculateVideoSize(fetchedVideoInfo, RES_720P, loadingSizeText), RES_720P),
            VideoQualityOption("480p (SD)", "Standard Definition", calculateVideoSize(fetchedVideoInfo, RES_480P, loadingSizeText), RES_480P),
            VideoQualityOption("360p (Low)", "Data Saver", calculateVideoSize(fetchedVideoInfo, RES_360P, loadingSizeText), RES_360P),
        )
    }
    var selectedVideoQualityIndex by rememberSaveable { mutableIntStateOf(0) }

    var embedThumbnail by rememberSaveable { mutableStateOf(true) }
    var embedMetadata by rememberSaveable { mutableStateOf(true) }
    var downloadSubtitles by rememberSaveable { mutableStateOf(preferences.downloadSubtitle) }

    var isCustomRename by rememberSaveable { mutableStateOf(false) }
    var customName by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(selectedType) {
        if (selectedType == Playlist) {
            useFormatSelection = false
        }
    }

    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Header(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                title = stringResource(R.string.settings_before_download),
                icon = Icons.Outlined.DoneAll,
            )

            if (urlList.size > 1) {
                Surface(
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = "${urlList.size} URLs ready for batch download",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }

            if (displayInfo != null) {
                val info = displayInfo
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!info.thumbnail.isNullOrBlank()) {
                            AsyncImageImpl(
                                model = info.thumbnail,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(width = 80.dp, height = 48.dp)
                                    .clip(MaterialTheme.shapes.small),
                                contentScale = ContentScale.Crop,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = info.title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val meta = buildList {
                                if (!info.uploader.isNullOrBlank()) add(info.uploader)
                                info.duration?.roundToInt()?.toDurationText()?.takeIf { it.isNotBlank() }?.let { add(it) }
                            }.joinToString(" • ")
                            if (meta.isNotBlank()) {
                                Text(
                                    text = meta,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (isFetchingInfo) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(10.dp),
                                        strokeWidth = 1.5.dp,
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Resolving stream formats & sizes...",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (isFetchingInfo) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Calculating real media sizes...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            DrawerSheetSubtitle(text = stringResource(id = R.string.download_type))
            DownloadTypeSelectionGroup(
                typeEntries = config.typeEntries,
                selectedType = selectedType,
                onSelect = { selectedType = it },
            )

            when (selectedType) {
                Audio -> {
                    DrawerSheetSubtitle(text = "Audio Format & Quality")
                    audioFormatOptions.forEachIndexed { index, option ->
                        AudioFormatCard(
                            title = option.title,
                            subtitle = option.subtitle,
                            sizeText = option.sizeText,
                            tag = option.tag,
                            selected = selectedAudioIndex == index,
                            onClick = { selectedAudioIndex = index },
                        )
                    }
                }

                Video -> {
                    DrawerSheetSubtitle(text = "Video Container Format")
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        videoContainers.forEach { (container, label) ->
                            val isSelected = selectedVideoContainer == container
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedVideoContainer = container },
                                label = { Text(label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    DrawerSheetSubtitle(text = "Video Resolution & Quality")
                    videoQualityOptions.forEachIndexed { index, option ->
                        VideoQualityCard(
                            title = option.title,
                            subtitle = option.subtitle,
                            sizeText = option.sizeText,
                            selected = selectedVideoQualityIndex == index,
                            onClick = { selectedVideoQualityIndex = index },
                        )
                    }
                }

                Command -> {
                    if (showTemplateSelectionDialog) {
                        TemplatePickerDialog { showTemplateSelectionDialog = false }
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
                    DrawerSheetSubtitle(
                        text = stringResource(id = R.string.template_selection),
                        modifier = Modifier,
                    )
                    LazyRow(modifier = Modifier) {
                        item {
                            ButtonChip(
                                icon = Icons.Outlined.Code,
                                label = template.name,
                                onClick = { showTemplateSelectionDialog = true },
                            )
                        }
                        item {
                            ButtonChip(
                                icon = Icons.Outlined.NewLabel,
                                label = stringResource(id = R.string.new_template),
                                onClick = { showTemplateCreatorDialog = true },
                            )
                        }
                        item {
                            ButtonChip(
                                icon = Icons.Outlined.Edit,
                                label = stringResource(id = R.string.edit_template, template.name),
                                onClick = { showTemplateEditorDialog = true },
                            )
                        }
                    }
                }

                Playlist -> {
                    DrawerSheetSubtitle(text = "Playlist Options")
                    Text(
                        text = "All items in the playlist will be fetched and downloaded.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> {}
            }

            if (selectedType != Command) {
                DrawerSheetSubtitle(text = "Download Options")
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border =
                        androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        ),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .clickable { embedThumbnail = !embedThumbnail }
                                    .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = embedThumbnail,
                                onCheckedChange = { embedThumbnail = it },
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Embed Thumbnail Artwork",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        HorizontalDivider(
                            thickness = Dp.Hairline,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .clickable { embedMetadata = !embedMetadata }
                                    .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = embedMetadata,
                                onCheckedChange = { embedMetadata = it },
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Embed Metadata (Tags, Artist, Album)",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        HorizontalDivider(
                            thickness = Dp.Hairline,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .clickable { downloadSubtitles = !downloadSubtitles }
                                    .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = downloadSubtitles,
                                onCheckedChange = { downloadSubtitles = it },
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Download Subtitles & Auto-Captions",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }

                DrawerSheetSubtitle(text = "File Name")
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border =
                        androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        ),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .clickable { isCustomRename = !isCustomRename }
                                    .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = isCustomRename,
                                onCheckedChange = { isCustomRename = it },
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Rename File After Downloading",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text =
                                        if (isCustomRename) "Enter custom name below"
                                        else "Using original name from source",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (isCustomRename) {
                            OutlinedTextField(
                                value = customName,
                                onValueChange = { customName = it },
                                label = { Text("Custom File / Song Name") },
                                placeholder = { Text("Leave blank for original name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
                            )
                        }
                    }
                }
            }
        }

        ActionButtons(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            canProceed = canProceed,
            selectedType = selectedType,
            useFormatSelection = useFormatSelection,
            onCancel = { onActionPost(Action.HideSheet) },
            onDownload = {
                val selectedAudio = audioFormatOptions[selectedAudioIndex]
                val selectedVideo = videoQualityOptions[selectedVideoQualityIndex]

                val finalPreferences =
                    preferences.copy(
                        extractAudio = selectedType == Audio,
                        convertAudio = (selectedType == Audio),
                        audioConvertFormat =
                            if (selectedType == Audio) selectedAudio.convertFormat
                            else preferences.audioConvertFormat,
                        audioQuality =
                            if (selectedType == Audio) selectedAudio.audioQuality
                            else preferences.audioQuality,
                        videoResolution =
                            if (selectedType == Video) selectedVideo.resCode
                            else preferences.videoResolution,
                        videoContainerFormat = if (selectedType == Video) selectedVideoContainer else "",
                        createThumbnail = embedThumbnail,
                        embedThumbnail = embedThumbnail,
                        embedMetadata = embedMetadata,
                        downloadSubtitle = downloadSubtitles,
                        newTitle = if (isCustomRename && customName.isNotBlank()) customName.trim() else "",
                    )

                onConfigSave(
                    config.copy(
                        useFormatSelection = false,
                        downloadType = selectedType,
                    )
                )

                onActionPost(
                    Action.DownloadWithPreset(
                        urlList = urlList,
                        preferences = finalPreferences,
                    )
                )
            },
            onFetchInfo = {
                val selectedAudio = audioFormatOptions[selectedAudioIndex]
                val selectedVideo = videoQualityOptions[selectedVideoQualityIndex]

                val finalPreferences =
                    preferences.copy(
                        extractAudio = selectedType == Audio,
                        convertAudio = (selectedType == Audio),
                        audioConvertFormat =
                            if (selectedType == Audio) selectedAudio.convertFormat
                            else preferences.audioConvertFormat,
                        audioQuality =
                            if (selectedType == Audio) selectedAudio.audioQuality
                            else preferences.audioQuality,
                        videoResolution =
                            if (selectedType == Video) selectedVideo.resCode
                            else preferences.videoResolution,
                        videoContainerFormat = if (selectedType == Video) selectedVideoContainer else "",
                        createThumbnail = embedThumbnail,
                        embedThumbnail = embedThumbnail,
                        embedMetadata = embedMetadata,
                        downloadSubtitle = downloadSubtitles,
                        newTitle = if (isCustomRename && customName.isNotBlank()) customName.trim() else "",
                    )

                onConfigSave(
                    config.copy(
                        useFormatSelection = useFormatSelection,
                        downloadType = selectedType,
                    )
                )
                if (selectedType == Playlist) {
                    onActionPost(
                        Action.FetchPlaylist(
                            url = urlList.firstOrNull().orEmpty(),
                            preferences = finalPreferences,
                        )
                    )
                } else {
                    onActionPost(
                        Action.FetchFormats(
                            url = urlList.firstOrNull().orEmpty(),
                            audioOnly = selectedType == Audio,
                            preferences = finalPreferences,
                        )
                    )
                }
            },
            onTaskStart = {
                onConfigSave(
                    config.copy(
                        useFormatSelection = useFormatSelection,
                        downloadType = selectedType,
                    )
                )
                onActionPost(
                    Action.RunCommand(
                        url = urlList.firstOrNull().orEmpty(),
                        template = template,
                        preferences = preferences,
                    )
                )
            },
        )
    }
}

@Composable
fun ConfigurePagePlaylistVariant(
    modifier: Modifier = Modifier,
    initialDownloadType: DownloadType,
    preferences: DownloadUtil.DownloadPreferences,
    onPreferencesUpdate: (DownloadUtil.DownloadPreferences) -> Unit,
    onPresetEdit: (DownloadType?) -> Unit = {},
    onDismissRequest: () -> Unit,
    onDownload: (DownloadType) -> Unit,
) {

    var selectedType by remember(initialDownloadType) { mutableStateOf(initialDownloadType) }

    Column {
        Column(modifier = modifier.padding(horizontal = 20.dp)) {
            Header(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                title = stringResource(R.string.settings_before_download),
                icon = Icons.Outlined.DoneAll,
            )
            DrawerSheetSubtitle(text = stringResource(id = R.string.download_type))
            DownloadTypeSelectionGroup(
                typeEntries = listOf(Video, Audio),
                selectedType = selectedType,
                onSelect = { selectedType = it },
            )
            DrawerSheetSubtitle(
                text = stringResource(id = R.string.format_selection),
                modifier = Modifier,
            )
            Preset(
                modifier = Modifier,
                preference = preferences,
                selected = true,
                downloadType = selectedType,
                onClick = { onPresetEdit(selectedType) },
                showEditIcon = true,
                onEdit = { onPresetEdit(selectedType) },
            )
        }
        var expanded by remember { mutableStateOf(false) }
        ExpandableTitle(expanded = expanded, onClick = { expanded = true }) {
            AdditionalSettings(
                modifier = Modifier.padding(horizontal = 16.dp),
                isQuickDownload = false,
                preference = preferences,
                selectedType = Audio,
                onPreferenceUpdate = {
                    onPreferencesUpdate(DownloadUtil.DownloadPreferences.createFromPreferences())
                },
            )
        }

        ActionButtons(
            modifier = Modifier.padding(horizontal = 20.dp),
            canProceed = true,
            selectedType = selectedType,
            useFormatSelection = false,
            onCancel = onDismissRequest,
            onDownload = {
                onDownload(initialDownloadType)
                onDismissRequest()
            },
            onFetchInfo = { throw IllegalStateException() },
            onTaskStart = { throw IllegalStateException() },
        )
    }
}

@Composable
private fun AdditionalSettings(
    modifier: Modifier = Modifier,
    isQuickDownload: Boolean,
    selectedType: DownloadType?,
    preference: DownloadUtil.DownloadPreferences,
    onNavigateToCookieGeneratorPage: (String) -> Unit = {},
    onPreferenceUpdate: () -> Unit,
) {
    val cookiesProfiles by DatabaseUtil.getCookiesFlow().collectAsStateWithLifecycle(emptyList())
    var showCookiesDialog by rememberSaveable { mutableStateOf(false) }

    with(preference) {
        Row(modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            if (cookiesProfiles.isNotEmpty()) {
                VideoFilterChip(
                    selected = preference.cookies,
                    onClick = {
                        if (isQuickDownload) {
                            COOKIES.updateBoolean(!cookies)
                            onPreferenceUpdate()
                        } else {
                            showCookiesDialog = true
                        }
                    },
                    label = stringResource(id = R.string.cookies),
                )
            }

            VideoFilterChip(
                selected = downloadSubtitle,
                enabled = selectedType != Command,
                onClick = {
                    SUBTITLE.updateBoolean(!downloadSubtitle)
                    onPreferenceUpdate()
                },
                label = stringResource(id = R.string.download_subtitles),
            )
            VideoFilterChip(
                selected = createThumbnail,
                enabled = selectedType != Command,
                onClick = {
                    THUMBNAIL.updateBoolean(!createThumbnail)
                    onPreferenceUpdate()
                },
                label = stringResource(R.string.create_thumbnail),
            )
        }

        if (showCookiesDialog && cookiesProfiles.isNotEmpty()) {
            CookiesQuickSettingsDialog(
                onDismissRequest = { showCookiesDialog = false },
                onConfirm = {},
                cookieProfiles = cookiesProfiles,
                onCookieProfileClicked = { onNavigateToCookieGeneratorPage(it.url) },
                isCookiesEnabled = cookies,
                onCookiesToggled = {
                    COOKIES.updateBoolean(!cookies)
                    onPreferenceUpdate()
                },
            )
        }
    }
}

@Composable
fun ExpandableTitle(
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    onClick: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Column {
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(thickness = Dp.Hairline, modifier = Modifier.padding(horizontal = 20.dp))
        Column(
            modifier =
                modifier
                    .clickable(
                        onClick = onClick,
                        onClickLabel = stringResource(R.string.show_more_actions),
                        enabled = !expanded,
                    )
                    .padding(top = 12.dp, bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(modifier = Modifier.width(24.dp))
                Text(
                    text = stringResource(R.string.additional_settings),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(modifier = Modifier.weight(1f))
                if (!expanded) {
                    Icon(
                        imageVector = Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(32.dp))
                }
            }
            AnimatedVisibility(expanded) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    content()
                }
            }
        }
    }
}

@Composable
private fun SingleChoiceItem(
    modifier: Modifier = Modifier,
    title: String,
    desc: String,
    selected: Boolean,
    icon: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
) {
    val corner by
        animateDpAsState(
            if (selected) 28.dp else 16.dp,
            animationSpec =
                spring(
                    stiffness = Spring.StiffnessMedium,
                    visibilityThreshold = Dp.VisibilityThreshold,
                ),
            label = "",
        )
    val color by
        animateColorAsState(
            if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
            label = "",
        )

    Surface(
        selected = selected,
        onClick = onClick,
        color = color,
        shape = RoundedCornerShape(corner),
        modifier = modifier.padding(vertical = 4.dp).run { if (!enabled) alpha(0.32f) else this },
        enabled = enabled,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    icon?.invoke()
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 32.dp),
                )
            }
            action?.invoke()
        }
    }
}

@Composable
internal fun Header(modifier: Modifier = Modifier, icon: ImageVector, title: String) {
    Column(modifier = modifier) {
        Icon(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            imageVector = icon,
            contentDescription = null,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            modifier =
                Modifier.align(Alignment.CenterHorizontally).padding(top = 16.dp, bottom = 8.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DownloadTypeSelectionGroup(
    modifier: Modifier = Modifier,
    typeEntries: List<DownloadType>,
    selectedType: DownloadType?,
    onSelect: (DownloadType) -> Unit,
) {
    val typeCount = typeEntries.size
    if (typeCount == DownloadType.entries.size) {
        LazyRow(modifier = modifier) {
            items(typeEntries) { type ->
                SingleChoiceChip(
                    selected = selectedType == type,
                    label = type.label(),
                    onClick = { onSelect(type) },
                )
            }
        }
    } else {
        SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
            typeEntries.forEachIndexed { index, type ->
                SingleChoiceSegmentedButton(
                    selected = selectedType == type,
                    onClick = { onSelect(type) },
                    shape = SegmentedButtonDefaults.itemShape(index, typeCount),
                ) {
                    Text(text = type.label())
                }
            }
        }
    }
}

@Composable
private fun Preset(
    modifier: Modifier = Modifier,
    preference: DownloadUtil.DownloadPreferences,
    downloadType: DownloadType?,
    selected: Boolean,
    showEditIcon: Boolean,
    onEdit: () -> Unit,
    onClick: () -> Unit,
) {
    val description =
        when (downloadType) {
            Audio -> {
                PreferenceStrings.getAudioPresetText(preference)
            }

            Video -> {
                PreferenceStrings.getVideoPresetText(preference)
            }

            Playlist -> stringResource(R.string.preset_format_selection_desc)
            else -> ""
        }

    SingleChoiceItem(
        modifier = modifier,
        title = stringResource(R.string.preset),
        desc = description,
        icon = {
            Crossfade(selected, animationSpec = spring(stiffness = Spring.StiffnessMedium)) {
                if (it) {
                    Icon(
                        imageVector = Icons.Filled.SettingsSuggest,
                        null,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.SettingsSuggest,
                        null,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
        selected = selected,
        action = {
            Crossfade(showEditIcon, animationSpec = spring(stiffness = Spring.StiffnessMedium)) {
                if (it) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = stringResource(R.string.edit),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
        onClick = {
            if (showEditIcon) {
                onEdit()
            } else {
                onClick()
            }
        },
    )
}

@Composable
private fun Custom(
    modifier: Modifier = Modifier,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    SingleChoiceItem(
        modifier = modifier,
        title = stringResource(R.string.custom),
        desc = stringResource(R.string.custom_format_selection_desc),
        icon = {
            Crossfade(selected, animationSpec = spring(stiffness = Spring.StiffnessMedium)) {
                if (it) {
                    Icon(
                        imageVector = Icons.Filled.VideoFile,
                        null,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.VideoFile,
                        null,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
        selected = selected,
        enabled = enabled,
        onClick = onClick,
    )
}

private enum class ActionButton {
    FetchInfo,
    Download,
    StartTask,
}

@Composable
private fun ActionButton.Icon() {
    Icon(
        imageVector =
            when (this) {
                FetchInfo -> Icons.AutoMirrored.Filled.ArrowForward
                Download -> Icons.Outlined.FileDownload
                StartTask -> Icons.Filled.DownloadDone
            },
        contentDescription = null,
        modifier = Modifier.size(18.dp),
    )
}

@Composable
private fun ActionButton.Label() {
    Text(
        stringResource(
            when (this) {
                FetchInfo -> R.string.proceed
                Download -> R.string.download
                StartTask -> R.string.start
            }
        ),
        modifier = Modifier.padding(start = 8.dp),
    )
}

@Composable
private fun ActionButtons(
    modifier: Modifier = Modifier,
    canProceed: Boolean,
    selectedType: DownloadType?,
    useFormatSelection: Boolean,
    onCancel: () -> Unit,
    onFetchInfo: () -> Unit,
    onDownload: () -> Unit,
    onTaskStart: () -> Unit,
) {
    val action =
        if (selectedType == Command) {
            StartTask
        } else if (selectedType == Playlist || useFormatSelection) {
            FetchInfo
        } else {
            Download
        }

    val state = rememberLazyListState()
    LazyRow(
        modifier = modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.End,
        state = state,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item {
            OutlinedButtonWithIcon(
                modifier = Modifier.padding(horizontal = 12.dp),
                onClick = onCancel,
                icon = Icons.Outlined.Cancel,
                text = stringResource(R.string.cancel),
            )
        }
        item {
            Button(
                modifier = Modifier,
                onClick = {
                    when (action) {
                        FetchInfo -> onFetchInfo()
                        Download -> onDownload()
                        StartTask -> onTaskStart()
                    }
                },
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                enabled = canProceed,
            ) {
                AnimatedContent(
                    targetState = action,
                    label = "",
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(220, delayMillis = 90))).togetherWith(
                            fadeOut(animationSpec = tween(90))
                        )
                    },
                ) { action ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        action.Icon()
                        action.Label()
                    }
                }
            }
        }
    }
}
