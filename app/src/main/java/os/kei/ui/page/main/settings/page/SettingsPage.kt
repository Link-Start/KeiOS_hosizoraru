package os.kei.ui.page.main.settings.page

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import os.kei.R
import os.kei.core.prefs.AppThemeMode
import os.kei.core.system.ShizukuApiUtils
import os.kei.ui.page.main.host.pager.MainLoadedPager
import os.kei.ui.page.main.host.pager.rememberMainLoadedPagerState
import os.kei.ui.page.main.os.appLucideBackIcon
import os.kei.ui.page.main.os.appLucideSearchIcon
import os.kei.ui.page.main.settings.state.SettingsPageViewModel
import os.kei.ui.page.main.settings.state.rememberSettingsBackgroundController
import os.kei.ui.page.main.settings.state.rememberSettingsPageRouteState
import os.kei.ui.page.main.settings.state.rememberSettingsPageUiState
import os.kei.ui.page.main.settings.support.rememberSettingsAppLanguageController
import os.kei.ui.page.main.settings.support.rememberSettingsBatteryOptimizationController
import os.kei.ui.page.main.settings.support.rememberSettingsPermissionKeepAliveController
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.chrome.AppLiquidNavigationButton
import os.kei.ui.page.main.widget.chrome.AppPageLazyColumn
import os.kei.ui.page.main.widget.chrome.AppPageScaffold
import os.kei.ui.page.main.widget.chrome.ScrollChromeVisibilityController
import os.kei.ui.page.main.widget.chrome.appPageBottomPaddingWithFloatingOverlay
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.motion.AppMotionTokens
import os.kei.ui.page.main.widget.motion.resolvedMotionDuration
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs

@Composable
fun SettingsPage(
    notificationPermissionGranted: Boolean,
    onRequestNotificationPermission: () -> Unit,
    liquidBottomBarEnabled: Boolean,
    onLiquidBottomBarChanged: (Boolean) -> Unit,
    mainPagerMiuixModeEnabled: Boolean,
    onMainPagerMiuixModeChanged: (Boolean) -> Unit,
    liquidActionBarLayeredStyleEnabled: Boolean,
    onLiquidActionBarLayeredStyleChanged: (Boolean) -> Unit,
    liquidSwitchEnabled: Boolean,
    onLiquidSwitchChanged: (Boolean) -> Unit,
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
    logDebugEnabled: Boolean,
    onLogDebugChanged: (Boolean) -> Unit,
    textCopyCapabilityExpanded: Boolean,
    onTextCopyCapabilityExpandedChanged: (Boolean) -> Unit,
    cacheDiagnosticsEnabled: Boolean,
    onCacheDiagnosticsChanged: (Boolean) -> Unit,
    shizukuStatus: String,
    onCheckOrRequestShizuku: () -> Unit,
    shizukuApiUtils: ShizukuApiUtils,
    appThemeMode: AppThemeMode,
    onAppThemeModeChanged: (AppThemeMode) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settingsTitle = stringResource(R.string.settings_title)
    val enabledCardColor = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.64f)
    val disabledCardColor = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.50f)
    val scope = rememberCoroutineScope()
    var shizukuRefreshToken by remember { mutableIntStateOf(0) }
    val settingsPageViewModel: SettingsPageViewModel = viewModel()
    val cacheState by settingsPageViewModel.cacheState.collectAsState()
    val logState by settingsPageViewModel.logState.collectAsState()
    val routeState = rememberSettingsPageRouteState(
        cacheState = cacheState,
        logState = logState
    )

    val pageUiState = rememberSettingsPageUiState()
    val backgroundController = rememberSettingsBackgroundController(
        nonHomeBackgroundEnabled = nonHomeBackgroundEnabled,
        onNonHomeBackgroundEnabledChanged = onNonHomeBackgroundEnabledChanged,
        nonHomeBackgroundUri = nonHomeBackgroundUri,
        onNonHomeBackgroundUriChanged = onNonHomeBackgroundUriChanged
    )
    val appLanguageController = rememberSettingsAppLanguageController(context)
    val batteryOptimizationController = rememberSettingsBatteryOptimizationController(context)
    val permissionKeepAliveController = rememberSettingsPermissionKeepAliveController(
        context = context,
        shizukuApiUtils = shizukuApiUtils
    )
    BindSettingsPageEffects(
        context = context,
        lifecycleOwner = lifecycleOwner,
        scope = scope,
        settingsPageViewModel = settingsPageViewModel,
        batteryOptimizationController = batteryOptimizationController,
        permissionKeepAliveController = permissionKeepAliveController,
        notificationPermissionGranted = notificationPermissionGranted,
        shizukuStatus = shizukuStatus,
        cacheDiagnosticsEnabled = cacheDiagnosticsEnabled,
        logDebugEnabled = logDebugEnabled,
        shizukuRefreshToken = shizukuRefreshToken
    )
    val sectionContracts = rememberSettingsPageSectionContracts(
        context = context,
        pageUiState = pageUiState,
        permissionKeepAliveController = permissionKeepAliveController,
        batteryOptimizationController = batteryOptimizationController,
        appLanguageController = appLanguageController,
        notificationPermissionGranted = notificationPermissionGranted,
        preloadingEnabled = preloadingEnabled,
        onPreloadingEnabledChanged = onPreloadingEnabledChanged,
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
        liquidBottomBarEnabled = liquidBottomBarEnabled,
        onLiquidBottomBarChanged = onLiquidBottomBarChanged,
        mainPagerMiuixModeEnabled = mainPagerMiuixModeEnabled,
        onMainPagerMiuixModeChanged = onMainPagerMiuixModeChanged,
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
            shizukuRefreshToken += 1
            onCheckOrRequestShizuku()
        }
    )

    BindSettingsLogExportAction(
        context = context,
        scope = scope,
        settingsPageViewModel = settingsPageViewModel,
        pendingExportFileName = routeState.logState.pendingExportFileName
    )

    val scrollBehavior = MiuixScrollBehavior()
    val categories = remember { SettingsCategory.entries.toList() }
    var selectedCategoryIndex by rememberSaveable { mutableIntStateOf(0) }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val pagerState = rememberMainLoadedPagerState(
        initialPage = selectedCategoryIndex.coerceIn(0, categories.lastIndex),
        pageCount = categories.size
    )
    val accessListState = rememberLazyListState()
    val appearanceListState = rememberLazyListState()
    val notifyListState = rememberLazyListState()
    val dataListState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    var sliderInteractionActive by remember { mutableStateOf(false) }
    val topBarBackdrop = rememberLayerBackdrop()
    val bottomBarBackdrop = rememberLayerBackdrop()
    val navigationBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var showBottomBar by remember { mutableStateOf(true) }
    val farJumpAlpha = remember { Animatable(1f) }
    var tabJumpJob by remember { mutableStateOf<Job?>(null) }
    val density = LocalDensity.current
    val bottomBarVisibilityThresholdPx = remember(density) { with(density) { 22.dp.toPx() } }
    val bottomBarVisibilityController = remember(bottomBarVisibilityThresholdPx) {
        ScrollChromeVisibilityController(bottomBarVisibilityThresholdPx)
    }
    val activeCategoryIndex = if (pagerState.isScrollInProgress) {
        pagerState.targetPage
    } else {
        pagerState.settledPage
    }.coerceIn(0, categories.lastIndex)
    val activeCategory = categories[activeCategoryIndex]
    val activePageListState = when (activeCategory) {
        SettingsCategory.Access -> accessListState
        SettingsCategory.Appearance -> appearanceListState
        SettingsCategory.Notify -> notifyListState
        SettingsCategory.Data -> dataListState
    }
    val currentActivePageListState = rememberUpdatedState(activePageListState)
    val currentActiveCategory = rememberUpdatedState(activeCategory)
    val bottomBarNestedScrollConnection = remember(bottomBarVisibilityController) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (currentActiveCategory.value.keepsChromeVisibleOnBounds()) {
                    bottomBarVisibilityController.showNow(showBottomBar) { showBottomBar = it }
                    return Offset.Zero
                }
                val currentListState = currentActivePageListState.value
                bottomBarVisibilityController.updateWithinScrollBounds(
                    deltaY = consumed.y,
                    visible = showBottomBar,
                    canScrollBackward = currentListState.canScrollBackward,
                    canScrollForward = currentListState.canScrollForward
                ) { showBottomBar = it }
                return Offset.Zero
            }
        }
    }
    val settingsSearchPlaceholder = stringResource(R.string.settings_search_placeholder)
    val searchContentDescription = settingsSearchPlaceholder
    val searchTargets = rememberSettingsSearchTargets()
    val trimmedSearchQuery = searchQuery.trim()
    val searchActive = trimmedSearchQuery.isNotEmpty()
    val matchingSearchTargets = searchTargets.filter { it.matches(trimmedSearchQuery) }
    val selectSettingsCategoryAction = remember(
        categories,
        pagerState,
        transitionAnimationsEnabled,
        farJumpAlpha,
        scope
    ) {
        { index: Int ->
            val safeIndex = index.coerceIn(0, categories.lastIndex)
            val stablePageIndex = if (pagerState.isScrollInProgress) {
                pagerState.targetPage
            } else {
                pagerState.settledPage
            }
            if (safeIndex != stablePageIndex) {
                selectedCategoryIndex = safeIndex
                tabJumpJob?.cancel()
                tabJumpJob = scope.launch {
                    val distance = abs(safeIndex - stablePageIndex)
                    if (distance > 1) {
                        farJumpAlpha.snapTo(1f)
                        farJumpAlpha.animateTo(
                            targetValue = 0.92f,
                            animationSpec = tween(
                                durationMillis = resolvedMotionDuration(
                                    AppMotionTokens.farJumpDimMs,
                                    transitionAnimationsEnabled
                                )
                            )
                        )
                    }
                    pagerState.animateToPage(
                        target = safeIndex,
                        animationsEnabled = transitionAnimationsEnabled,
                        durationMillis = settingsPagerSwitchDurationMillis(distance)
                    )
                    if (distance > 1) {
                        farJumpAlpha.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = resolvedMotionDuration(
                                    AppMotionTokens.farJumpRestoreMs,
                                    transitionAnimationsEnabled
                                )
                            )
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(pagerState.settledPage) {
        sliderInteractionActive = false
        if (selectedCategoryIndex != pagerState.settledPage) {
            selectedCategoryIndex = pagerState.settledPage
        }
    }

    BackHandler(enabled = searchExpanded) {
        searchExpanded = false
    }

    LaunchedEffect(trimmedSearchQuery) {
        if (trimmedSearchQuery.isNotEmpty()) {
            searchListState.scrollToItem(0)
        }
    }
    val settingsSearchCardInput = SettingsSearchCardRenderInput(
        context = context,
        scope = scope,
        settingsPageViewModel = settingsPageViewModel,
        sectionContracts = sectionContracts,
        backgroundController = backgroundController,
        cacheState = routeState.cacheState,
        logState = routeState.logState,
        cacheDiagnosticsEnabled = cacheDiagnosticsEnabled,
        onCacheDiagnosticsChanged = onCacheDiagnosticsChanged,
        logDebugEnabled = logDebugEnabled,
        onLogDebugChanged = onLogDebugChanged,
        nonHomeBackgroundEnabled = nonHomeBackgroundEnabled,
        onNonHomeBackgroundEnabledChanged = onNonHomeBackgroundEnabledChanged,
        nonHomeBackgroundUri = nonHomeBackgroundUri,
        nonHomeBackgroundOpacity = nonHomeBackgroundOpacity,
        onNonHomeBackgroundOpacityChanged = onNonHomeBackgroundOpacityChanged,
        enabledCardColor = enabledCardColor,
        disabledCardColor = disabledCardColor,
        onSliderInteractionChanged = { active -> sliderInteractionActive = active }
    )

    AppPageScaffold(
        title = settingsTitle,
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(bottomBarNestedScrollConnection),
        scrollBehavior = scrollBehavior,
        topBarColor = Color.Transparent,
        titleBackdrop = topBarBackdrop,
        navigationIcon = {
            AppLiquidNavigationButton(
                icon = appLucideBackIcon(),
                contentDescription = settingsTitle,
                onClick = onBack,
                backdrop = topBarBackdrop
            )
        },
        bottomBar = {
            SettingsBottomChrome(
                visible = showBottomBar,
                navigationBarBottom = navigationBarBottom,
                categories = categories,
                selectedPage = pagerState.targetPage.coerceIn(0, categories.lastIndex),
                selectedPagePosition = if (!searchExpanded && pagerState.isScrollInProgress) {
                    pagerState.pagePosition.coerceIn(
                        0f,
                        categories.lastIndex.coerceAtLeast(0).toFloat()
                    )
                } else {
                    null
                },
                selectedPageProvider = { pagerState.targetPage },
                searchExpanded = searchExpanded,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onSearchExpandedChange = { searchExpanded = it },
                searchIcon = appLucideSearchIcon(),
                searchContentDescription = searchContentDescription,
                searchPlaceholder = settingsSearchPlaceholder,
                backdrop = bottomBarBackdrop,
                isLiquidEffectEnabled = liquidBottomBarEnabled,
                onSelectCategory = selectSettingsCategoryAction
            )
        }
    ) { innerPadding ->
        if (searchActive) {
            AppPageLazyColumn(
                innerPadding = innerPadding,
                state = searchListState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .graphicsLayer { alpha = 1f }
                    .layerBackdrop(topBarBackdrop)
                    .layerBackdrop(bottomBarBackdrop),
                bottomExtra = appPageBottomPaddingWithFloatingOverlay(
                    AppChromeTokens.floatingBottomBarOuterHeight,
                ),
                sectionSpacing = 12.dp,
                userScrollEnabled = !sliderInteractionActive,
            ) {
                if (matchingSearchTargets.isEmpty()) {
                    item(key = "settings_search_empty") {
                        Text(
                            text = stringResource(R.string.common_no_matched_results),
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                            fontSize = AppTypographyTokens.Body.fontSize,
                            lineHeight = AppTypographyTokens.Body.lineHeight,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AppChromeTokens.pageHorizontalPadding),
                        )
                    }
                } else {
                    matchingSearchTargets.forEach { target ->
                        settingsCardItem(target.card, settingsSearchCardInput)
                    }
                }
            }
        } else {
            MainLoadedPager(
                state = pagerState,
                userScrollEnabled = !sliderInteractionActive && !searchExpanded,
                animationsEnabled = transitionAnimationsEnabled,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = farJumpAlpha.value }
                    .layerBackdrop(topBarBackdrop)
                    .layerBackdrop(bottomBarBackdrop)
            ) { pageIndex ->
            val renderHeavyContent = pageIndex == pagerState.currentPage ||
                    pageIndex == pagerState.settledPage ||
                    pageIndex == pagerState.targetPage ||
                    abs(pageIndex - pagerState.pagePosition) <= 1.05f
            if (renderHeavyContent) {
                val category = categories[pageIndex]
                val pageListState = when (category) {
                    SettingsCategory.Access -> accessListState
                    SettingsCategory.Appearance -> appearanceListState
                    SettingsCategory.Notify -> notifyListState
                    SettingsCategory.Data -> dataListState
                }
                val pageNestedScrollConnection = remember(pageListState, scrollBehavior) {
                    settingsChromeNestedScrollConnection(
                        listState = pageListState,
                        delegate = scrollBehavior.nestedScrollConnection
                    )
                }
                AppPageLazyColumn(
                    innerPadding = innerPadding,
                    state = pageListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(pageNestedScrollConnection),
                    bottomExtra = appPageBottomPaddingWithFloatingOverlay(
                        AppChromeTokens.floatingBottomBarOuterHeight
                    ),
                    sectionSpacing = 12.dp,
                    userScrollEnabled = !sliderInteractionActive
                ) {
                    settingsCategoryItems(category, settingsSearchCardInput)
            }
        }
    }
}

}

}
