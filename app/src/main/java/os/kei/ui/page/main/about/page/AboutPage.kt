package os.kei.ui.page.main.about.page

import android.content.pm.PackageInfo
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import os.kei.ui.page.main.os.appLucideSearchIcon
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
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
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
    val appCardTitle = stringResource(R.string.about_card_app_title)
    val appCardSubtitle = stringResource(R.string.about_card_app_subtitle)
    val githubCardTitle = stringResource(R.string.about_card_github_title)
    val githubCardSubtitle = stringResource(R.string.about_card_github_subtitle)
    val runtimeCardTitle = stringResource(R.string.about_card_runtime_title)
    val runtimeCardSubtitle = stringResource(R.string.about_card_runtime_subtitle)
    val networkCardTitle = stringResource(R.string.about_card_network_title)
    val networkCardSubtitle = stringResource(R.string.about_card_network_subtitle)
    val mediaCardTitle = stringResource(R.string.about_card_media_title)
    val mediaCardSubtitle = stringResource(R.string.about_card_media_subtitle)
    val permissionCardTitle = stringResource(R.string.about_card_permission_title)
    val permissionCardSubtitle = stringResource(R.string.about_card_permission_subtitle)
    val componentCardTitle = stringResource(R.string.about_card_component_title)
    val componentCardSubtitle = stringResource(R.string.about_card_component_subtitle)
    val buildCardTitle = stringResource(R.string.about_card_build_title)
    val buildCardSubtitle = stringResource(R.string.about_card_build_subtitle)
    val uiCardTitle = stringResource(R.string.about_card_ui_title)
    val uiCardSubtitle = stringResource(R.string.about_card_ui_subtitle)
    val projectLicenseCardTitle = stringResource(R.string.about_card_project_license_title)
    val projectLicenseCardSubtitle = stringResource(R.string.about_card_project_license_subtitle)
    val licenseCardTitle = stringResource(R.string.about_card_license_title)
    val licenseCardSubtitle = stringResource(R.string.about_card_license_subtitle)
    val aboutSearchPlaceholder = stringResource(R.string.about_search_placeholder)
    val searchContentDescription = stringResource(R.string.about_search_placeholder)
    val selectAboutCategory: (Int) -> Unit = { index ->
        val target = index.coerceIn(0, categories.lastIndex)
        selectedCategoryIndex = target
        scope.launch {
            pagerState.animateScrollToPage(target)
        }
    }
    fun matchesAboutSearch(vararg values: String): Boolean {
        val query = searchQuery.trim()
        if (query.isBlank()) return true
        return values.any { value -> value.contains(query, ignoreCase = true) }
    }

    BackHandler(enabled = searchExpanded) {
        searchExpanded = false
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
            Box(modifier = Modifier.fillMaxWidth()) {
                AboutCategoryBottomBar(
                    visible = true,
                    navigationBarBottom = navigationBarBottom,
                    categories = categories,
                    selectedPage = pagerState.targetPage.coerceIn(0, categories.lastIndex),
                    selectedPageProvider = { pagerState.targetPage },
                    backdrop = bottomBarBackdrop,
                    isLiquidEffectEnabled = true,
                    compact = searchExpanded,
                    onSelectCategory = selectAboutCategory,
                )
                AboutSearchDock(
                    backdrop = bottomBarBackdrop,
                    expanded = searchExpanded,
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onExpandedChange = { searchExpanded = it },
                    searchIcon = appLucideSearchIcon(),
                    contentDescription = searchContentDescription,
                    placeholder = aboutSearchPlaceholder,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 14.dp, bottom = 12.dp + navigationBarBottom),
                    size = AppChromeTokens.floatingBottomBarOuterHeight,
                    iconSize = 24.dp,
                )
            }
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
                        if (matchesAboutSearch(appCardTitle, appCardSubtitle, appLabel)) item {
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
                        if (matchesAboutSearch(githubCardTitle, githubCardSubtitle)) item {
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
                        if (matchesAboutSearch(
                                runtimeCardTitle,
                                runtimeCardSubtitle,
                                shizukuStatus
                            )
                        ) item {
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
                        if (matchesAboutSearch(networkCardTitle, networkCardSubtitle)) item {
                            AboutNetworkServiceCardSection(
                                cardColor = palette.networkServiceCardColor,
                                titleColor = palette.readyColor,
                                subtitleColor = palette.subtitleColor,
                                expanded = expansionState.networkExpanded,
                                onExpandedChange = { expansionState.networkExpanded = it }
                            )
                        }
                        if (matchesAboutSearch(mediaCardTitle, mediaCardSubtitle)) item {
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
                        if (matchesAboutSearch(permissionCardTitle, permissionCardSubtitle)) item {
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
                        if (matchesAboutSearch(componentCardTitle, componentCardSubtitle)) item {
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
                        if (matchesAboutSearch(buildCardTitle, buildCardSubtitle)) item {
                            AboutBuildSdkCardSection(
                                cardColor = palette.buildCardColor,
                                accent = palette.accent,
                                subtitleColor = palette.subtitleColor,
                                expanded = expansionState.buildExpanded,
                                onExpandedChange = { expansionState.buildExpanded = it }
                            )
                        }
                        if (matchesAboutSearch(uiCardTitle, uiCardSubtitle)) item {
                            AboutUiFrameworkCardSection(
                                cardColor = palette.uiFrameworkCardColor,
                                accent = palette.accent,
                                subtitleColor = palette.subtitleColor,
                                expanded = expansionState.uiFrameworkExpanded,
                                onExpandedChange = { expansionState.uiFrameworkExpanded = it }
                            )
                        }
                        if (matchesAboutSearch(
                                projectLicenseCardTitle,
                                projectLicenseCardSubtitle
                            )
                        ) item {
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
                        if (matchesAboutSearch(licenseCardTitle, licenseCardSubtitle)) item {
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
