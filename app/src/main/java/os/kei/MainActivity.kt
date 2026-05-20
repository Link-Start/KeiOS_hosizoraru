@file:Suppress("FunctionName")

package os.kei

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import os.kei.core.ext.showToast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import os.kei.core.concurrency.AppDispatchers
import kotlinx.coroutines.launch
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.metrics.performance.JankStats
import os.kei.core.icon.LauncherIconController
import os.kei.core.perf.AppJankMonitor
import os.kei.core.platform.LocalNetworkPermissionCompat
import os.kei.core.platform.TransientExternalLaunchGuard
import os.kei.core.prefs.AppThemeMode
import os.kei.core.prefs.UiPrefs
import os.kei.core.shortcut.AppShortcuts
import os.kei.core.shizuku.ShizukuApiUtils
import os.kei.mcp.notification.McpNotificationHelper
import os.kei.mcp.server.LocalMcpService
import os.kei.mcp.server.McpServerManager
import os.kei.ui.page.main.ba.BaApIslandShortcutNotificationCoordinator
import os.kei.ui.page.main.host.main.MainHostCallbacks
import os.kei.ui.page.main.host.main.MainHostUiState
import os.kei.ui.page.main.host.main.MainScreen
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_TARGET_BOTTOM_PAGE = "os.kei.extra.TARGET_BOTTOM_PAGE"
        const val EXTRA_MCP_SERVER_ACTION = "os.kei.extra.MCP_SERVER_ACTION"
        const val EXTRA_SHORTCUT_ACTION = "os.kei.extra.SHORTCUT_ACTION"
        const val EXTRA_GITHUB_ACTIONS_TRACK_ID = "os.kei.extra.GITHUB_ACTIONS_TRACK_ID"
        const val TARGET_BOTTOM_PAGE_OS = "Os"
        const val TARGET_BOTTOM_PAGE_GITHUB = "GitHub"
        const val TARGET_BOTTOM_PAGE_MCP = "Mcp"
        const val TARGET_BOTTOM_PAGE_BA = "Ba"
        const val MCP_SERVER_ACTION_TOGGLE = "toggle"
        const val SHORTCUT_ACTION_BA_AP_ISLAND = "ba_ap_island"
        const val SHORTCUT_ACTION_BA_OPEN_BGM_PLAYBACK = "ba_open_bgm_playback"
        const val SHORTCUT_ACTION_GITHUB_REFRESH_TRACKED = "github_refresh_tracked"
    }

    /**
     * Single Compose-observable holder for every Activity-owned UI signal that flows into
     * [MainScreen]. Collapsing 11 independent `mutableStateOf` fields into one [MainHostUiState]
     * lets `MainScreen` skip recomposition by structural equality and removes 19 individual
     * parameters from the `setContent` call site.
     */
    private var hostUiState by mutableStateOf(
        MainHostUiState.Initial.copy(appThemeMode = UiPrefs.getAppThemeMode())
    )
    private var pendingMcpServerAction: String? = null
    private var pendingShortcutAction: String? = null
    private var startMcpAfterLocalNetworkPermission = false
    private val shizukuApiUtils = ShizukuApiUtils()
    private lateinit var localMcpService: LocalMcpService
    private lateinit var mcpServerManager: McpServerManager
    private var jankStats: JankStats? = null
    private val transientExternalLaunchGuard = TransientExternalLaunchGuard()
    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hostUiState = hostUiState.copy(
                notificationPermissionGranted = granted,
                transientExternalLaunchActive = false
            )
            transientExternalLaunchGuard.clear()
        }
    private val requestLocalNetworkPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            transientExternalLaunchGuard.clear()
            hostUiState = hostUiState.copy(transientExternalLaunchActive = false)
            val permissionGranted = granted || hasLocalNetworkPermission()
            showToast(getString(
                if (permissionGranted) {
                    R.string.mcp_toast_local_network_permission_granted
                } else {
                    R.string.mcp_toast_local_network_permission_denied
                },
            ))
            val shouldStartMcp = startMcpAfterLocalNetworkPermission && permissionGranted
            startMcpAfterLocalNetworkPermission = false
            if (shouldStartMcp) {
                startMcpServerFromShortcutIfAllowed()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        LauncherIconController.applyDesign(this, UiPrefs.getLauncherIconDesign())
        applyWindowColorMode(UiPrefs.isHomeIconHdrEnabled())
        window.isNavigationBarContrastEnforced = false
        val initialNotificationGranted = hasNotificationPermission()
        hostUiState = hostUiState.copy(notificationPermissionGranted = initialNotificationGranted)
        if (!initialNotificationGranted) {
            transientExternalLaunchGuard.markLaunching(
                TransientExternalLaunchGuard.Reason.NotificationPermission,
            )
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        consumeIntentNavigation(intent)

        val appLabel =
            runCatching {
                packageManager.getApplicationLabel(applicationInfo).toString()
            }.getOrDefault("KeiOS")
        val packageInfo =
            runCatching {
                packageManager.getPackageInfoCompat(packageName)
            }.getOrNull()
        localMcpService =
            LocalMcpService(
                appContext = applicationContext,
                shizukuApiUtils = shizukuApiUtils,
                appVersionName = packageInfo?.versionName ?: "unknown",
                appVersionCode = packageInfo?.longVersionCode ?: -1L,
                appPackageName = packageName,
                appLabel = appLabel,
            )
        mcpServerManager =
            McpServerManager(
                appContext = applicationContext,
                localMcpService = localMcpService,
            )
        applyPendingShortcutActions()
        // Defer non-first-frame work: shortcut sync writes to system storage, Xiaomi network
        // restoration may trigger system calls. Neither affects the first Compose frame.
        lifecycleScope.launch(AppDispatchers.fileIo) {
            runCatching { McpNotificationHelper.restoreXiaomiNetworkIfNeeded(this@MainActivity) }
            runCatching { AppShortcuts.sync(this@MainActivity) }
        }

        shizukuApiUtils.attach { status ->
            hostUiState = hostUiState.copy(shizukuStatus = status)
        }

        // Stable callback bundle: created once and reused across recompositions so MainScreen does
        // not see new lambda identities every time hostUiState changes.
        val hostCallbacks = MainHostCallbacks(
            onCheckOrRequestShizuku = { shizukuApiUtils.requestPermissionIfNeeded() },
            onRequestNotificationPermission = { requestNotificationPermissionIfNeeded() },
            onAppThemeModeChanged = { mode ->
                hostUiState = hostUiState.copy(appThemeMode = mode)
                UiPrefs.setAppThemeMode(mode)
            },
            onRequestedBottomPageConsumed = {
                hostUiState = hostUiState.copy(requestedBottomPage = null)
            }
        )

        setContent {
            val state = hostUiState
            val colorSchemeMode =
                when (state.appThemeMode) {
                    AppThemeMode.FOLLOW_SYSTEM -> ColorSchemeMode.System
                    AppThemeMode.LIGHT -> ColorSchemeMode.Light
                    AppThemeMode.DARK -> ColorSchemeMode.Dark
                }
            val controller = ThemeController(colorSchemeMode)

            MiuixTheme(controller = controller) {
                CompositionLocalProvider(LocalOverscrollFactory provides null) {
                    SystemBarAutoStyle(state.appThemeMode)
                    MainScreen(
                        appLabel = appLabel,
                        packageInfo = packageInfo,
                        hostState = state,
                        hostCallbacks = hostCallbacks,
                        shizukuApiUtils = shizukuApiUtils,
                        mcpServerManager = mcpServerManager,
                    )
                }
            }
        }
        jankStats =
            AppJankMonitor.attach(
                window = window,
                // Enable for debug and benchmark variants so Macrobenchmark runs collect jank
                // data for comparison. Disabled in release to avoid any frame-callback overhead.
                enabled = BuildConfig.BUILD_TYPE != "release",
            )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        transientExternalLaunchGuard.markLaunching(
            TransientExternalLaunchGuard.Reason.NotificationRoute,
        )
        setIntent(intent)
        consumeIntentNavigation(intent)
        applyPendingShortcutActions()
    }

    override fun onStop() {
        hostUiState = hostUiState.copy(
            transientExternalLaunchActive = transientExternalLaunchGuard.shouldDeferStopWork()
        )
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        transientExternalLaunchGuard.onResume()
        hostUiState = hostUiState.copy(transientExternalLaunchActive = false)
    }

    override fun onDestroy() {
        jankStats?.isTrackingEnabled = false
        jankStats = null
        // Only stop the MCP server when the user is intentionally leaving (back press / finish).
        // If the system is reclaiming the Activity while McpKeepAliveService is running as a
        // foreground service, the server should survive so connected clients are not dropped.
        if (isFinishing) {
            runCatching { mcpServerManager.stop() }
        }
        shizukuApiUtils.detach()
        super.onDestroy()
    }

    private fun requestNotificationPermissionIfNeeded() {
        val granted = hasNotificationPermission()
        hostUiState = hostUiState.copy(notificationPermissionGranted = granted)
        if (!granted) {
            transientExternalLaunchGuard.markLaunching(
                TransientExternalLaunchGuard.Reason.NotificationPermission,
            )
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun hasNotificationPermission() =
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun requestLocalNetworkPermissionIfNeeded(startMcpAfterGrant: Boolean = false): Boolean {
        if (hasLocalNetworkPermission()) return true
        val permission = LocalNetworkPermissionCompat.requiredPermissionOrNull() ?: return true
        startMcpAfterLocalNetworkPermission = startMcpAfterGrant
        transientExternalLaunchGuard.markLaunching(
            TransientExternalLaunchGuard.Reason.LocalNetworkPermission,
        )
        requestLocalNetworkPermissionLauncher.launch(permission)
        showToast(getString(R.string.mcp_toast_local_network_permission_requested))
        return false
    }

    private fun hasLocalNetworkPermission(): Boolean = LocalNetworkPermissionCompat.hasPermission(this)

    private fun applyWindowColorMode(hdrEnabled: Boolean) {
        runCatching {
            window.colorMode =
                if (hdrEnabled) {
                    ActivityInfo.COLOR_MODE_HDR
                } else {
                    ActivityInfo.COLOR_MODE_DEFAULT
                }
        }
    }

    private fun consumeIntentNavigation(intent: Intent?) {
        pendingMcpServerAction = null
        pendingShortcutAction = null
        val route =
            MainActivityIntentRouting.sanitize(
                rawTargetBottomPage = intent?.getStringExtra(EXTRA_TARGET_BOTTOM_PAGE),
                rawMcpServerAction = intent?.getStringExtra(EXTRA_MCP_SERVER_ACTION),
                rawShortcutAction = intent?.getStringExtra(EXTRA_SHORTCUT_ACTION),
                rawGitHubActionsTrackId = intent?.getStringExtra(EXTRA_GITHUB_ACTIONS_TRACK_ID),
            ) ?: return
        val previous = hostUiState
        val nextActionsTrackId = route.githubActionsTrackId ?: previous.requestedGitHubActionsTrackId
        val nextActionsSheetToken = if (route.githubActionsTrackId != null) {
            previous.requestedGitHubActionsSheetToken + 1
        } else {
            previous.requestedGitHubActionsSheetToken
        }
        hostUiState = previous.copy(
            requestedBottomPage = route.targetBottomPage,
            requestedBottomPageToken = previous.requestedBottomPageToken + 1,
            requestedGitHubActionsTrackId = nextActionsTrackId,
            requestedGitHubActionsSheetToken = nextActionsSheetToken
        )
        pendingMcpServerAction = route.mcpServerAction
        pendingShortcutAction = route.shortcutAction
    }

    private fun applyPendingShortcutActions() {
        applyPendingMcpServerAction()
        applyPendingBaApIslandAction()
        applyPendingBaBgmPlaybackAction()
        applyPendingGitHubRefreshAction()
        pendingShortcutAction = null
    }

    private fun applyPendingMcpServerAction() {
        if (!::mcpServerManager.isInitialized) return
        val action = pendingMcpServerAction ?: return
        pendingMcpServerAction = null
        if (action != MCP_SERVER_ACTION_TOGGLE) return

        val state = mcpServerManager.uiState.value
        if (state.running) {
            mcpServerManager.stop()
        } else {
            startMcpServerFromShortcutIfAllowed()
        }
    }

    private fun startMcpServerFromShortcutIfAllowed() {
        if (!::mcpServerManager.isInitialized) return
        val state = mcpServerManager.uiState.value
        if (state.running) return
        if (state.allowExternal && !requestLocalNetworkPermissionIfNeeded(startMcpAfterGrant = true)) return
        mcpServerManager.start(
            port = state.port,
            allowExternal = state.allowExternal,
        )
    }

    private fun applyPendingBaApIslandAction() {
        val action = pendingShortcutAction ?: return
        if (action != SHORTCUT_ACTION_BA_AP_ISLAND) return
        pendingShortcutAction = null
        val sent = BaApIslandShortcutNotificationCoordinator.send(this)
        if (!sent) {
            showToast(getString(R.string.ba_toast_notification_permission_required))
        }
    }

    private fun applyPendingGitHubRefreshAction() {
        val action = pendingShortcutAction ?: return
        if (action != SHORTCUT_ACTION_GITHUB_REFRESH_TRACKED) return
        pendingShortcutAction = null
        hostUiState = hostUiState.copy(
            requestedGitHubRefreshToken = hostUiState.requestedGitHubRefreshToken + 1
        )
    }

    private fun applyPendingBaBgmPlaybackAction() {
        val action = pendingShortcutAction ?: return
        if (action != SHORTCUT_ACTION_BA_OPEN_BGM_PLAYBACK) return
        pendingShortcutAction = null
        hostUiState = hostUiState.copy(
            requestedBaBgmPlaybackToken = hostUiState.requestedBaBgmPlaybackToken + 1
        )
    }
}

private fun PackageManager.getPackageInfoCompat(packageName: String): PackageInfo = getPackageInfo(packageName, 0)

@Composable
@Suppress("DEPRECATION")
private fun SystemBarAutoStyle(appThemeMode: AppThemeMode) {
    val view = LocalView.current
    val darkTheme =
        when (appThemeMode) {
            AppThemeMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
            AppThemeMode.LIGHT -> false
            AppThemeMode.DARK -> true
        }
    val backgroundColor = MiuixTheme.colorScheme.background
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? MainActivity)?.window ?: return@SideEffect
            window.statusBarColor = backgroundColor.toArgb()
            window.navigationBarColor = backgroundColor.copy(alpha = 0.85f).toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }
}
