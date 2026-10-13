package hrshd1eux.flowgrab.ui.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import hrshd1eux.flowgrab.download.DownloaderV2
import hrshd1eux.flowgrab.util.PreferenceUtil
import hrshd1eux.flowgrab.util.PreferenceUtil.getBoolean
import hrshd1eux.flowgrab.util.PreferenceUtil.getLong
import hrshd1eux.flowgrab.util.PreferenceUtil.getString
import hrshd1eux.flowgrab.util.UpdateUtil
import hrshd1eux.flowgrab.util.YT_DLP_AUTO_UPDATE
import hrshd1eux.flowgrab.util.YT_DLP_UPDATE_INTERVAL
import hrshd1eux.flowgrab.util.YT_DLP_UPDATE_TIME
import hrshd1eux.flowgrab.util.YT_DLP_VERSION
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@Composable
fun YtdlpUpdater(downloader: DownloaderV2 = koinInject()) {
    LaunchedEffect(Unit) {
        if (downloader.isBusy()) return@LaunchedEffect

        if (!YT_DLP_AUTO_UPDATE.getBoolean() && YT_DLP_VERSION.getString().isNotEmpty())
            return@LaunchedEffect

        if (!PreferenceUtil.isNetworkAvailableForDownload()) {
            return@LaunchedEffect
        }

        val lastUpdateTime = YT_DLP_UPDATE_TIME.getLong()
        val currentTime = System.currentTimeMillis()

        if (currentTime < lastUpdateTime + YT_DLP_UPDATE_INTERVAL.getLong()) {
            return@LaunchedEffect
        }

        runCatching {
            withContext(Dispatchers.IO) { UpdateUtil.updateYtDlp() }
        }.onFailure { it.printStackTrace() }
    }
}
