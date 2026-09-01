@file:Suppress("FunctionName")

package os.kei.ui.page.main.ba

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch
import os.kei.R
import os.kei.ui.page.main.ba.support.formatBaDateTimeNoYearInTimeZone
import os.kei.ui.page.main.ba.support.serverRefreshTimeZone
import os.kei.core.ui.effect.rememberAppTopBarColor
import os.kei.ui.page.main.back.KeiOSActivityRootBackHandler
import os.kei.ui.page.main.common.applicationViewModel
import os.kei.ui.page.main.host.pager.MainLoadedPager
import os.kei.ui.page.main.host.pager.rememberMainLoadedPagerState
import os.kei.ui.page.main.os.appLucideBackIcon
import os.kei.ui.page.main.os.appLucideSearchIcon
import os.kei.ui.page.main.widget.chrome.AppLiquidNavigationButton
import os.kei.ui.page.main.widget.chrome.AppPageScaffold
import os.kei.ui.page.main.widget.chrome.TabbedPageBottomChrome
import os.kei.ui.page.main.widget.chrome.TabbedPageCategory
import os.kei.ui.page.main.widget.chrome.appPageColumnCount
import os.kei.ui.page.main.widget.chrome.appPageContentMaxWidthFor
import os.kei.ui.page.main.widget.chrome.appManagedPageBackgroundActive
import os.kei.ui.page.main.widget.isAppInDarkTheme
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
import os.kei.ui.page.main.widget.status.AppStatusColors
import os.kei.ui.testing.KeiOsTestTags
import os.kei.ui.testing.pageRootTestTag
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.composables.icons.lucide.R as LucideR

/**
 * The two halves of one screen.
 *
 * The activity calendar and the banner list were two routes reached from two dock buttons, and they had
 * almost nothing of their own: the same view model, the same server picker, the same sync-time strip, the
 * same action bar, the same data-settings sheet. What differed was the entry list and which of the two
 * refresh handlers the toolbar called. Two dock slots for that is a poor trade — the BA dock only has room
 * for four, and this hands one back.
 */
internal enum class BaCalendarPoolTab(
    override val iconRes: Int,
    override val labelRes: Int,
) : TabbedPageCategory {
    Calendar(LucideR.drawable.lucide_ic_calendar, R.string.ba_calendar_tab),
    Pool(LucideR.drawable.lucide_ic_mail, R.string.ba_pool_tab),
}

@Composable
internal fun BaCalendarPoolPage(
    targetServerSelection: BaCalendarPoolInitialServerSelection,
    initialTab: BaCalendarPoolTab,
    onClose: () -> Unit,
    onOpenGuide: () -> Unit,
) {
    KeiOSActivityRootBackHandler(
        needsInterception = false,
        onBack = onClose,
    )

    val context = LocalContext.current
    val pageScope = rememberCoroutineScope()
    val calendarPoolViewModel: BaCalendarPoolViewModel = applicationViewModel(create = ::BaCalendarPoolViewModel)
    val settingsUiState by calendarPoolViewModel.settingsUiState.collectAsStateWithLifecycle()
    val snapshot = settingsUiState.snapshot
    val chromeUiState by calendarPoolViewModel.chromeUiState.collectAsStateWithLifecycle()
    val calendarUiState by calendarPoolViewModel.calendarUiState.collectAsStateWithLifecycle()
    val poolUiState by calendarPoolViewModel.poolUiState.collectAsStateWithLifecycle()
    val tabs = remember { BaCalendarPoolTab.entries.toList() }
    val pagerState =
        rememberMainLoadedPagerState(
            initialPage = tabs.indexOf(initialTab).coerceAtLeast(0),
            pageCount = tabs.size,
            pageKeys = tabs.map { tab -> tab.name },
        )
    // Two columns on a tablet or an unfolded fold, and then there is no tab to be on: both lists are
    // visible, so the bar that switches between them would be a control for something already on screen.
    val columnCount = appPageColumnCount()
    val bothColumns = columnCount >= 2
    val activeTab = tabs[pagerState.targetPage.coerceIn(tabs.indices)]
    val serverOptions =
        listOf(
            stringResource(R.string.ba_server_cn),
            stringResource(R.string.ba_server_global),
            stringResource(R.string.ba_server_jp),
        )
    val serverIndex = chromeUiState.serverIndex
    val hydrationReady = settingsUiState.loaded
    val calendarListState = rememberLazyListState()
    val poolListState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val pageBackdrop = rememberLayerBackdrop()
    val bottomBarBackdrop = rememberLayerBackdrop()
    val topBarColor = rememberAppTopBarColor(enableBackdropEffects = true)
    val accent = MiuixTheme.colorScheme.primary
    val countdownBlue = AppStatusColors.Refreshing
    val navigationBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val transitionAnimationsEnabled = LocalTransitionAnimationsEnabled.current
    val serverTimeZone = serverRefreshTimeZone(serverIndex)

    val calendarBusy = calendarUiState.loading || calendarUiState.refreshing
    val poolBusy = poolUiState.loading || poolUiState.refreshing
    val refreshing =
        if (bothColumns) {
            calendarBusy || poolBusy
        } else {
            when (activeTab) {
                BaCalendarPoolTab.Calendar -> calendarBusy
                BaCalendarPoolTab.Pool -> poolBusy
            }
        }
    val lastSyncMs =
        if (bothColumns) {
            // The more stale of the two, because one strip is answering "is what I am looking at fresh"
            // for both lists and the honest answer is the older one. Zero means one has never synced,
            // which reads as "not synced" -- also honest while that half is empty.
            listOf(calendarUiState.lastSyncMs, poolUiState.lastSyncMs).min()
        } else {
            when (activeTab) {
                BaCalendarPoolTab.Calendar -> calendarUiState.lastSyncMs
                BaCalendarPoolTab.Pool -> poolUiState.lastSyncMs
            }
        }
    val syncText =
        when {
            refreshing -> stringResource(R.string.ba_syncing)
            lastSyncMs > 0L -> formatBaDateTimeNoYearInTimeZone(lastSyncMs, serverTimeZone)
            else -> stringResource(R.string.ba_state_not_synced)
        }

    DisposableEffect(calendarPoolViewModel) {
        onDispose { calendarPoolViewModel.clearServerPopup() }
    }

    LaunchedEffect(targetServerSelection.token, calendarPoolViewModel) {
        targetServerSelection.serverIndex?.let(calendarPoolViewModel::selectServer)
    }

    // Per tab, not per page: the badge on the dock counts both, and reading one half must not clear the
    // other's unread mark for a list the teacher has not looked at yet.
    LaunchedEffect(serverIndex, activeTab, bothColumns, calendarPoolViewModel) {
        val readKinds =
            when {
                // Both lists are on screen, so both have been seen.
                bothColumns -> BaCalendarPoolUnreadKind.entries
                activeTab == BaCalendarPoolTab.Calendar -> listOf(BaCalendarPoolUnreadKind.Calendar)
                else -> listOf(BaCalendarPoolUnreadKind.Pool)
            }
        readKinds.forEach { kind ->
            calendarPoolViewModel.markUnreadRead(kind = kind, serverIndex = serverIndex)
        }
    }

    // Only the tab on screen syncs. Both used to be their own route, so each fetched on entry; firing both
    // here would double the work on open for a list the teacher may never swipe to.
    LaunchedEffect(
        serverIndex,
        chromeUiState.calendarReloadSignal,
        snapshot.calendarRefreshIntervalHours,
        hydrationReady,
        activeTab,
        bothColumns,
    ) {
        calendarPoolViewModel.syncCalendar(
            isPageActive = bothColumns || activeTab == BaCalendarPoolTab.Calendar,
            serverIndex = serverIndex,
            reloadSignal = chromeUiState.calendarReloadSignal,
            calendarRefreshIntervalHours = snapshot.calendarRefreshIntervalHours,
            hydrationReady = hydrationReady,
        )
    }
    LaunchedEffect(
        serverIndex,
        chromeUiState.poolReloadSignal,
        snapshot.calendarRefreshIntervalHours,
        hydrationReady,
        activeTab,
        bothColumns,
    ) {
        calendarPoolViewModel.syncPool(
            isPageActive = bothColumns || activeTab == BaCalendarPoolTab.Pool,
            serverIndex = serverIndex,
            reloadSignal = chromeUiState.poolReloadSignal,
            calendarRefreshIntervalHours = snapshot.calendarRefreshIntervalHours,
            hydrationReady = hydrationReady,
        )
    }

    val refreshIconRotation =
        if (refreshing) {
            val loadingRotation by rememberInfiniteTransition(label = "ba_calendar_pool_refresh_rotation")
                .animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(durationMillis = 900, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart,
                        ),
                    label = "ba_calendar_pool_refresh_rotation_value",
                )
            loadingRotation
        } else {
            0f
        }

    val pageTitle =
        stringResource(
            when {
                bothColumns -> R.string.ba_calendar_pool_title_format
                activeTab == BaCalendarPoolTab.Calendar -> R.string.ba_calendar_title_format
                else -> R.string.ba_pool_title_format
            },
            serverOptions[serverIndex],
        )
    val activePageKind =
        when {
            bothColumns -> BaCalendarPoolPageKind.Both
            activeTab == BaCalendarPoolTab.Calendar -> BaCalendarPoolPageKind.Calendar
            else -> BaCalendarPoolPageKind.Pool
        }

    AppPageScaffold(
        title = pageTitle,
        modifier =
            Modifier
                .fillMaxSize()
                .pageRootTestTag(KeiOsTestTags.BaCalendarPoolPageRoot),
        scrollBehavior = scrollBehavior,
        topBarColor = topBarColor,
        titleBackdrop = pageBackdrop,
        contentMaxWidth = appPageContentMaxWidthFor(columnCount),
        reserveTopEndActionSpace = true,
        navigationIcon = {
            AppLiquidNavigationButton(
                icon = appLucideBackIcon(),
                contentDescription = stringResource(R.string.common_close),
                onClick = onClose,
                backdrop = pageBackdrop,
            )
        },
        actions = {
            BaCalendarPoolActionBar(
                backdrop = pageBackdrop,
                settingsContentDescription = stringResource(R.string.ba_calendar_pool_cd_data_settings),
                refreshContentDescription =
                    stringResource(
                        when {
                            bothColumns -> R.string.ba_calendar_pool_cd_refresh
                            activeTab == BaCalendarPoolTab.Calendar -> R.string.ba_calendar_cd_refresh
                            else -> R.string.ba_pool_cd_refresh
                        },
                    ),
                refreshing = refreshing,
                refreshIconRotation = refreshIconRotation,
                refreshingTint = countdownBlue,
                onOpenSettings = { calendarPoolViewModel.updateDataSettingsSheetVisible(true) },
                onRefresh = {
                    // One toolbar over both lists refreshes both; over one, only the one on screen.
                    if (bothColumns || activeTab == BaCalendarPoolTab.Calendar) {
                        calendarPoolViewModel.requestCalendarReload()
                    }
                    if (bothColumns || activeTab == BaCalendarPoolTab.Pool) {
                        calendarPoolViewModel.requestPoolReload()
                    }
                },
            )
        },
        bottomBar = {
            // No search on either half, so the dock collapses to the category bar alone. The bar is what
            // replaces the second dock slot, and it says which of the two lists is on screen -- which two
            // separate routes never had to. With both lists side by side there is nothing to switch, so
            // the bar is gone entirely rather than shown inert.
            if (!bothColumns) {
                TabbedPageBottomChrome(
                    visible = true,
                    navigationBarBottom = navigationBarBottom,
                    categories = tabs,
                    selectedPage = pagerState.targetPage.coerceIn(tabs.indices),
                    selectedPagePosition = null,
                    selectedPagePositionProvider = {
                        if (pagerState.isScrollInProgress) {
                            pagerState.pagePosition.coerceIn(0f, tabs.lastIndex.toFloat())
                        } else {
                            null
                        }
                    },
                    selectedPageProvider = { pagerState.targetPage },
                    searchExpanded = false,
                    searchQuery = "",
                    onSearchQueryChange = {},
                    onSearchExpandedChange = {},
                    searchIcon = appLucideSearchIcon(),
                    searchContentDescription = "",
                    searchPlaceholder = "",
                    searchEnabled = false,
                    backdrop = bottomBarBackdrop,
                    isLiquidEffectEnabled = true,
                    onSelectCategory = { index ->
                        pageScope.launch {
                            pagerState.animateToPage(
                                target = index.coerceIn(tabs.indices),
                                animationsEnabled = transitionAnimationsEnabled,
                                durationMillis = BaCalendarPoolTabSwitchMs,
                            )
                        }
                    },
                    onExpandDock = {},
                    labelPrefix = "ba_calendar_pool",
                )
            }
        },
    ) { innerPadding ->
        // Hoisted so the accent wash has exactly one theme decision, and so the gradient's
        // opaque ends can drop out without duplicating it.
        val accentWash = accent.copy(alpha = if (isAppInDarkTheme()) 0.11f else 0.07f)
        val pageBase = MiuixTheme.colorScheme.background
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                // The accent wash is this page's design and stays. Only the opaque ends
                                // drop out while a non-Home background is painting, so the image shows
                                // through instead of being hidden behind a full-page plate.
                                colors =
                                    if (appManagedPageBackgroundActive()) {
                                        listOf(Color.Transparent, accentWash, Color.Transparent)
                                    } else {
                                        listOf(pageBase, accentWash, pageBase)
                                    },
                            ),
                        ).layerBackdrop(pageBackdrop),
            )
            if (bothColumns) {
                BaCalendarPoolBothColumnsContent(
                    innerPadding = innerPadding,
                    calendarListState = calendarListState,
                    poolListState = poolListState,
                    nestedScrollConnection = scrollBehavior.nestedScrollConnection,
                    backdrop = pageBackdrop,
                    serverOptions = serverOptions,
                    serverIndex = serverIndex,
                    showServerPopup = chromeUiState.showServerPopup,
                    serverPopupAnchorBounds = chromeUiState.serverPopupAnchorBounds,
                    showEndedActivities = snapshot.showEndedActivities,
                    showEndedPools = snapshot.showEndedPools,
                    showCalendarPoolImages = snapshot.showCalendarPoolImages,
                    calendarUiState = calendarUiState,
                    poolUiState = poolUiState,
                    syncText = syncText,
                    syncTextColor = countdownBlue,
                    onServerPopupChange = calendarPoolViewModel::updateServerPopupExpanded,
                    onServerPopupAnchorBoundsChange = calendarPoolViewModel::updateServerPopupAnchorBounds,
                    onServerSelected = { selected ->
                        calendarPoolViewModel.selectServer(selected.coerceIn(serverOptions.indices))
                    },
                    onOpenPoolStudentGuide = { url ->
                        openBaPoolGuideLink(
                            context = context,
                            scope = pageScope,
                            calendarPoolViewModel = calendarPoolViewModel,
                            rawUrl = url,
                            onOpenGuide = onOpenGuide,
                        )
                    },
                    onOpenCalendarLink = { url ->
                        openBaExternalLink(context = context, url = url)
                    },
                )
            } else {
                MainLoadedPager(
                    state = pagerState,
                    userScrollEnabled = true,
                    animationsEnabled = transitionAnimationsEnabled,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .layerBackdrop(bottomBarBackdrop),
                ) { pageIndex ->
                    when (tabs[pageIndex]) {
                        BaCalendarPoolTab.Calendar ->
                            BaActivityCalendarListContent(
                                innerPadding = innerPadding,
                                listState = calendarListState,
                                nestedScrollConnection = scrollBehavior.nestedScrollConnection,
                                backdrop = pageBackdrop,
                                serverOptions = serverOptions,
                                serverIndex = serverIndex,
                                showServerPopup = chromeUiState.showServerPopup,
                                serverPopupAnchorBounds = chromeUiState.serverPopupAnchorBounds,
                                showEndedActivities = snapshot.showEndedActivities,
                                showCalendarPoolImages = snapshot.showCalendarPoolImages,
                                entries = calendarUiState.entries,
                                loading = calendarUiState.loading,
                                refreshing = calendarUiState.refreshing,
                                error = calendarUiState.error,
                                syncText = syncText,
                                syncTextColor = countdownBlue,
                                onServerPopupChange = calendarPoolViewModel::updateServerPopupExpanded,
                                onServerPopupAnchorBoundsChange = calendarPoolViewModel::updateServerPopupAnchorBounds,
                                onServerSelected = { selected ->
                                    calendarPoolViewModel.selectServer(selected.coerceIn(serverOptions.indices))
                                },
                                onOpenCalendarLink = { url ->
                                    openBaExternalLink(context = context, url = url)
                                },
                            )

                        BaCalendarPoolTab.Pool ->
                            BaPoolListContent(
                                innerPadding = innerPadding,
                                listState = poolListState,
                                nestedScrollConnection = scrollBehavior.nestedScrollConnection,
                                backdrop = pageBackdrop,
                                serverOptions = serverOptions,
                                serverIndex = serverIndex,
                                showServerPopup = chromeUiState.showServerPopup,
                                serverPopupAnchorBounds = chromeUiState.serverPopupAnchorBounds,
                                showEndedPools = snapshot.showEndedPools,
                                showCalendarPoolImages = snapshot.showCalendarPoolImages,
                                entries = poolUiState.entries,
                                loading = poolUiState.loading,
                                refreshing = poolUiState.refreshing,
                                error = poolUiState.error,
                                syncText = syncText,
                                syncTextColor = countdownBlue,
                                onServerPopupChange = calendarPoolViewModel::updateServerPopupExpanded,
                                onServerPopupAnchorBoundsChange = calendarPoolViewModel::updateServerPopupAnchorBounds,
                                onServerSelected = { selected ->
                                    calendarPoolViewModel.selectServer(selected.coerceIn(serverOptions.indices))
                                },
                                onOpenPoolStudentGuide = { url ->
                                    openBaPoolGuideLink(
                                        context = context,
                                        scope = pageScope,
                                        calendarPoolViewModel = calendarPoolViewModel,
                                        rawUrl = url,
                                        onOpenGuide = onOpenGuide,
                                    )
                                },
                                onOpenCalendarLink = { url ->
                                    openBaExternalLink(context = context, url = url)
                                },
                            )
                    }
                }
            }
        }
    }
    BaCalendarPoolDataSettingsSheet(
        show = chromeUiState.showDataSettingsSheet,
        backdrop = pageBackdrop,
        pageKind = activePageKind,
        snapshot = snapshot,
        refreshIntervalDropdownExpanded = chromeUiState.dataRefreshIntervalDropdownExpanded,
        refreshIntervalDropdownAnchorBounds = chromeUiState.dataRefreshIntervalDropdownAnchorBounds,
        onRefreshIntervalDropdownExpandedChange = calendarPoolViewModel::updateDataRefreshIntervalDropdownExpanded,
        onRefreshIntervalDropdownAnchorBoundsChange = calendarPoolViewModel::updateDataRefreshIntervalDropdownAnchorBounds,
        onRefreshIntervalSelected = { hours ->
            calendarPoolViewModel.saveRefreshInterval(
                hours = hours,
                lastSyncMs = lastSyncMs,
                pageKind = activePageKind,
            )
        },
        onShowEndedActivitiesChange = calendarPoolViewModel::saveShowEndedActivities,
        onShowEndedPoolsChange = calendarPoolViewModel::saveShowEndedPools,
        onShowCalendarPoolImagesChange = calendarPoolViewModel::saveShowCalendarPoolImages,
        onDismissRequest = { calendarPoolViewModel.updateDataSettingsSheetVisible(false) },
    )
}

/** One step between two adjacent tabs, so the bar's indicator and the pager land together. */
private const val BaCalendarPoolTabSwitchMs = 260
