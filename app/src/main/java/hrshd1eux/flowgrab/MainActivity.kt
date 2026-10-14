package hrshd1eux.flowgrab

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import hrshd1eux.flowgrab.ui.common.LocalDarkTheme
import hrshd1eux.flowgrab.ui.common.SettingsProvider
import hrshd1eux.flowgrab.ui.page.AppEntry
import hrshd1eux.flowgrab.ui.page.downloadv2.configure.DownloadDialogViewModel
import hrshd1eux.flowgrab.ui.theme.SealTheme
import hrshd1eux.flowgrab.util.PreferenceUtil
import hrshd1eux.flowgrab.util.matchUrlFromSharedText
import hrshd1eux.flowgrab.util.sanitizeUrl
import hrshd1eux.flowgrab.util.setLanguage
import kotlinx.coroutines.runBlocking
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.compose.KoinContext

class MainActivity : AppCompatActivity() {
    private val dialogViewModel: DownloadDialogViewModel by viewModel()

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT < 33) {
            setLanguage(PreferenceUtil.getLocaleFromPreference())
        }
        enableEdgeToEdge()

        if (intent?.getBooleanExtra("EXTRA_INSTALL_UPDATE", false) == true) {
            hrshd1eux.flowgrab.util.UpdateUtil.installLatestApk(this)
        }

        setContent {
            KoinContext {
                val windowSizeClass = calculateWindowSizeClass(this)
                SettingsProvider(windowWidthSizeClass = windowSizeClass.widthSizeClass) {
                    SealTheme(
                        darkTheme = LocalDarkTheme.current.isDarkTheme(),
                        isHighContrastModeEnabled = LocalDarkTheme.current.isHighContrastModeEnabled,
                    ) {
                        AppEntry(dialogViewModel = dialogViewModel)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("EXTRA_INSTALL_UPDATE", false)) {
            hrshd1eux.flowgrab.util.UpdateUtil.installLatestApk(this)
            return
        }
        val url = intent.getSharedURL()
        if (url != null) {
            dialogViewModel.postAction(DownloadDialogViewModel.Action.ShowSheet(listOf(url)))
        }
    }

    private fun Intent.getSharedURL(): String? {
        val intent = this

        return when (intent.action) {
            Intent.ACTION_VIEW -> {
                intent.dataString?.let { sanitizeUrl(it) }
            }

            Intent.ACTION_SEND -> {
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let { sharedContent ->
                    intent.removeExtra(Intent.EXTRA_TEXT)
                    matchUrlFromSharedText(sharedContent).also { matchedUrl ->
                        if (sharedUrlCached != matchedUrl) {
                            sharedUrlCached = matchedUrl
                        }
                    }
                }
            }

            else -> {
                null
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private var sharedUrlCached = ""
    }
}
