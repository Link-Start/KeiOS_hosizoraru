package os.kei.ui.page.main.github.share

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import os.kei.MainActivity
import os.kei.R
import os.kei.core.intent.SafeExternalIntents
import os.kei.core.prefs.AppThemeMode
import os.kei.core.prefs.UiPrefs
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.remote.GitHubShareIntentParser
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

class GitHubShareImportActivity : ComponentActivity() {
    private var incomingGitHubShareText by mutableStateOf<String?>(null)
    private var incomingGitHubShareToken by mutableIntStateOf(0)
    private var shareImportResumeToken by mutableIntStateOf(0)
    private var shareImportDisabled by mutableStateOf(false)
    private var sendInstallInProgress by mutableStateOf(false)
    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeIncomingShareIntent(intent)
        if (isFinishing) return
        requestNotificationPermissionIfNeededForActiveFlow()

        setContent {
            val appThemeMode = UiPrefs.getAppThemeMode()
            val colorSchemeMode = when (appThemeMode) {
                AppThemeMode.FOLLOW_SYSTEM -> ColorSchemeMode.System
                AppThemeMode.LIGHT -> ColorSchemeMode.Light
                AppThemeMode.DARK -> ColorSchemeMode.Dark
            }
            val controller = ThemeController(colorSchemeMode)

            MiuixTheme(controller = controller) {
                Box(modifier = Modifier.fillMaxSize())
                if (sendInstallInProgress) {
                    Box(modifier = Modifier.fillMaxSize())
                } else if (shareImportDisabled) {
                    GitHubShareImportDisabledSheet(
                        show = true,
                        onClose = { finishSafely() },
                        onOpenGitHub = {
                            openGitHubPage()
                            finishSafely()
                        }
                    )
                } else {
                    GitHubShareImportWindowFlowHost(
                        incomingGitHubShareText = incomingGitHubShareText,
                        incomingGitHubShareToken = incomingGitHubShareToken,
                        resumeRequestToken = shareImportResumeToken,
                        onIncomingGitHubShareConsumed = {
                            incomingGitHubShareText = null
                        },
                        onNavigateToGitHubPage = {
                            val launched = openGitHubPage()
                            if (!launched) {
                                Toast.makeText(
                                    this,
                                    getString(R.string.common_open_link_failed),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            finishSafely()
                        },
                        showPendingArmedSheet = true,
                        onNotificationOnlyResolveChanged = { notificationOnly ->
                            setShareImportWindowDim(enabled = !notificationOnly)
                        },
                        onMinimizeActiveFlow = { finishSafely() },
                        onClosePendingArmedSheet = { finishSafely() },
                        onIdleWithNoPendingFlow = { finishSafely() }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeIncomingShareIntent(intent)
        if (!isFinishing) {
            requestNotificationPermissionIfNeededForActiveFlow()
        }
    }

    private fun consumeIncomingShareIntent(intent: Intent?) {
        if (intent?.action == ACTION_RESUME_SHARE_IMPORT) {
            shareImportDisabled = false
            sendInstallInProgress = false
            incomingGitHubShareText = null
            shareImportResumeToken += 1
            return
        }
        if (intent?.action == ACTION_SEND_INSTALL_SHARE_IMPORT) {
            shareImportDisabled = false
            incomingGitHubShareText = null
            launchSendInstallAndFinish()
            return
        }
        sendInstallInProgress = false
        if (!SafeExternalIntents.isPlainTextSend(intent)) {
            finishSafely()
            return
        }
        val sharedText = intent?.getStringExtra(Intent.EXTRA_TEXT)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: run {
                finishSafely()
                return
            }
        if (!GitHubShareIntentParser.looksLikeGitHubShareText(sharedText)) {
            finishSafely()
            return
        }
        val shareImportEnabled = GitHubTrackStore.loadLookupConfig().shareImportLinkageEnabled
        if (!shareImportEnabled) {
            shareImportDisabled = true
            return
        }
        shareImportDisabled = false
        incomingGitHubShareText = sharedText
        incomingGitHubShareToken += 1
    }

    private fun launchSendInstallAndFinish() {
        if (sendInstallInProgress) return
        sendInstallInProgress = true
        lifecycleScope.launch {
            GitHubShareImportFlowCoordinator.sendActivePreviewAssetToInstaller(this@GitHubShareImportActivity)
            finishSafely()
        }
    }

    private fun openGitHubPage(): Boolean {
        val targetIntent = Intent(this, MainActivity::class.java).apply {
            putExtra(
                MainActivity.EXTRA_TARGET_BOTTOM_PAGE,
                MainActivity.TARGET_BOTTOM_PAGE_GITHUB
            )
            addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NEW_TASK
            )
        }
        return runCatching {
            startActivity(targetIntent)
            true
        }.getOrElse {
            runCatching {
                startActivity(
                    Intent(this, MainActivity::class.java).apply {
                        putExtra(
                            MainActivity.EXTRA_TARGET_BOTTOM_PAGE,
                            MainActivity.TARGET_BOTTOM_PAGE_GITHUB
                        )
                    }
                )
                true
            }.getOrDefault(false)
        }
    }

    private fun finishSafely() {
        if (!isFinishing) {
            finish()
        }
    }

    private fun setShareImportWindowDim(enabled: Boolean) {
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
    }

    private fun requestNotificationPermissionIfNeededForActiveFlow() {
        if (sendInstallInProgress) return
        if (shareImportDisabled) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return
        }
        requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    companion object {
        const val ACTION_RESUME_SHARE_IMPORT = "os.kei.github.share_import.action.RESUME"
        const val ACTION_SEND_INSTALL_SHARE_IMPORT =
            "os.kei.github.share_import.action.SEND_INSTALL"
    }
}
