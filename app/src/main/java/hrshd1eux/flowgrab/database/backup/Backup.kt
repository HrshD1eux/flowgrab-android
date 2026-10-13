package hrshd1eux.flowgrab.database.backup

import hrshd1eux.flowgrab.database.objects.CommandTemplate
import hrshd1eux.flowgrab.database.objects.DownloadedVideoInfo
import hrshd1eux.flowgrab.database.objects.OptionShortcut
import kotlinx.serialization.Serializable

@Serializable
data class Backup(
    val templates: List<CommandTemplate>? = null,
    val shortcuts: List<OptionShortcut>? = null,
    val downloadHistory: List<DownloadedVideoInfo>? = null,
)
