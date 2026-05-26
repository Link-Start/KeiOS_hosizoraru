package os.kei.ui.page.main.github.share

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import os.kei.MainActivity
import os.kei.R
import os.kei.core.ext.showToast
import os.kei.core.intent.SafeExternalIntents
import os.kei.core.platform.PredictiveBackOemCompat
import os.kei.core.prefs.AppThemeMode
import os.kei.core.prefs.UiPrefs
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.ui.page.main.back.KeiOSActivityRootBackHandler
import os.kei.ui.page.main.back.ProvideBackNavigationRuntime
import os.kei.ui.page.main.widget.motion.LocalPredictiveBackAnimationsEnabled
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import android.graphics.Color as AndroidColor

class GitHubShareImportActivity : ComponentActivity() {
    private var incomingGitHubShareText by mutableStateOf<String?>(null)
    private var incomingGitHubShareToken by mutableIntStateOf(0)
    private var shareImportResumeToken by mutableIntStateOf(0)
    private var shareImportDisplayState by mutableStateOf(
        GitHubShareImportActivityDisplayState.Hidden,
    )
    private var sendInstallInProgress by mutableStateOf(false)
    private var flowActivityBackNeedsInterception by mutableStateOf(false)
    private var incomingShareResolveToken = 0
    private var notificationPermissionRequestReady = false
    private val shareImportRepository = GitHubShareImportWindowRepository()
    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureTransparentShareImportWindow()
        consumeIncomingShareIntent(intent)
        if (isFinishing) return
        requestNotificationPermissionIfNeededForActiveFlow()

        setContent {
            val displayState = shareImportDisplayState
            if (
                displayState == GitHubShareImportActivityDisplayState.Hidden ||
                displayState == GitHubShareImportActivityDisplayState.SendingInstall ||
                displayState == GitHubShareImportActivityDisplayState.Finish
            ) {
                return@setContent
            }

            val appThemeMode = UiPrefs.getAppThemeMode()
            val transitionAnimationsEnabled = UiPrefs.isTransitionAnimationsEnabled()
            val predictiveBackPolicy =
                PredictiveBackOemCompat.currentPolicy(
                    transitionAnimationsEnabled = transitionAnimationsEnabled,
                    predictiveBackAnimationsEnabled = UiPrefs.isPredictiveBackAnimationsEnabled(),
                )
            val colorSchemeMode =
                when (appThemeMode) {
                    AppThemeMode.FOLLOW_SYSTEM -> ColorSchemeMode.System
                    AppThemeMode.LIGHT -> ColorSchemeMode.Light
                    AppThemeMode.DARK -> ColorSchemeMode.Dark
                }
            val controller = ThemeController(colorSchemeMode)

            MiuixTheme(controller = controller) {
                ProvideBackNavigationRuntime(policy = predictiveBackPolicy) {
                    CompositionLocalProvider(
                        LocalTransitionAnimationsEnabled provides transitionAnimationsEnabled,
                        LocalPredictiveBackAnimationsEnabled provides predictiveBackPolicy.localPredictiveBackEnabled,
                    ) {
                        KeiOSActivityRootBackHandler(
                            needsInterception =
                                displayState == GitHubShareImportActivityDisplayState.SendingInstall ||
                                    flowActivityBackNeedsInterception,
                            onBack = { finishSafely() },
                        )
                        Box(modifier = Modifier.fillMaxSize()) {
                            when (displayState) {
                                GitHubShareImportActivityDisplayState.Sheet -> {
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
                                                showToast(getString(R.string.common_open_link_failed))
                                            }
                                            finishSafely()
                                        },
                                        showPendingArmedSheet = true,
                                        onNotificationOnlyResolveChanged = {
                                            clearShareImportWindowDim()
                                        },
                                        onActivityBackInterceptionChanged = { needsInterception ->
                                            flowActivityBackNeedsInterception = needsInterception
                                        },
                                        onMinimizeActiveFlow = { finishSafely() },
                                        onClosePendingArmedSheet = { finishSafely() },
                                        onIdleWithNoPendingFlow = { finishSafely() },
                                    )
                                }

                                GitHubShareImportActivityDisplayState.Hidden,
                                GitHubShareImportActivityDisplayState.SendingInstall,
                                GitHubShareImportActivityDisplayState.Finish,
                                -> {
                                    Unit
                                }
                            }
                        }
                    }
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
        incomingShareResolveToken += 1
        notificationPermissionRequestReady = false
        if (intent?.action == ACTION_RESUME_SHARE_IMPORT) {
            sendInstallInProgress = false
            flowActivityBackNeedsInterception = false
            incomingGitHubShareText = null
            applyShareImportDisplayState(GitHubShareImportActivityDisplayState.Sheet)
            shareImportResumeToken += 1
            notificationPermissionRequestReady = true
            return
        }
        if (intent?.action == ACTION_SEND_INSTALL_SHARE_IMPORT) {
            flowActivityBackNeedsInterception = false
            incomingGitHubShareText = null
            applyShareImportDisplayState(GitHubShareImportActivityDisplayState.SendingInstall)
            launchSendInstallAndFinish()
            return
        }
        sendInstallInProgress = false
        flowActivityBackNeedsInterception = false
        if (!SafeExternalIntents.isPlainTextSend(intent)) {
            applyShareImportDisplayState(GitHubShareImportActivityDisplayState.Finish)
            finishSafely()
            return
        }
        val sharedText =
            intent
                ?.getStringExtra(Intent.EXTRA_TEXT)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: run {
                    applyShareImportDisplayState(GitHubShareImportActivityDisplayState.Finish)
                    finishSafely()
                    return
                }
        val resolveToken = incomingShareResolveToken
        incomingGitHubShareText = null
        applyShareImportDisplayState(GitHubShareImportActivityDisplayState.Hidden)
        lifecycleScope.launch {
            val resolvedShare = shareImportRepository.resolveIncomingShare(sharedText)
            if (resolveToken != incomingShareResolveToken || isFinishing) return@launch
            applyResolvedIncomingShare(
                resolvedShare = resolvedShare,
            )
        }
    }

    private fun applyResolvedIncomingShare(resolvedShare: GitHubShareImportResolvedIncomingShare) {
        val displayState = resolvedShare.displayState
        applyShareImportDisplayState(displayState)
        notificationPermissionRequestReady = displayState != GitHubShareImportActivityDisplayState.Finish
        requestNotificationPermissionIfNeededForActiveFlow()
        when (displayState) {
            GitHubShareImportActivityDisplayState.Finish -> {
                finishSafely()
            }

            GitHubShareImportActivityDisplayState.Hidden -> {
                incomingGitHubShareText = null
                startIncomingShareInBackground(
                    sharedText = resolvedShare.sharedText,
                    lookupConfig = resolvedShare.lookupConfig,
                )
            }

            GitHubShareImportActivityDisplayState.Sheet -> {
                incomingGitHubShareText = resolvedShare.sharedText
                incomingGitHubShareToken += 1
            }

            GitHubShareImportActivityDisplayState.SendingInstall -> {
                Unit
            }
        }
    }

    private fun startIncomingShareInBackground(
        sharedText: String,
        lookupConfig: GitHubLookupConfig,
    ) {
        lifecycleScope.launch {
            GitHubShareImportFlowCoordinator.startIncomingShare(
                context = this@GitHubShareImportActivity,
                sharedText = sharedText,
                lookupConfig = lookupConfig,
            )
            if (shareImportDisplayState == GitHubShareImportActivityDisplayState.Hidden) {
                finishSafely()
            }
        }
    }

    private fun applyShareImportDisplayState(state: GitHubShareImportActivityDisplayState) {
        shareImportDisplayState = state
        clearShareImportWindowDim()
    }

    private fun launchSendInstallAndFinish() {
        if (sendInstallInProgress) return
        sendInstallInProgress = true
        applyShareImportDisplayState(GitHubShareImportActivityDisplayState.SendingInstall)
        GitHubShareImportDeliveryRunner.launchCurrentDeliveryAction(this)
        finishSafely()
    }

    private fun openGitHubPage(): Boolean {
        val targetIntent =
            Intent(this, MainActivity::class.java).apply {
                putExtra(
                    MainActivity.EXTRA_TARGET_BOTTOM_PAGE,
                    MainActivity.TARGET_BOTTOM_PAGE_GITHUB,
                )
                addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NEW_TASK,
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
                            MainActivity.TARGET_BOTTOM_PAGE_GITHUB,
                        )
                    },
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

    private fun configureTransparentShareImportWindow() {
        enableEdgeToEdge()
        window.setBackgroundDrawable(AndroidColor.TRANSPARENT.toDrawable())
        window.isNavigationBarContrastEnforced = false
        clearShareImportWindowDim()
    }

    private fun clearShareImportWindowDim() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.attributes =
            window.attributes.apply {
                dimAmount = 0f
            }
    }

    private fun requestNotificationPermissionIfNeededForActiveFlow() {
        if (!notificationPermissionRequestReady) return
        if (sendInstallInProgress) return
        if (
            shareImportDisplayState == GitHubShareImportActivityDisplayState.SendingInstall ||
            shareImportDisplayState == GitHubShareImportActivityDisplayState.Finish
        ) {
            return
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return
        }
        requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    companion object {
        const val ACTION_RESUME_SHARE_IMPORT = "os.kei.github.share_import.action.RESUME"
        const val ACTION_SEND_INSTALL_SHARE_IMPORT =
            "os.kei.github.share_import.action.SEND_INSTALL"
        const val EXTRA_FORCE_SHEET = "os.kei.github.share_import.extra.FORCE_SHEET"
    }
}
