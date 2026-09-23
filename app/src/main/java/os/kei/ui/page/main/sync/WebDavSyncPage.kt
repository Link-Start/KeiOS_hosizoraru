@file:Suppress("FunctionName")

package os.kei.ui.page.main.sync

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntRect
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch
import os.kei.R
import os.kei.core.background.AppBackgroundScheduler
import os.kei.core.ui.effect.rememberAppTopBarColor
import os.kei.feature.webdav.jianguoyun.JianguoyunPreset
import os.kei.ui.page.main.host.pager.MainLoadedPager
import os.kei.ui.page.main.host.pager.MainLoadedPagerState
import os.kei.ui.page.main.host.pager.rememberMainLoadedPagerState
import os.kei.ui.page.main.os.appLucideBackIcon
import os.kei.ui.page.main.os.appLucideSearchIcon
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.chrome.AppLiquidNavigationButton
import os.kei.ui.page.main.widget.chrome.AppPageLazyColumn
import os.kei.ui.page.main.widget.chrome.AppPageScaffold
import os.kei.ui.page.main.widget.chrome.appPageBottomPaddingWithFloatingOverlay
import os.kei.ui.page.main.widget.chrome.rememberTabbedPageChromeScrollState
import os.kei.ui.page.main.widget.chrome.tabbedPageContentNestedScrollConnection
import os.kei.ui.page.main.widget.core.CardLayoutRhythm
import os.kei.ui.page.main.widget.dialog.AppWindowDialogHost
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
import os.kei.ui.testing.KeiOsTestTags
import os.kei.ui.testing.pageRootTestTag
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun WebDavSyncPage(
    onBack: () -> Unit,
    dataPorts: Map<WebDavSyncItem, WebDavSyncDataPort>,
    viewModel: WebDavSyncViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    val topBarBackdrop = rememberLayerBackdrop()
    val bottomBarBackdrop = rememberLayerBackdrop()
    val topBarColor = rememberAppTopBarColor(enableBackdropEffects = true)
    val categories = remember { WebDavSyncCategory.entries.toList() }
    val pagerState =
        rememberMainLoadedPagerState(
            initialPage = 0,
            pageCount = categories.size,
            pageKeys = categories.map { it.name },
        )
    val listStates =
        rememberWebDavSyncListStates(
            connection = rememberLazyListState(),
            data = rememberLazyListState(),
            history = rememberLazyListState(),
            advanced = rememberLazyListState(),
        )
    var bottomBarVisible by remember { mutableStateOf(true) }
    val navigationBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val transitionAnimationsEnabled = LocalTransitionAnimationsEnabled.current
    val cardColor = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.64f)
    val activeListStateProvider =
        remember(categories, pagerState, listStates) {
            {
                val activeIndex =
                    webDavActiveCategoryIndex(
                        scrolling = pagerState.isScrollInProgress,
                        targetPage = pagerState.targetPage,
                        settledPage = pagerState.settledPage,
                        lastIndex = categories.lastIndex,
                    )
                listStates.forCategory(categories[activeIndex])
            }
        }
    val bottomChromeScrollState =
        rememberTabbedPageChromeScrollState(
            visible = bottomBarVisible,
            activeListStateProvider = activeListStateProvider,
            onVisibleChange = { bottomBarVisible = it },
        )
    val selectCategory =
        remember(categories, pagerState, scope, transitionAnimationsEnabled) {
            { index: Int ->
                val target = index.coerceIn(0, categories.lastIndex)
                if (target != pagerState.targetPage) {
                    scope.launch {
                        pagerState.animateToPage(
                            target = target,
                            animationsEnabled = transitionAnimationsEnabled,
                        )
                    }
                }
            }
        }
    val openJianguoyunHelp =
        remember(context) {
            {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, JianguoyunPreset.HELP_URL.toUri())
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
                Unit
            }
        }

    LaunchedEffect(dataPorts) {
        viewModel.refreshLocalCounts(dataPorts)
    }

    LaunchedEffect(pagerState.settledPage) {
        bottomChromeScrollState.showNow()
    }

    AppPageScaffold(
        title = stringResource(R.string.webdav_sync_title),
        modifier = Modifier.fillMaxSize().pageRootTestTag(KeiOsTestTags.WebDavSyncPageRoot),
        scrollBehavior = scrollBehavior,
        topBarColor = topBarColor,
        titleBackdrop = topBarBackdrop,
        onTitleClick = {
            scope.launch {
                activeListStateProvider().animateScrollToItem(0)
            }
        },
        navigationIcon = {
            AppLiquidNavigationButton(
                icon = appLucideBackIcon(),
                contentDescription = stringResource(R.string.common_close),
                onClick = onBack,
                backdrop = topBarBackdrop,
            )
        },
        bottomBar = {
            WebDavSyncBottomChrome(
                visible = bottomBarVisible,
                navigationBarBottom = navigationBarBottom,
                categories = categories,
                selectedPage = pagerState.targetPage.coerceIn(0, categories.lastIndex),
                selectedPagePosition = null,
                selectedPagePositionProvider = {
                    if (pagerState.isScrollInProgress) {
                        pagerState.pagePosition.coerceIn(
                            0f,
                            categories.lastIndex.coerceAtLeast(0).toFloat(),
                        )
                    } else {
                        null
                    }
                },
                selectedPageProvider = { pagerState.targetPage },
                searchIcon = appLucideSearchIcon(),
                searchContentDescription = stringResource(R.string.webdav_sync_title),
                searchPlaceholder = stringResource(R.string.webdav_sync_title),
                backdrop = bottomBarBackdrop,
                isLiquidEffectEnabled = true,
                onSelectCategory = selectCategory,
                onExpandDock = {
                    bottomChromeScrollState.showNow()
                },
            )
        },
    ) { innerPadding ->
        WebDavSyncPagerContent(
            innerPadding = innerPadding,
            state = state,
            dataPorts = dataPorts,
            viewModel = viewModel,
            categories = categories,
            pagerState = pagerState,
            listStates = listStates,
            topBarBackdrop = topBarBackdrop,
            bottomBarBackdrop = bottomBarBackdrop,
            chromeNestedScrollConnection = bottomChromeScrollState.chromeNestedScrollConnection,
            topBarNestedScrollConnection = scrollBehavior.nestedScrollConnection,
            cardColor = cardColor,
            appContext = appContext,
            onOpenJianguoyunHelp = openJianguoyunHelp,
            transitionAnimationsEnabled = transitionAnimationsEnabled,
        )
    }

    val pendingPlan = state.pendingPlan
    AppWindowDialogHost(
        show = pendingPlan != null,
        onDismissRequest = viewModel::dismissPlan,
    ) {
        if (pendingPlan != null) {
            WebDavSyncPlanDialog(
                plan = pendingPlan,
                onDismiss = viewModel::dismissPlan,
                onConfirm = { viewModel.confirmPlan(appContext, dataPorts) },
            )
        }
    }
}

@Composable
private fun WebDavSyncPagerContent(
    innerPadding: PaddingValues,
    state: WebDavSyncUiState,
    dataPorts: Map<WebDavSyncItem, WebDavSyncDataPort>,
    viewModel: WebDavSyncViewModel,
    categories: List<WebDavSyncCategory>,
    pagerState: MainLoadedPagerState,
    listStates: WebDavSyncListStates,
    topBarBackdrop: LayerBackdrop,
    bottomBarBackdrop: LayerBackdrop,
    chromeNestedScrollConnection: NestedScrollConnection,
    topBarNestedScrollConnection: NestedScrollConnection,
    cardColor: Color,
    appContext: Context,
    onOpenJianguoyunHelp: () -> Unit,
    transitionAnimationsEnabled: Boolean,
) {
    MainLoadedPager(
        state = pagerState,
        userScrollEnabled = true,
        animationsEnabled = transitionAnimationsEnabled,
        modifier =
            Modifier
                .fillMaxSize()
                .layerBackdrop(topBarBackdrop)
                .layerBackdrop(bottomBarBackdrop),
    ) { pageIndex ->
        val category = categories[pageIndex]
        val listState = listStates.forCategory(category)
        val nestedScrollConnection =
            remember(listState, chromeNestedScrollConnection, topBarNestedScrollConnection) {
                tabbedPageContentNestedScrollConnection(
                    listState = listState,
                    chrome = chromeNestedScrollConnection,
                    delegate = topBarNestedScrollConnection,
                )
            }
        AppPageLazyColumn(
            innerPadding = innerPadding,
            state = listState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .nestedScroll(nestedScrollConnection),
            bottomExtra =
                appPageBottomPaddingWithFloatingOverlay(
                    AppChromeTokens.floatingBottomBarOuterHeight,
                ),
            sectionSpacing = CardLayoutRhythm.sectionGap,
        ) {
            webDavCategoryItems(
                category = category,
                state = state,
                dataPorts = dataPorts,
                viewModel = viewModel,
                cardColor = cardColor,
                appContext = appContext,
                onOpenJianguoyunHelp = onOpenJianguoyunHelp,
            )
        }
    }
}

private fun LazyListScope.webDavCategoryItems(
    category: WebDavSyncCategory,
    state: WebDavSyncUiState,
    dataPorts: Map<WebDavSyncItem, WebDavSyncDataPort>,
    viewModel: WebDavSyncViewModel,
    cardColor: Color,
    appContext: Context,
    onOpenJianguoyunHelp: () -> Unit,
) {
    when (category) {
        WebDavSyncCategory.Connection -> {
            item(key = "webdav-connection-overview", contentType = "webdav_overview_card") {
                var providerExpanded by remember { mutableStateOf(false) }
                var providerAnchorBounds by remember { mutableStateOf<IntRect?>(null) }
                WebDavConnectionOverviewCard(
                    state = state,
                    cardColor = cardColor,
                    providerExpanded = providerExpanded,
                    providerAnchorBounds = providerAnchorBounds,
                    onProviderExpandedChange = { providerExpanded = it },
                    onProviderAnchorBoundsChange = { providerAnchorBounds = it },
                    onSelectProvider = viewModel::selectProvider,
                )
            }
            item(key = "webdav-connection-credentials", contentType = "webdav_form_card") {
                WebDavCredentialsCard(
                    state = state,
                    cardColor = cardColor,
                    onUpdateUsername = viewModel::updateUsername,
                    onUpdateAppPassword = viewModel::updateAppPassword,
                    onTogglePasswordVisible = viewModel::togglePasswordVisible,
                )
            }
            item(key = "webdav-connection-workspace", contentType = "webdav_form_card") {
                WebDavRemoteWorkspaceCard(
                    state = state,
                    cardColor = cardColor,
                    onUpdateServerUrl = viewModel::updateServerUrl,
                    onUpdateRemoteDir = viewModel::updateRemoteDir,
                    onOpenJianguoyunHelp = onOpenJianguoyunHelp,
                )
            }
            item(key = "webdav-connection-actions", contentType = "webdav_action_card") {
                WebDavConnectionActionsCard(
                    state = state,
                    cardColor = cardColor,
                    onTestConnection = viewModel::testConnection,
                    onSave = {
                        viewModel.saveConfig {
                            AppBackgroundScheduler.scheduleWebDavAutoSync(appContext)
                        }
                    },
                )
            }
        }

        WebDavSyncCategory.Data -> {
            item(key = "webdav-sync-overview", contentType = "webdav_card") {
                WebDavSyncOverviewCard(
                    state = state,
                    cardColor = cardColor,
                )
            }
            item(key = "webdav-sync-auto", contentType = "webdav_card") {
                WebDavSyncAutoSyncCard(
                    state = state,
                    cardColor = cardColor,
                    onToggleAutoSync = { enabled ->
                        viewModel.setAutoSyncEnabled(enabled) {
                            AppBackgroundScheduler.scheduleWebDavAutoSync(appContext)
                        }
                    },
                    onAutoSyncIntervalHoursChange = { hours ->
                        viewModel.setAutoSyncIntervalHours(hours) {
                            AppBackgroundScheduler.scheduleWebDavAutoSync(appContext)
                        }
                    },
                    onResolveAutoSyncReview = {
                        viewModel.requestAutoSyncReviewPlan(dataPorts)
                    },
                )
            }
            item(key = "webdav-sync-remote", contentType = "webdav_card") {
                WebDavSyncRemoteSnapshotCard(
                    state = state,
                    cardColor = cardColor,
                    onRefreshRemote = { viewModel.refreshRemoteSummary(appContext, dataPorts) },
                )
            }
            item(key = "webdav-sync-batch", contentType = "webdav_card") {
                WebDavSyncBatchActionsCard(
                    state = state,
                    cardColor = cardColor,
                    onSyncAll = { viewModel.requestBatchPlan(WebDavBatchKind.Sync, dataPorts) },
                    onUploadAll = { viewModel.requestBatchPlan(WebDavBatchKind.Upload, dataPorts) },
                    onDownloadAll = { viewModel.requestBatchPlan(WebDavBatchKind.Download, dataPorts) },
                )
            }
            WebDavSyncDataGroup.entries.forEach { group ->
                item(key = "webdav-sync-items-${group.name}", contentType = "webdav_card") {
                    WebDavSyncItemListCard(
                        titleRes = group.titleRes,
                        items = group.items,
                        state = state,
                        cardColor = cardColor,
                        onToggleItem = { item ->
                            viewModel.toggleItem(item) {
                                AppBackgroundScheduler.scheduleWebDavAutoSync(appContext)
                            }
                        },
                        onRunItem = { item, kind ->
                            viewModel.requestItemPlan(item, kind, dataPorts)
                        },
                    )
                }
            }
        }

        WebDavSyncCategory.History -> {
            item(key = "webdav-history-summary", contentType = "webdav_card") {
                WebDavSyncHistorySummaryCard(
                    history = state.history,
                    cardColor = cardColor,
                    onClearHistory = viewModel::clearHistory,
                )
            }
            if (state.history.isEmpty()) {
                item(key = "webdav-history-empty", contentType = "webdav_card") {
                    WebDavSyncHistoryEmptyCard(cardColor = cardColor)
                }
            } else {
                items(
                    items = state.history,
                    key = { entry -> "webdav-history-${entry.id}" },
                    contentType = { "webdav_history_entry" },
                ) { entry ->
                    var expanded by rememberSaveable(entry.id) { mutableStateOf(false) }
                    WebDavSyncHistoryEntryCard(
                        entry = entry,
                        expanded = expanded,
                        cardColor = cardColor,
                        onExpandedChange = { expanded = it },
                    )
                }
            }
        }

        WebDavSyncCategory.Advanced -> {
            item(key = "webdav-advanced", contentType = "webdav_card") {
                if (state.isConfigured) {
                    WebDavClearCard(
                        cardColor = cardColor,
                        onClear = {
                            viewModel.clearConfig {
                                AppBackgroundScheduler.scheduleWebDavAutoSync(appContext)
                            }
                        },
                    )
                } else {
                    WebDavAdvancedInfoCard(cardColor = cardColor)
                }
            }
        }
    }
}

@Composable
private fun rememberWebDavSyncListStates(
    connection: LazyListState,
    data: LazyListState,
    history: LazyListState,
    advanced: LazyListState,
): WebDavSyncListStates =
    remember(connection, data, history, advanced) {
        WebDavSyncListStates(
            connection = connection,
            data = data,
            history = history,
            advanced = advanced,
        )
    }

private data class WebDavSyncListStates(
    val connection: LazyListState,
    val data: LazyListState,
    val history: LazyListState,
    val advanced: LazyListState,
) {
    fun forCategory(category: WebDavSyncCategory): LazyListState =
        when (category) {
            WebDavSyncCategory.Connection -> connection
            WebDavSyncCategory.Data -> data
            WebDavSyncCategory.History -> history
            WebDavSyncCategory.Advanced -> advanced
        }
}

private fun webDavActiveCategoryIndex(
    scrolling: Boolean,
    targetPage: Int,
    settledPage: Int,
    lastIndex: Int,
): Int =
    if (scrolling) {
        targetPage
    } else {
        settledPage
    }.coerceIn(0, lastIndex.coerceAtLeast(0))

private enum class WebDavSyncDataGroup(
    val titleRes: Int,
    val items: List<WebDavSyncItem>,
) {
    GitHub(
        titleRes = R.string.webdav_sync_items_github_title,
        items = listOf(WebDavSyncItem.GitHubTracked),
    ),
    Ba(
        titleRes = R.string.webdav_sync_items_ba_title,
        items =
            listOf(
                WebDavSyncItem.BaAccounts,
                WebDavSyncItem.BaCatalogFavorites,
                WebDavSyncItem.BaBgmFavorites,
            ),
    ),
    Os(
        titleRes = R.string.webdav_sync_items_os_title,
        items =
            listOf(
                WebDavSyncItem.OsActivityCards,
                WebDavSyncItem.OsShellCards,
            ),
    ),
}
