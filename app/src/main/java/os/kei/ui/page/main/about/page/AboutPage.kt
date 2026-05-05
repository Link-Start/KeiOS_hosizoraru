package os.kei.ui.page.main.about.page

import android.content.pm.PackageInfo
import android.widget.Toast
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch
import os.kei.R
import os.kei.core.system.ShizukuApiUtils
import os.kei.ui.page.main.about.section.AboutAppCardSection
import os.kei.ui.page.main.about.section.AboutBuildSdkCardSection
import os.kei.ui.page.main.about.section.AboutComponentCardSection
import os.kei.ui.page.main.about.section.AboutGitHubCardSection
import os.kei.ui.page.main.about.section.AboutLicenseCardSection
import os.kei.ui.page.main.about.section.AboutMediaStorageCardSection
import os.kei.ui.page.main.about.section.AboutNetworkServiceCardSection
import os.kei.ui.page.main.about.section.AboutPermissionCardSection
import os.kei.ui.page.main.about.section.AboutProjectLicenseCardSection
import os.kei.ui.page.main.about.section.AboutRuntimeStatusCardSection
import os.kei.ui.page.main.about.section.AboutUiFrameworkCardSection
import os.kei.ui.page.main.about.state.rememberAboutPageColorPalette
import os.kei.ui.page.main.about.state.rememberAboutPageSectionExpansionState
import os.kei.ui.page.main.about.util.openExternalUrl
import os.kei.ui.page.main.debug.DebugComponentLabActivity
import os.kei.ui.page.main.os.appLucideBackIcon
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.chrome.AppLiquidNavigationButton
import os.kei.ui.page.main.widget.chrome.AppPageLazyColumn
import os.kei.ui.page.main.widget.chrome.AppPageScaffold
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior

@Composable
fun AboutPage(
    appLabel: String,
    packageInfo: PackageInfo?,
    notificationPermissionGranted: Boolean,
    shizukuStatus: String,
    shizukuApiUtils: ShizukuApiUtils,
    onCheckShizuku: () -> Unit,
    contentBottomPadding: Dp = 72.dp,
    scrollToTopSignal: Int = 0,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val palette = rememberAboutPageColorPalette(shizukuStatus = shizukuStatus)
    val viewModel: AboutPageViewModel = viewModel()
    val detailsState by viewModel.detailsState.collectAsState()

    val categories = remember {
        listOf(
            AboutCategory.Overview,
            AboutCategory.Runtime,
            AboutCategory.Security,
            AboutCategory.Tech,
        )
    }
    var selectedCategoryIndex by rememberSaveable { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(
        initialPage = selectedCategoryIndex,
        pageCount = { categories.size },
    )
    val overviewListState = rememberLazyListState()
    val runtimeListState = rememberLazyListState()
    val securityListState = rememberLazyListState()
    val techListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    val expansionState = rememberAboutPageSectionExpansionState()
    val topBarBackdrop = rememberLayerBackdrop()
    val bottomBarBackdrop = rememberLayerBackdrop()
    val navigationBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal > 0) {
            when (categories.getOrNull(pagerState.settledPage)) {
                AboutCategory.Overview -> overviewListState.animateScrollToItem(0)
                AboutCategory.Runtime -> runtimeListState.animateScrollToItem(0)
                AboutCategory.Security -> securityListState.animateScrollToItem(0)
                AboutCategory.Tech -> techListState.animateScrollToItem(0)
                null -> Unit
            }
        }
    }

    LaunchedEffect(pagerState.settledPage) {
        if (selectedCategoryIndex != pagerState.settledPage) {
            selectedCategoryIndex = pagerState.settledPage
        }
    }

    LaunchedEffect(context, notificationPermissionGranted, shizukuStatus, shizukuApiUtils) {
        viewModel.refreshDetails(
            context = context,
            notificationPermissionGranted = notificationPermissionGranted,
            shizukuApiUtils = shizukuApiUtils
        )
    }
    val permissionEntries = detailsState.permissionEntries
    val componentEntries = detailsState.componentEntries
    val shizukuDetailMap = detailsState.shizukuDetailMap
    val shizukuReady = shizukuStatus.contains("granted", ignoreCase = true)
    val openLinkFailed = stringResource(R.string.common_open_link_failed)
    val selectAboutCategory: (Int) -> Unit = { index ->
        val target = index.coerceIn(0, categories.lastIndex)
        selectedCategoryIndex = target
        scope.launch {
            pagerState.animateScrollToPage(target)
        }
    }

    AppPageScaffold(
        title = stringResource(R.string.about_page_title),
        modifier = Modifier.fillMaxSize(),
        scrollBehavior = scrollBehavior,
        topBarColor = Color.Transparent,
        navigationIcon = {
            if (onBack != null) {
                AppLiquidNavigationButton(
                    icon = appLucideBackIcon(),
                    contentDescription = stringResource(R.string.common_close),
                    onClick = onBack,
                    backdrop = topBarBackdrop
                )
            }
        },
        bottomBar = {
            AboutCategoryBottomBar(
                visible = true,
                navigationBarBottom = navigationBarBottom,
                categories = categories,
                selectedPage = pagerState.targetPage.coerceIn(0, categories.lastIndex),
                selectedPageProvider = { pagerState.targetPage },
                backdrop = bottomBarBackdrop,
                isLiquidEffectEnabled = true,
                onSelectCategory = selectAboutCategory,
            )
        }
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            key = { index -> categories[index].name },
            overscrollEffect = null,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .graphicsLayer { alpha = 1f }
                .layerBackdrop(topBarBackdrop)
                .layerBackdrop(bottomBarBackdrop),
        ) { pageIndex ->
            val category = categories[pageIndex]
            val pageListState = when (category) {
                AboutCategory.Overview -> overviewListState
                AboutCategory.Runtime -> runtimeListState
                AboutCategory.Security -> securityListState
                AboutCategory.Tech -> techListState
            }
            AppPageLazyColumn(
                innerPadding = innerPadding,
                state = pageListState,
                modifier = Modifier.fillMaxSize(),
                bottomExtra = contentBottomPadding + AppChromeTokens.floatingBottomBarOuterHeight,
                sectionSpacing = 14.dp,
            ) {
                when (category) {
                    AboutCategory.Overview -> {
                        item {
                            AboutAppCardSection(
                                appLabel = appLabel,
                                packageInfo = packageInfo,
                                cardColor = palette.infoCardColor,
                                accent = palette.accent,
                                subtitleColor = palette.subtitleColor,
                                expanded = expansionState.appExpanded,
                                onExpandedChange = { expansionState.appExpanded = it },
                                onOpenDebugActivity = { DebugComponentLabActivity.launch(context) }
                            )
                        }
                        item {
                            AboutGitHubCardSection(
                                cardColor = palette.githubCardColor,
                                accent = palette.accent,
                                subtitleColor = palette.subtitleColor,
                                expanded = expansionState.githubExpanded,
                                onExpandedChange = { expansionState.githubExpanded = it },
                                onOpenProjectUrl = { url ->
                                    if (!openExternalUrl(context, url)) {
                                        Toast.makeText(context, openLinkFailed, Toast.LENGTH_SHORT)
                                            .show()
                                    }
                                }
                            )
                        }
                    }

                    AboutCategory.Runtime -> {
                        item {
                            AboutRuntimeStatusCardSection(
                                cardColor = palette.runtimeCardColor,
                                accent = palette.accent,
                                shizukuReady = shizukuReady,
                                readyColor = palette.readyColor,
                                notReadyColor = palette.notReadyColor,
                                subtitleColor = palette.subtitleColor,
                                notificationPermissionGranted = notificationPermissionGranted,
                                shizukuDetailMap = shizukuDetailMap,
                                permissionCount = permissionEntries.size,
                                componentCount = componentEntries.size,
                                expanded = expansionState.runtimeExpanded,
                                onExpandedChange = { expansionState.runtimeExpanded = it },
                                onCheckShizuku = onCheckShizuku
                            )
                        }
                        item {
                            AboutNetworkServiceCardSection(
                                cardColor = palette.networkServiceCardColor,
                                titleColor = palette.readyColor,
                                subtitleColor = palette.subtitleColor,
                                expanded = expansionState.networkExpanded,
                                onExpandedChange = { expansionState.networkExpanded = it }
                            )
                        }
                        item {
                            AboutMediaStorageCardSection(
                                cardColor = palette.mediaStorageCardColor,
                                accent = palette.accent,
                                subtitleColor = palette.subtitleColor,
                                expanded = expansionState.mediaExpanded,
                                onExpandedChange = { expansionState.mediaExpanded = it }
                            )
                        }
                    }

                    AboutCategory.Security -> {
                        item {
                            AboutPermissionCardSection(
                                cardColor = palette.githubCardColor,
                                accent = palette.accent,
                                subtitleColor = palette.subtitleColor,
                                readyColor = palette.readyColor,
                                notReadyColor = palette.notReadyColor,
                                entries = permissionEntries,
                                expanded = expansionState.permissionExpanded,
                                onExpandedChange = { expansionState.permissionExpanded = it }
                            )
                        }
                        item {
                            AboutComponentCardSection(
                                cardColor = Color(0x2234D399),
                                titleColor = palette.readyColor,
                                subtitleColor = palette.subtitleColor,
                                accent = palette.accent,
                                entries = componentEntries,
                                expanded = expansionState.componentExpanded,
                                onExpandedChange = { expansionState.componentExpanded = it }
                            )
                        }
                    }

                    AboutCategory.Tech -> {
                        item {
                            AboutBuildSdkCardSection(
                                cardColor = palette.buildCardColor,
                                accent = palette.accent,
                                subtitleColor = palette.subtitleColor,
                                expanded = expansionState.buildExpanded,
                                onExpandedChange = { expansionState.buildExpanded = it }
                            )
                        }
                        item {
                            AboutUiFrameworkCardSection(
                                cardColor = palette.uiFrameworkCardColor,
                                accent = palette.accent,
                                subtitleColor = palette.subtitleColor,
                                expanded = expansionState.uiFrameworkExpanded,
                                onExpandedChange = { expansionState.uiFrameworkExpanded = it }
                            )
                        }
                        item {
                            AboutProjectLicenseCardSection(
                                cardColor = palette.projectLicenseCardColor,
                                accent = palette.accent,
                                subtitleColor = palette.subtitleColor,
                                expanded = expansionState.projectLicenseExpanded,
                                onExpandedChange = { expansionState.projectLicenseExpanded = it },
                                onOpenLicenseUrl = { url ->
                                    if (!openExternalUrl(context, url)) {
                                        Toast.makeText(context, openLinkFailed, Toast.LENGTH_SHORT)
                                            .show()
                                    }
                                }
                            )
                        }
                        item {
                            AboutLicenseCardSection(
                                cardColor = palette.licenseCardColor,
                                accent = palette.accent,
                                subtitleColor = palette.subtitleColor,
                                expanded = expansionState.licenseExpanded,
                                onExpandedChange = { expansionState.licenseExpanded = it },
                                onOpenSourceUrl = { url ->
                                    if (!openExternalUrl(context, url)) {
                                        Toast.makeText(context, openLinkFailed, Toast.LENGTH_SHORT)
                                            .show()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
