@file:Suppress("FunctionName")

package os.kei.ui.page.main.sync

import android.content.Intent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import os.kei.ui.page.main.widget.chrome.TabbedPageBottomChromeMotionMs
import os.kei.ui.page.main.widget.chrome.appPageBottomPaddingWithFloatingOverlay
import os.kei.ui.page.main.widget.chrome.rememberTabbedPageChromeScrollState
import os.kei.ui.page.main.widget.chrome.tabbedPageContentNestedScrollConnection
import os.kei.ui.page.main.widget.core.CardLayoutRhythm
import os.kei.ui.page.main.widget.dialog.AppWindowDialogHost
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
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
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    val topBarBackdrop = rememberLayerBackdrop()
    val bottomBarBackdrop = rememberLayerBackdrop()
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
                            durationMillis = TabbedPageBottomChromeMotionMs,
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
        modifier = Modifier.fillMaxSize(),
        scrollBehavior = scrollBehavior,
        topBarColor = Color.Transparent,
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
                onConfirm = { viewModel.confirmPlan(dataPorts) },
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
    onOpenJianguoyunHelp: () -> Unit,
) {
    when (category) {
        WebDavSyncCategory.Connection -> {
            item(key = "webdav-connection", contentType = "webdav_card") {
                var providerExpanded by remember { mutableStateOf(false) }
                var providerAnchorBounds by remember { mutableStateOf<IntRect?>(null) }
                WebDavConnectionCard(
                    state = state,
                    cardColor = cardColor,
                    providerExpanded = providerExpanded,
                    providerAnchorBounds = providerAnchorBounds,
                    onProviderExpandedChange = { providerExpanded = it },
                    onProviderAnchorBoundsChange = { providerAnchorBounds = it },
                    onSelectProvider = viewModel::selectProvider,
                    onUpdateServerUrl = viewModel::updateServerUrl,
                    onUpdateUsername = viewModel::updateUsername,
                    onUpdateAppPassword = viewModel::updateAppPassword,
                    onUpdateRemoteDir = viewModel::updateRemoteDir,
                    onTogglePasswordVisible = viewModel::togglePasswordVisible,
                    onTestConnection = viewModel::testConnection,
                    onSave = viewModel::saveConfig,
                    onOpenJianguoyunHelp = onOpenJianguoyunHelp,
                )
            }
        }

        WebDavSyncCategory.Data -> {
            item(key = "webdav-sync-items", contentType = "webdav_card") {
                WebDavSyncItemsCard(
                    state = state,
                    cardColor = cardColor,
                    onToggleAutoSync = viewModel::setAutoSyncEnabled,
                    onToggleItem = viewModel::toggleItem,
                    onRunItem = { item, kind ->
                        viewModel.requestItemPlan(item, kind, dataPorts)
                    },
                    onSyncAll = { viewModel.requestBatchPlan(WebDavBatchKind.Sync, dataPorts) },
                    onUploadAll = { viewModel.requestBatchPlan(WebDavBatchKind.Upload, dataPorts) },
                    onDownloadAll = { viewModel.requestBatchPlan(WebDavBatchKind.Download, dataPorts) },
                    onRefreshRemote = { viewModel.refreshRemoteSummary(dataPorts) },
                )
            }
        }

        WebDavSyncCategory.Advanced -> {
            item(key = "webdav-advanced", contentType = "webdav_card") {
                if (state.isConfigured) {
                    WebDavClearCard(
                        cardColor = cardColor,
                        onClear = viewModel::clearConfig,
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
    advanced: LazyListState,
): WebDavSyncListStates =
    remember(connection, data, advanced) {
        WebDavSyncListStates(
            connection = connection,
            data = data,
            advanced = advanced,
        )
    }

private data class WebDavSyncListStates(
    val connection: LazyListState,
    val data: LazyListState,
    val advanced: LazyListState,
) {
    fun forCategory(category: WebDavSyncCategory): LazyListState =
        when (category) {
            WebDavSyncCategory.Connection -> connection
            WebDavSyncCategory.Data -> data
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
