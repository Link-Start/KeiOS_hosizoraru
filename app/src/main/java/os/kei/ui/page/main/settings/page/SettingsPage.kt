@file:Suppress("FunctionName")

package os.kei.ui.page.main.settings.page

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import os.kei.R
import os.kei.core.log.AppLogLevel
import os.kei.core.prefs.AppThemeMode
import os.kei.core.prefs.LauncherIconDesign
import os.kei.core.shizuku.ShizukuApiUtils
import os.kei.ui.page.main.host.pager.rememberMainLoadedPagerState
import os.kei.ui.page.main.os.appLucideBackIcon
import os.kei.ui.page.main.os.appLucideSearchIcon
import os.kei.ui.page.main.settings.state.SettingsPageUiActions
import os.kei.ui.page.main.settings.state.SettingsPageViewModel
import os.kei.ui.page.main.settings.state.rememberSettingsBackgroundController
import os.kei.ui.page.main.settings.state.rememberSettingsPageRouteState
import os.kei.ui.page.main.settings.state.rememberSettingsPageUiState
import os.kei.ui.page.main.settings.support.rememberSettingsAppLanguageController
import os.kei.ui.page.main.settings.support.rememberSettingsBatteryOptimizationController
import os.kei.ui.page.main.settings.support.rememberSettingsPermissionKeepAliveController
import os.kei.ui.page.main.widget.chrome.AppLiquidNavigationButton
import os.kei.ui.page.main.widget.chrome.AppPageScaffold
import os.kei.ui.page.main.widget.chrome.ScrollChromeVisibilityController
import os.kei.ui.page.main.widget.motion.AppMotionTokens
import os.kei.ui.page.main.widget.motion.resolvedMotionDuration
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs

@Composable
fun SettingsPage(
    notificationPermissionGranted: Boolean,
    onRequestNotificationPermission: () -> Unit,
    liquidBottomBarEnabled: Boolean,
    onLiquidBottomBarChanged: (Boolean) -> Unit,
    liquidActionBarLayeredStyleEnabled: Boolean,
    onLiquidActionBarLayeredStyleChanged: (Boolean) -> Unit,
    liquidSwitchEnabled: Boolean,
    onLiquidSwitchChanged: (Boolean) -> Unit,
    liquidToastEnabled: Boolean,
    onLiquidToastChanged: (Boolean) -> Unit,
    reduceToastInterruptionEnabled: Boolean,
    onReduceToastInterruptionChanged: (Boolean) -> Unit,
    liquidSheetEnabled: Boolean,
    onLiquidSheetChanged: (Boolean) -> Unit,
    liquidDialogEnabled: Boolean,
    onLiquidDialogChanged: (Boolean) -> Unit,
    transitionAnimationsEnabled: Boolean,
    onTransitionAnimationsChanged: (Boolean) -> Unit,
    predictiveBackAnimationsEnabled: Boolean,
    onPredictiveBackAnimationsChanged: (Boolean) -> Unit,
    searchAutoFocusEnabled: Boolean,
    onSearchAutoFocusChanged: (Boolean) -> Unit,
    gripAwareFloatingDockEnabled: Boolean,
    onGripAwareFloatingDockChanged: (Boolean) -> Unit,
    homeIconHdrEnabled: Boolean,
    onHomeIconHdrChanged: (Boolean) -> Unit,
    homeDynamicFullEffectEnabled: Boolean,
    onHomeDynamicFullEffectChanged: (Boolean) -> Unit,
    preloadingEnabled: Boolean,
    onPreloadingEnabledChanged: (Boolean) -> Unit,
    launcherIconDesign: LauncherIconDesign,
    onLauncherIconDesignChanged: (LauncherIconDesign) -> Unit,
    nonHomeBackgroundEnabled: Boolean,
    onNonHomeBackgroundEnabledChanged: (Boolean) -> Unit,
    nonHomeBackgroundUri: String,
    onNonHomeBackgroundUriChanged: (String) -> Unit,
    nonHomeBackgroundOpacity: Float,
    onNonHomeBackgroundOpacityChanged: (Float) -> Unit,
    superIslandNotificationEnabled: Boolean,
    onSuperIslandNotificationChanged: (Boolean) -> Unit,
    superIslandBypassRestrictionEnabled: Boolean,
    onSuperIslandBypassRestrictionChanged: (Boolean) -> Unit,
    superIslandRestoreDelayMs: Int,
    onSuperIslandRestoreDelayMsChanged: (Int) -> Unit,
    logLevel: AppLogLevel,
    onLogLevelChanged: (AppLogLevel) -> Unit,
    textCopyCapabilityExpanded: Boolean,
    onTextCopyCapabilityExpandedChanged: (Boolean) -> Unit,
    cacheDiagnosticsEnabled: Boolean,
    onCacheDiagnosticsChanged: (Boolean) -> Unit,
    shizukuStatus: String,
    onCheckOrRequestShizuku: () -> Unit,
    shizukuApiUtils: ShizukuApiUtils,
    appThemeMode: AppThemeMode,
    onAppThemeModeChanged: (AppThemeMode) -> Unit,
    onBack: () -> Unit,
    onOpenWebDavSync: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settingsTitle = stringResource(R.string.settings_title)
    val enabledCardColor = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.64f)
    val disabledCardColor = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.50f)
    val scope = rememberCoroutineScope()
    val settingsPageViewModel: SettingsPageViewModel = viewModel()
    val pageSnapshotState by settingsPageViewModel.pageSnapshotState.collectAsStateWithLifecycle()
    val diagnosticsUiState = pageSnapshotState.diagnosticsUiState
    val supportUiState = pageSnapshotState.supportUiState
    val chromeState = pageSnapshotState.chromeState
    val routeState =
        rememberSettingsPageRouteState(
            cacheState = diagnosticsUiState.cacheState,
            logState = diagnosticsUiState.logState,
        )
    val pageUiState =
        rememberSettingsPageUiState(
            chromeState = chromeState,
            actions =
                SettingsPageUiActions(
                    onShowThemeModePopupChange = settingsPageViewModel::updateShowThemeModePopup,
                    onThemePopupAnchorBoundsChange = settingsPageViewModel::updateThemePopupAnchorBounds,
                    onShowLauncherIconDesignPopupChange = settingsPageViewModel::updateShowLauncherIconDesignPopup,
                    onLauncherIconDesignPopupAnchorBoundsChange =
                        settingsPageViewModel::updateLauncherIconDesignPopupAnchorBounds,
                ),
        )
    val backgroundController =
        rememberSettingsBackgroundController(
            settingsPageViewModel = settingsPageViewModel,
            nonHomeBackgroundEnabled = nonHomeBackgroundEnabled,
            onNonHomeBackgroundEnabledChanged = onNonHomeBackgroundEnabledChanged,
            nonHomeBackgroundUri = nonHomeBackgroundUri,
            onNonHomeBackgroundUriChanged = onNonHomeBackgroundUriChanged,
        )
    val appLanguageController = rememberSettingsAppLanguageController(context)
    val batteryOptimizationController = rememberSettingsBatteryOptimizationController(context)
    val permissionKeepAliveController =
        rememberSettingsPermissionKeepAliveController(
            context = context,
            shizukuApiUtils = shizukuApiUtils,
        )
    BindSettingsPageEffects(
        context = context,
        lifecycleOwner = lifecycleOwner,
        settingsPageViewModel = settingsPageViewModel,
        batteryOptimizationController = batteryOptimizationController,
        permissionKeepAliveController = permissionKeepAliveController,
        notificationPermissionGranted = notificationPermissionGranted,
        shizukuStatus = shizukuStatus,
        cacheDiagnosticsEnabled = cacheDiagnosticsEnabled,
        logLevel = logLevel,
        shizukuRefreshToken = chromeState.shizukuRefreshToken,
    )
    val sectionContracts =
        rememberSettingsPageSectionContracts(
            context = context,
            pageUiState = pageUiState,
            permissionKeepAliveState = supportUiState.permissionKeepAliveState,
            batteryOptimizationState = supportUiState.batteryOptimizationState,
            permissionKeepAliveController = permissionKeepAliveController,
            batteryOptimizationController = batteryOptimizationController,
            appLanguageController = appLanguageController,
            notificationPermissionGranted = notificationPermissionGranted,
            preloadingEnabled = preloadingEnabled,
            onPreloadingEnabledChanged = onPreloadingEnabledChanged,
            launcherIconDesign = launcherIconDesign,
            onLauncherIconDesignChanged = onLauncherIconDesignChanged,
            homeIconHdrEnabled = homeIconHdrEnabled,
            onHomeIconHdrChanged = onHomeIconHdrChanged,
            homeDynamicFullEffectEnabled = homeDynamicFullEffectEnabled,
            onHomeDynamicFullEffectChanged = onHomeDynamicFullEffectChanged,
            appThemeMode = appThemeMode,
            onAppThemeModeChanged = onAppThemeModeChanged,
            transitionAnimationsEnabled = transitionAnimationsEnabled,
            onTransitionAnimationsChanged = onTransitionAnimationsChanged,
            predictiveBackAnimationsEnabled = predictiveBackAnimationsEnabled,
            onPredictiveBackAnimationsChanged = onPredictiveBackAnimationsChanged,
            searchAutoFocusEnabled = searchAutoFocusEnabled,
            onSearchAutoFocusChanged = onSearchAutoFocusChanged,
            liquidActionBarLayeredStyleEnabled = liquidActionBarLayeredStyleEnabled,
            onLiquidActionBarLayeredStyleChanged = onLiquidActionBarLayeredStyleChanged,
            liquidSwitchEnabled = liquidSwitchEnabled,
            onLiquidSwitchChanged = onLiquidSwitchChanged,
            liquidToastEnabled = liquidToastEnabled,
            onLiquidToastChanged = onLiquidToastChanged,
            reduceToastInterruptionEnabled = reduceToastInterruptionEnabled,
            onReduceToastInterruptionChanged = onReduceToastInterruptionChanged,
            liquidSheetEnabled = liquidSheetEnabled,
            onLiquidSheetChanged = onLiquidSheetChanged,
            liquidDialogEnabled = liquidDialogEnabled,
            onLiquidDialogChanged = onLiquidDialogChanged,
            liquidBottomBarEnabled = liquidBottomBarEnabled,
            onLiquidBottomBarChanged = onLiquidBottomBarChanged,
            gripAwareFloatingDockEnabled = gripAwareFloatingDockEnabled,
            onGripAwareFloatingDockChanged = onGripAwareFloatingDockChanged,
            superIslandNotificationEnabled = superIslandNotificationEnabled,
            onSuperIslandNotificationChanged = onSuperIslandNotificationChanged,
            superIslandBypassRestrictionEnabled = superIslandBypassRestrictionEnabled,
            onSuperIslandBypassRestrictionChanged = onSuperIslandBypassRestrictionChanged,
            superIslandRestoreDelayMs = superIslandRestoreDelayMs,
            onSuperIslandRestoreDelayMsChanged = onSuperIslandRestoreDelayMsChanged,
            textCopyCapabilityExpanded = textCopyCapabilityExpanded,
            onTextCopyCapabilityExpandedChanged = onTextCopyCapabilityExpandedChanged,
            onRequestNotificationPermission = onRequestNotificationPermission,
            onCheckOrRequestShizuku = {
                settingsPageViewModel.requestShizukuRefresh()
                onCheckOrRequestShizuku()
            },
        )

    BindSettingsLogExportAction(
        context = context,
        settingsPageViewModel = settingsPageViewModel,
    )

    val scrollBehavior = MiuixScrollBehavior()
    val categories = remember { SettingsCategory.entries.toList() }
    val searchExpanded = chromeState.searchExpanded
    val searchQuery = chromeState.searchQuery
    val bottomBarVisible = chromeState.bottomBarVisible
    val sliderInteractionActive = chromeState.sliderInteractionActive
    val pagerState =
        rememberMainLoadedPagerState(
            initialPage = chromeState.selectedCategoryIndex.coerceIn(0, categories.lastIndex),
            pageCount = categories.size,
        )
    val accessListState = rememberLazyListState()
    val appearanceListState = rememberLazyListState()
    val effectsListState = rememberLazyListState()
    val dataListState = rememberLazyListState()
    val categoryListStates =
        remember(accessListState, appearanceListState, effectsListState, dataListState) {
            SettingsCategoryListStates(
                access = accessListState,
                appearance = appearanceListState,
                effects = effectsListState,
                data = dataListState,
            )
        }
    val searchListState = rememberLazyListState()
    val topBarBackdrop = rememberLayerBackdrop()
    val bottomBarBackdrop = rememberLayerBackdrop()
    val navigationBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val farJumpAlpha = remember { Animatable(1f) }
    val tabJumpCoordinator = remember { SettingsTabJumpCoordinator() }
    val density = LocalDensity.current
    val bottomBarVisibilityThresholdPx = remember(density) { with(density) { 22.dp.toPx() } }
    val bottomBarVisibilityController =
        remember(bottomBarVisibilityThresholdPx) {
            ScrollChromeVisibilityController(bottomBarVisibilityThresholdPx)
        }
    val activeCategoryProvider =
        remember(categories, pagerState) {
            {
                categories[
                    settingsActiveCategoryIndex(
                        scrolling = pagerState.isScrollInProgress,
                        targetPage = pagerState.targetPage,
                        settledPage = pagerState.settledPage,
                        lastIndex = categories.lastIndex,
                    ),
                ]
            }
        }
    val activePageListStateProvider =
        remember(activeCategoryProvider, categoryListStates) {
            { categoryListStates.forCategory(activeCategoryProvider()) }
        }
    val currentBottomBarVisible = rememberUpdatedState(bottomBarVisible)
    val bottomBarNestedScrollConnection =
        remember(bottomBarVisibilityController, activeCategoryProvider, activePageListStateProvider) {
            object : NestedScrollConnection {
                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    val activeCategory = activeCategoryProvider()
                    if (activeCategory.keepsChromeVisibleOnBounds()) {
                        bottomBarVisibilityController.showNow(
                            visible = currentBottomBarVisible.value,
                            onVisibleChange = settingsPageViewModel::updateBottomBarVisible,
                        )
                        return Offset.Zero
                    }
                    val currentListState = activePageListStateProvider()
                    bottomBarVisibilityController.updateWithinScrollBounds(
                        deltaY = consumed.y,
                        visible = currentBottomBarVisible.value,
                        canScrollBackward = currentListState.canScrollBackward,
                        canScrollForward = currentListState.canScrollForward,
                        onVisibleChange = settingsPageViewModel::updateBottomBarVisible,
                    )
                    return Offset.Zero
                }
            }
        }
    val settingsSearchPlaceholder = stringResource(R.string.settings_search_placeholder)
    val searchContentDescription = settingsSearchPlaceholder
    val trimmedSearchQuery = searchQuery.trim()
    val searchActive = trimmedSearchQuery.isNotEmpty()
    val matchingSearchTargets = pageSnapshotState.searchUiState.matchingTargets
    val selectSettingsCategoryAction =
        remember(
            categories,
            pagerState,
            transitionAnimationsEnabled,
            farJumpAlpha,
            scope,
            settingsPageViewModel,
            tabJumpCoordinator,
        ) {
            { index: Int ->
                val safeIndex = index.coerceIn(0, categories.lastIndex)
                val stablePageIndex =
                    settingsActiveCategoryIndex(
                        scrolling = pagerState.isScrollInProgress,
                        targetPage = pagerState.targetPage,
                        settledPage = pagerState.settledPage,
                        lastIndex = categories.lastIndex,
                    )
                if (safeIndex != stablePageIndex) {
                    settingsPageViewModel.updateSelectedCategoryIndex(safeIndex)
                    tabJumpCoordinator.launch {
                        scope.launch {
                            val distance = abs(safeIndex - stablePageIndex)
                            if (distance > 1) {
                                farJumpAlpha.snapTo(1f)
                                farJumpAlpha.animateTo(
                                    targetValue = 0.92f,
                                    animationSpec =
                                        tween(
                                            durationMillis =
                                                resolvedMotionDuration(
                                                    AppMotionTokens.farJumpDimMs,
                                                    transitionAnimationsEnabled,
                                                ),
                                        ),
                                )
                            }
                            pagerState.animateToPage(
                                target = safeIndex,
                                animationsEnabled = transitionAnimationsEnabled,
                                durationMillis = settingsPagerSwitchDurationMillis(distance),
                            )
                            if (distance > 1) {
                                farJumpAlpha.animateTo(
                                    targetValue = 1f,
                                    animationSpec =
                                        tween(
                                            durationMillis =
                                                resolvedMotionDuration(
                                                    AppMotionTokens.farJumpRestoreMs,
                                                    transitionAnimationsEnabled,
                                                ),
                                        ),
                                )
                            }
                        }
                    }
                }
            }
        }

    LaunchedEffect(pagerState.settledPage) {
        settingsPageViewModel.updateSliderInteractionActive(false)
        bottomBarVisibilityController.showNow(
            visible = bottomBarVisible,
            onVisibleChange = settingsPageViewModel::updateBottomBarVisible,
        )
        if (chromeState.selectedCategoryIndex != pagerState.settledPage) {
            settingsPageViewModel.updateSelectedCategoryIndex(pagerState.settledPage)
        }
    }
    LaunchedEffect(activeCategoryProvider, activePageListStateProvider, bottomBarVisibilityController) {
        snapshotFlow {
            val category = activeCategoryProvider()
            val listState = activePageListStateProvider()
            SettingsActiveChromeBoundsState(
                category = category,
                canScrollBackward = listState.canScrollBackward,
                canScrollForward = listState.canScrollForward,
            )
        }.distinctUntilChanged()
            .collect { boundsState ->
                if (boundsState.category.keepsChromeVisibleOnBounds()) {
                    bottomBarVisibilityController.showNow(
                        visible = currentBottomBarVisible.value,
                        onVisibleChange = settingsPageViewModel::updateBottomBarVisible,
                    )
                } else {
                    bottomBarVisibilityController.showForStaticContent(
                        visible = currentBottomBarVisible.value,
                        canScrollBackward = boundsState.canScrollBackward,
                        canScrollForward = boundsState.canScrollForward,
                        onVisibleChange = settingsPageViewModel::updateBottomBarVisible,
                    )
                }
            }
    }

    BackHandler(enabled = searchExpanded) {
        settingsPageViewModel.updateSearchExpanded(false)
    }

    LaunchedEffect(trimmedSearchQuery) {
        if (trimmedSearchQuery.isNotEmpty()) {
            searchListState.scrollToItem(0)
        }
    }
    val settingsSearchCardInput =
        SettingsSearchCardRenderInput(
            context = context,
            settingsPageViewModel = settingsPageViewModel,
            chromeState = chromeState,
            sectionContracts = sectionContracts,
            backgroundController = backgroundController,
            cacheState = routeState.cacheState,
            logState = routeState.logState,
            cacheDiagnosticsEnabled = cacheDiagnosticsEnabled,
            onCacheDiagnosticsChanged = onCacheDiagnosticsChanged,
            logLevel = logLevel,
            onLogLevelChanged = onLogLevelChanged,
            nonHomeBackgroundEnabled = nonHomeBackgroundEnabled,
            onNonHomeBackgroundEnabledChanged = onNonHomeBackgroundEnabledChanged,
            nonHomeBackgroundUri = nonHomeBackgroundUri,
            nonHomeBackgroundOpacity = nonHomeBackgroundOpacity,
            onNonHomeBackgroundOpacityChanged = onNonHomeBackgroundOpacityChanged,
            enabledCardColor = enabledCardColor,
            disabledCardColor = disabledCardColor,
            onSliderInteractionChanged = settingsPageViewModel::updateSliderInteractionActive,
            onNavigateToWebDavSync = onOpenWebDavSync,
        )

    AppPageScaffold(
        title = settingsTitle,
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(bottomBarNestedScrollConnection),
        scrollBehavior = scrollBehavior,
        topBarColor = androidx.compose.ui.graphics.Color.Transparent,
        titleBackdrop = topBarBackdrop,
        onTitleClick = {
            bottomBarVisibilityController.showNow(
                visible = currentBottomBarVisible.value,
                onVisibleChange = settingsPageViewModel::updateBottomBarVisible,
            )
        },
        navigationIcon = {
            AppLiquidNavigationButton(
                icon = appLucideBackIcon(),
                contentDescription = settingsTitle,
                onClick = onBack,
                backdrop = topBarBackdrop,
            )
        },
        bottomBar = {
            SettingsBottomChrome(
                visible = bottomBarVisible,
                navigationBarBottom = navigationBarBottom,
                categories = categories,
                selectedPage = pagerState.targetPage.coerceIn(0, categories.lastIndex),
                selectedPagePosition = null,
                selectedPagePositionProvider = {
                    if (!searchExpanded && pagerState.isScrollInProgress) {
                        pagerState.pagePosition.coerceIn(
                            0f,
                            categories.lastIndex.coerceAtLeast(0).toFloat(),
                        )
                    } else {
                        null
                    }
                },
                selectedPageProvider = { pagerState.targetPage },
                searchExpanded = searchExpanded,
                searchQuery = searchQuery,
                onSearchQueryChange = settingsPageViewModel::updateSearchQuery,
                onSearchExpandedChange = settingsPageViewModel::updateSearchExpanded,
                searchIcon = appLucideSearchIcon(),
                searchContentDescription = searchContentDescription,
                searchPlaceholder = settingsSearchPlaceholder,
                backdrop = bottomBarBackdrop,
                isLiquidEffectEnabled = liquidBottomBarEnabled,
                onSelectCategory = selectSettingsCategoryAction,
                onExpandDock = {
                    bottomBarVisibilityController.showNow(
                        visible = currentBottomBarVisible.value,
                        onVisibleChange = settingsPageViewModel::updateBottomBarVisible,
                    )
                },
            )
        },
    ) { innerPadding ->
        // Keep both pager and search content mounted to avoid blank flash on
        // search toggle, matching the pattern used by GitHub, MCP, OS, and BA
        // Catalog pages. Control visibility and backdrop capture instead.
        Box(Modifier.fillMaxSize()) {
            SettingsCategoryPagerContent(
                innerPadding = innerPadding,
                pagerState = pagerState,
                categories = categories,
                listStates = categoryListStates,
                settingsSearchCardInput = settingsSearchCardInput,
                scrollNestedConnection = scrollBehavior.nestedScrollConnection,
                topBarBackdrop = topBarBackdrop,
                bottomBarBackdrop = bottomBarBackdrop,
                sliderInteractionActive = sliderInteractionActive || searchExpanded,
                transitionAnimationsEnabled = transitionAnimationsEnabled && !searchActive,
                farJumpAlphaProvider = { farJumpAlpha.value },
                backdropEnabled = !searchActive,
                modifier = Modifier.alpha(if (searchActive) 0f else 1f),
            )
            if (searchActive) {
                SettingsSearchContent(
                    innerPadding = innerPadding,
                    searchListState = searchListState,
                    matchingSearchTargets = matchingSearchTargets,
                    settingsSearchCardInput = settingsSearchCardInput,
                    scrollNestedConnection = scrollBehavior.nestedScrollConnection,
                    topBarBackdrop = topBarBackdrop,
                    bottomBarBackdrop = bottomBarBackdrop,
                    sliderInteractionActive = sliderInteractionActive,
                )
            }
        }
    }
}

private data class SettingsActiveChromeBoundsState(
    val category: SettingsCategory,
    val canScrollBackward: Boolean,
    val canScrollForward: Boolean,
)

internal fun settingsActiveCategoryIndex(
    scrolling: Boolean,
    targetPage: Int,
    settledPage: Int,
    lastIndex: Int,
): Int {
    val candidate =
        if (scrolling) {
            targetPage
        } else {
            settledPage
        }
    return candidate.coerceIn(0, lastIndex.coerceAtLeast(0))
}
