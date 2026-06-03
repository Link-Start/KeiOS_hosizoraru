package os.kei.ui.page.main.host.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import os.kei.R
import os.kei.core.shizuku.ShizukuApiUtils
import os.kei.mcp.server.McpServerManager
import os.kei.ui.navigation.KeiosRoute
import os.kei.ui.navigation.Navigator
import os.kei.ui.page.main.model.BottomPage

@Composable
fun MainScreen(
    appLabel: String,
    hostState: MainHostUiState,
    hostCallbacks: MainHostCallbacks,
    shizukuApiUtils: ShizukuApiUtils,
    mcpServerManager: McpServerManager
) {
    val backStack = remember { mutableStateListOf<NavKey>().apply { add(KeiosRoute.Main) } }
    val navigator = remember { Navigator(backStack) }
    val context = LocalContext.current
    val appContext = context.applicationContext
    val view = LocalView.current
    val currentAppLabel by rememberUpdatedState(appLabel)
    val currentShizukuStatus by rememberUpdatedState(hostState.shizukuStatus)
    val currentNotificationPermissionGranted by rememberUpdatedState(hostState.notificationPermissionGranted)
    val currentOnCheckOrRequestShizuku by rememberUpdatedState(hostCallbacks.onCheckOrRequestShizuku)
    val currentOnAppThemeModeChanged by rememberUpdatedState(hostCallbacks.onAppThemeModeChanged)
    val prefsViewModel: MainScreenPrefsViewModel = viewModel()
    val guideNavigationViewModel: MainScreenGuideNavigationViewModel = viewModel()
    var localRequestedBottomPage by rememberSaveable { mutableStateOf<String?>(null) }
    var localRequestedBottomPageToken by rememberSaveable { mutableIntStateOf(0) }
    var localRequestedGitHubActionsTrackId by rememberSaveable { mutableStateOf<String?>(null) }
    var localRequestedGitHubActionsSheetToken by rememberSaveable { mutableIntStateOf(0) }
    val externalBottomPageRequested = !hostState.requestedBottomPage.isNullOrBlank()
    val externalGitHubActionsRequested =
        hostState.requestedGitHubActionsSheetToken > 0 &&
            !hostState.requestedGitHubActionsTrackId.isNullOrBlank()
    val effectiveRequestedBottomPage = if (externalBottomPageRequested) {
        hostState.requestedBottomPage
    } else {
        localRequestedBottomPage
    }
    val effectiveRequestedBottomPageToken = if (externalBottomPageRequested) {
        hostState.requestedBottomPageToken
    } else {
        localRequestedBottomPageToken
    }
    val effectiveRequestedGitHubActionsTrackId = if (externalGitHubActionsRequested) {
        hostState.requestedGitHubActionsTrackId
    } else {
        localRequestedGitHubActionsTrackId
    }
    val effectiveRequestedGitHubActionsSheetToken = if (externalGitHubActionsRequested) {
        hostState.requestedGitHubActionsSheetToken
    } else {
        localRequestedGitHubActionsSheetToken
    }
    LaunchedEffect(prefsViewModel) {
        prefsViewModel.loadInitialSnapshot()
    }
    val uiPrefsSnapshot by prefsViewModel.snapshot.collectAsStateWithLifecycle()
    val mainReturnState = rememberMainScreenSettingsReturnState(backStack)
    BindMainScreenBottomPageReturnEffect(
        requestedBottomPageToken = effectiveRequestedBottomPageToken,
        requestedBottomPage = effectiveRequestedBottomPage,
        onReturnToMain = {
            navigator.popUntil { it == KeiosRoute.Main }
        }
    )
    LaunchedEffect(hostState.requestedBaBgmPlaybackToken) {
        if (hostState.requestedBaBgmPlaybackToken <= 0) return@LaunchedEffect
        navigator.popUntil { it == KeiosRoute.Main }
        navigator.push(
            KeiosRoute.BaGuideCatalog(
                openBgmPlaybackToken = hostState.requestedBaBgmPlaybackToken.toLong()
            )
        )
    }
    LaunchedEffect(guideNavigationViewModel, navigator) {
        guideNavigationViewModel.events.collect { event ->
            when (event) {
                MainScreenGuideNavigationEvent.OpenStudentGuide -> {
                    navigator.push(KeiosRoute.BaStudentGuide(nonce = System.nanoTime()))
                }
            }
        }
    }
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
            guideNavigationViewModel.saveAndOpenCanonicalGuide(canonicalGuideUrl)
        }
    )
    val pagerCoordinator =
        remember(
            mainReturnState.settingsReturnToken,
            uiPrefsState,
            currentShizukuStatus,
            shizukuApiUtils,
            mcpServerManager,
            openGuideDetail,
            effectiveRequestedBottomPage,
            effectiveRequestedBottomPageToken,
            hostState.requestedGitHubRefreshToken,
            effectiveRequestedGitHubActionsTrackId,
            effectiveRequestedGitHubActionsSheetToken,
            externalBottomPageRequested,
            hostCallbacks,
        ) {
            buildMainScreenPagerCoordinator(
                settingsReturnToken = mainReturnState.settingsReturnToken,
                prefsState = uiPrefsState,
                shizukuStatus = currentShizukuStatus,
                shizukuApiUtils = shizukuApiUtils,
                mcpServerManager = mcpServerManager,
                onOpenGuideDetail = openGuideDetail,
                requestedBottomPage = effectiveRequestedBottomPage,
                requestedBottomPageToken = effectiveRequestedBottomPageToken,
                requestedGitHubRefreshToken = hostState.requestedGitHubRefreshToken,
                requestedGitHubActionsTrackId = effectiveRequestedGitHubActionsTrackId,
                requestedGitHubActionsSheetToken = effectiveRequestedGitHubActionsSheetToken,
                onRequestedBottomPageConsumed = {
                    if (externalBottomPageRequested) {
                        hostCallbacks.onRequestedBottomPageConsumed()
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
                },
            )
        }
    MainScreenNavHost(
        backStack = backStack,
        navigator = navigator,
        pagerCoordinator = pagerCoordinator,
        prefsState = uiPrefsState,
        appLabel = currentAppLabel,
        onCheckOrRequestShizuku = currentOnCheckOrRequestShizuku,
        notificationPermissionGranted = currentNotificationPermissionGranted,
        onRequestNotificationPermission = hostCallbacks.onRequestNotificationPermission,
        mcpServerManager = mcpServerManager,
        appThemeMode = hostState.appThemeMode,
        transientExternalLaunchActive = hostState.transientExternalLaunchActive,
        onAppThemeModeChanged = currentOnAppThemeModeChanged,
        onOpenGitHubActionsTrackFromHistory = { trackId ->
            val normalizedTrackId = trackId.trim()
            if (normalizedTrackId.isNotBlank()) {
                localRequestedBottomPage = BottomPage.GitHub.name
                localRequestedBottomPageToken += 1
                localRequestedGitHubActionsTrackId = normalizedTrackId
                localRequestedGitHubActionsSheetToken += 1
                navigator.popUntil { route -> route == KeiosRoute.Main }
            }
        },
    )
}
