package os.kei.ui.page.main.host.main

import android.content.pm.PackageInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import os.kei.R
import os.kei.core.prefs.AppThemeMode
import os.kei.core.system.ShizukuApiUtils
import os.kei.mcp.server.McpServerManager
import os.kei.ui.navigation.KeiosRoute
import os.kei.ui.navigation.Navigator
import os.kei.ui.page.main.model.BottomPage
import os.kei.ui.page.main.student.BaStudentGuideStore

@Composable
fun MainScreen(
    appLabel: String,
    packageInfo: PackageInfo?,
    shizukuStatus: String,
    onCheckOrRequestShizuku: () -> Unit,
    notificationPermissionGranted: Boolean,
    onRequestNotificationPermission: () -> Unit,
    shizukuApiUtils: ShizukuApiUtils,
    mcpServerManager: McpServerManager,
    appThemeMode: AppThemeMode,
    onAppThemeModeChanged: (AppThemeMode) -> Unit,
    requestedBottomPage: String?,
    requestedBottomPageToken: Int,
    requestedGitHubRefreshToken: Int,
    onRequestedBottomPageConsumed: () -> Unit
) {
    val backStack = remember { mutableStateListOf<NavKey>().apply { add(KeiosRoute.Main) } }
    val navigator = remember { Navigator(backStack) }
    val context = LocalContext.current
    val appContext = context.applicationContext
    val view = LocalView.current
    val currentAppLabel by rememberUpdatedState(appLabel)
    val currentPackageInfo by rememberUpdatedState(packageInfo)
    val currentShizukuStatus by rememberUpdatedState(shizukuStatus)
    val currentNotificationPermissionGranted by rememberUpdatedState(notificationPermissionGranted)
    val currentOnCheckOrRequestShizuku by rememberUpdatedState(onCheckOrRequestShizuku)
    val currentOnAppThemeModeChanged by rememberUpdatedState(onAppThemeModeChanged)
    val prefsViewModel: MainScreenPrefsViewModel = viewModel()
    var localRequestedBottomPage by rememberSaveable { mutableStateOf<String?>(null) }
    var localRequestedBottomPageToken by rememberSaveable { mutableIntStateOf(0) }
    val externalBottomPageRequested = !requestedBottomPage.isNullOrBlank()
    val effectiveRequestedBottomPage = if (externalBottomPageRequested) {
        requestedBottomPage
    } else {
        localRequestedBottomPage
    }
    val effectiveRequestedBottomPageToken = if (externalBottomPageRequested) {
        requestedBottomPageToken
    } else {
        localRequestedBottomPageToken
    }
    LaunchedEffect(prefsViewModel) {
        prefsViewModel.loadInitialSnapshot()
    }
    val uiPrefsSnapshot by prefsViewModel.snapshot.collectAsState()
    val mainReturnState = rememberMainScreenSettingsReturnState(backStack)
    BindMainScreenBottomPageReturnEffect(
        requestedBottomPageToken = effectiveRequestedBottomPageToken,
        requestedBottomPage = effectiveRequestedBottomPage,
        onReturnToMain = {
            navigator.popUntil { it == KeiosRoute.Main }
        }
    )
    val uiPrefsState = rememberMainScreenUiPrefsState(
        snapshot = uiPrefsSnapshot,
        appContext = appContext,
        mcpServerManager = mcpServerManager,
        viewModel = prefsViewModel
    )
    val poolGuideMissingText = stringResource(R.string.main_toast_pool_guide_missing)
    val externalOpenFailureText = stringResource(R.string.ba_error_open_activity_link)
    val openGuideDetail = rememberMainScreenOpenGuideDetailAction(
        poolGuideMissingText = poolGuideMissingText,
        externalOpenFailureText = externalOpenFailureText,
        onNavigateToCanonicalGuide = { canonicalGuideUrl ->
            BaStudentGuideStore.setCurrentUrl(canonicalGuideUrl)
            navigator.push(KeiosRoute.BaStudentGuide(nonce = System.nanoTime()))
        }
    )
    val pagerCoordinator = buildMainScreenPagerCoordinator(
        settingsReturnToken = mainReturnState.settingsReturnToken,
        prefsState = uiPrefsState,
        shizukuStatus = currentShizukuStatus,
        shizukuApiUtils = shizukuApiUtils,
        mcpServerManager = mcpServerManager,
        onOpenGuideDetail = openGuideDetail,
        requestedBottomPage = effectiveRequestedBottomPage,
        requestedBottomPageToken = effectiveRequestedBottomPageToken,
        requestedGitHubRefreshToken = requestedGitHubRefreshToken,
        onRequestedBottomPageConsumed = {
            if (externalBottomPageRequested) {
                onRequestedBottomPageConsumed()
            }
            localRequestedBottomPage = null
        },
        onBaGuideCatalogOpen = {
            localRequestedBottomPage = BottomPage.Ba.name
            localRequestedBottomPageToken += 1
        },
        onBaGuideCatalogBack = {
            localRequestedBottomPage = BottomPage.Ba.name
            localRequestedBottomPageToken += 1
        }
    )
    MainScreenNavHost(
        backStack = backStack,
        navigator = navigator,
        pagerCoordinator = pagerCoordinator,
        prefsState = uiPrefsState,
        appLabel = currentAppLabel,
        packageInfo = currentPackageInfo,
        onCheckOrRequestShizuku = currentOnCheckOrRequestShizuku,
        notificationPermissionGranted = currentNotificationPermissionGranted,
        onRequestNotificationPermission = onRequestNotificationPermission,
        mcpServerManager = mcpServerManager,
        appThemeMode = appThemeMode,
        onAppThemeModeChanged = currentOnAppThemeModeChanged,
    )
}
