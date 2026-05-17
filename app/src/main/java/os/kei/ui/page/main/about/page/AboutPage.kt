package os.kei.ui.page.main.about.page

import android.content.pm.PackageInfo
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import os.kei.R
import os.kei.core.system.ShizukuApiUtils
import os.kei.ui.page.main.about.section.AboutAppCardSection
import os.kei.ui.page.main.about.section.AboutBuildSdkCardSection
import os.kei.ui.page.main.about.section.AboutComponentCardSection
import os.kei.ui.page.main.about.section.AboutComponentLabCardSection
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
import os.kei.ui.page.main.host.pager.MainLoadedPager
import os.kei.ui.page.main.host.pager.rememberMainLoadedPagerState
import os.kei.ui.page.main.os.appLucideBackIcon
import os.kei.ui.page.main.os.appLucideSearchIcon
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.chrome.AppLiquidNavigationButton
import os.kei.ui.page.main.widget.chrome.AppPageLazyColumn
import os.kei.ui.page.main.widget.chrome.AppPageScaffold
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.motion.AppMotionTokens
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
import os.kei.ui.page.main.widget.motion.resolvedMotionDuration
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs

@Composable
fun AboutPage(
    appLabel: String,
    packageInfo: PackageInfo?,
    notificationPermissionGranted: Boolean,
    shizukuStatus: String,
    shizukuApiUtils: ShizukuApiUtils,
    onCheckShizuku: () -> Unit,
    miuixMainNavigationEnabled: Boolean = false,
    contentBottomPadding: Dp = 72.dp,
    scrollToTopSignal: Int = 0,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val palette = rememberAboutPageColorPalette(shizukuStatus = shizukuStatus)
    val viewModel: AboutPageViewModel = viewModel()
    val detailsState by viewModel.detailsState.collectAsStateWithLifecycle()

    val categories = remember {
        listOf(
            AboutCategory.Overview,
            AboutCategory.System,
            AboutCategory.Tech,
            AboutCategory.Lab,
        )
    }
    var selectedCategoryIndex by rememberSaveable { mutableIntStateOf(0) }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val pagerState = rememberMainLoadedPagerState(
        initialPage = selectedCategoryIndex,
        pageCount = categories.size,
    )
    val overviewListState = rememberLazyListState()
    val systemListState = rememberLazyListState()
    val techListState = rememberLazyListState()
    val labListState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    val expansionState = rememberAboutPageSectionExpansionState()
    val topBarBackdrop = rememberLayerBackdrop()
    val bottomBarBackdrop = rememberLayerBackdrop()
    val navigationBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val farJumpAlpha = remember { Animatable(1f) }
    var tabJumpJob by remember { mutableStateOf<Job?>(null) }
    val transitionAnimationsEnabled = LocalTransitionAnimationsEnabled.current

    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal > 0) {
            when (categories.getOrNull(pagerState.settledPage)) {
                AboutCategory.Overview -> overviewListState.animateScrollToItem(0)
                AboutCategory.System -> systemListState.animateScrollToItem(0)
                AboutCategory.Tech -> techListState.animateScrollToItem(0)
                AboutCategory.Lab -> labListState.animateScrollToItem(0)
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
    val aboutSearchPlaceholder = stringResource(R.string.about_search_placeholder)
    val searchContentDescription = stringResource(R.string.about_search_placeholder)
    val searchTargets = rememberAboutSearchTargets(
        appLabel = appLabel,
        shizukuStatus = shizukuStatus,
        permissionEntries = permissionEntries,
        componentEntries = componentEntries,
    )
    val trimmedSearchQuery = searchQuery.trim()
    val searchActive = trimmedSearchQuery.isNotEmpty()
    val matchingSearchTargets = searchTargets.filter { it.matches(trimmedSearchQuery) }
    val matchingSearchCards = matchingSearchTargets.map { it.card }.toSet()
    val selectAboutCategory: (Int) -> Unit = { index ->
        val target = index.coerceIn(0, categories.lastIndex)
        val stablePageIndex = if (pagerState.isScrollInProgress) {
            pagerState.targetPage
        } else {
            pagerState.settledPage
        }
        if (target != stablePageIndex) {
            selectedCategoryIndex = target
            tabJumpJob?.cancel()
            tabJumpJob = scope.launch {
                val distance = abs(target - stablePageIndex)
                if (distance > 1) {
                    farJumpAlpha.snapTo(1f)
                    farJumpAlpha.animateTo(
                        targetValue = 0.92f,
                        animationSpec = tween(
                            durationMillis = resolvedMotionDuration(
                                AppMotionTokens.farJumpDimMs,
                                transitionAnimationsEnabled,
                            ),
                        ),
                    )
                }
                pagerState.animateToPage(
                    target = target,
                    animationsEnabled = transitionAnimationsEnabled,
                    durationMillis = aboutPagerSwitchDurationMillis(distance),
                )
                if (distance > 1) {
                    farJumpAlpha.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = resolvedMotionDuration(
                                AppMotionTokens.farJumpRestoreMs,
                                transitionAnimationsEnabled,
                            ),
                        ),
                    )
                }
            }
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

    fun cardVisible(card: AboutSearchCard): Boolean {
        return !searchActive || card in matchingSearchCards
    }

    fun LazyListScope.aboutCardItem(card: AboutSearchCard) {
        item(key = "about_card_${card.name}") {
            when (card) {
                AboutSearchCard.App -> AboutAppCardSection(
                    appLabel = appLabel,
                    packageInfo = packageInfo,
                    cardColor = palette.infoCardColor,
                    accent = palette.accent,
                    subtitleColor = palette.subtitleColor,
                    expanded = if (searchActive) true else expansionState.appExpanded,
                    onExpandedChange = { expansionState.appExpanded = it }
                )

                AboutSearchCard.GitHub -> AboutGitHubCardSection(
                    cardColor = palette.githubCardColor,
                    accent = palette.accent,
                    subtitleColor = palette.subtitleColor,
                    expanded = if (searchActive) true else expansionState.githubExpanded,
                    onExpandedChange = { expansionState.githubExpanded = it },
                    onOpenProjectUrl = { url ->
                        if (!openExternalUrl(context, url)) {
                            Toast.makeText(context, openLinkFailed, Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                AboutSearchCard.Runtime -> AboutRuntimeStatusCardSection(
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
                    expanded = if (searchActive) true else expansionState.runtimeExpanded,
                    onExpandedChange = { expansionState.runtimeExpanded = it },
                    onCheckShizuku = onCheckShizuku
                )

                AboutSearchCard.Network -> AboutNetworkServiceCardSection(
                    cardColor = palette.networkServiceCardColor,
                    titleColor = palette.readyColor,
                    subtitleColor = palette.subtitleColor,
                    expanded = if (searchActive) true else expansionState.networkExpanded,
                    onExpandedChange = { expansionState.networkExpanded = it }
                )

                AboutSearchCard.Media -> AboutMediaStorageCardSection(
                    cardColor = palette.mediaStorageCardColor,
                    accent = palette.accent,
                    subtitleColor = palette.subtitleColor,
                    expanded = if (searchActive) true else expansionState.mediaExpanded,
                    onExpandedChange = { expansionState.mediaExpanded = it }
                )

                AboutSearchCard.Permission -> AboutPermissionCardSection(
                    cardColor = palette.githubCardColor,
                    accent = palette.accent,
                    subtitleColor = palette.subtitleColor,
                    readyColor = palette.readyColor,
                    notReadyColor = palette.notReadyColor,
                    entries = permissionEntries,
                    expanded = if (searchActive) true else expansionState.permissionExpanded,
                    onExpandedChange = { expansionState.permissionExpanded = it }
                )

                AboutSearchCard.Component -> AboutComponentCardSection(
                    cardColor = Color(0x2234D399),
                    titleColor = palette.readyColor,
                    subtitleColor = palette.subtitleColor,
                    accent = palette.accent,
                    entries = componentEntries,
                    expanded = if (searchActive) true else expansionState.componentExpanded,
                    onExpandedChange = { expansionState.componentExpanded = it }
                )

                AboutSearchCard.Build -> AboutBuildSdkCardSection(
                    cardColor = palette.buildCardColor,
                    accent = palette.accent,
                    subtitleColor = palette.subtitleColor,
                    expanded = if (searchActive) true else expansionState.buildExpanded,
                    onExpandedChange = { expansionState.buildExpanded = it }
                )

                AboutSearchCard.Ui -> AboutUiFrameworkCardSection(
                    cardColor = palette.uiFrameworkCardColor,
                    accent = palette.accent,
                    subtitleColor = palette.subtitleColor,
                    expanded = if (searchActive) true else expansionState.uiFrameworkExpanded,
                    onExpandedChange = { expansionState.uiFrameworkExpanded = it }
                )

                AboutSearchCard.ProjectLicense -> AboutProjectLicenseCardSection(
                    cardColor = palette.projectLicenseCardColor,
                    accent = palette.accent,
                    subtitleColor = palette.subtitleColor,
                    expanded = if (searchActive) true else expansionState.projectLicenseExpanded,
                    onExpandedChange = { expansionState.projectLicenseExpanded = it },
                    onOpenLicenseUrl = { url ->
                        if (!openExternalUrl(context, url)) {
                            Toast.makeText(context, openLinkFailed, Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                AboutSearchCard.License -> AboutLicenseCardSection(
                    cardColor = palette.licenseCardColor,
                    accent = palette.accent,
                    subtitleColor = palette.subtitleColor,
                    expanded = if (searchActive) true else expansionState.licenseExpanded,
                    onExpandedChange = { expansionState.licenseExpanded = it },
                    onOpenSourceUrl = { url ->
                        if (!openExternalUrl(context, url)) {
                            Toast.makeText(context, openLinkFailed, Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                AboutSearchCard.Lab -> AboutComponentLabCardSection(
                    cardColor = palette.componentLabCardColor,
                    accent = palette.accent,
                    subtitleColor = palette.subtitleColor,
                    expanded = if (searchActive) true else expansionState.componentLabExpanded,
                    onExpandedChange = { expansionState.componentLabExpanded = it },
                    onOpenComponentLab = { DebugComponentLabActivity.launch(context) }
                )
            }
        }
    }

    fun LazyListScope.aboutCategoryCards(category: AboutCategory) {
        val cards = when (category) {
            AboutCategory.Overview -> listOf(AboutSearchCard.App, AboutSearchCard.GitHub)
            AboutCategory.System -> listOf(
                AboutSearchCard.Runtime,
                AboutSearchCard.Network,
                AboutSearchCard.Media,
                AboutSearchCard.Permission,
                AboutSearchCard.Component,
            )

            AboutCategory.Tech -> listOf(
                AboutSearchCard.Build,
                AboutSearchCard.Ui,
                AboutSearchCard.ProjectLicense,
                AboutSearchCard.License,
            )

            AboutCategory.Lab -> listOf(AboutSearchCard.Lab)
        }
        cards.filter(::cardVisible).forEach(::aboutCardItem)
    }

    AppPageScaffold(
        title = stringResource(R.string.about_page_title),
        modifier = Modifier.fillMaxSize(),
        scrollBehavior = scrollBehavior,
        topBarColor = Color.Transparent,
        titleBackdrop = topBarBackdrop,
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
            AboutBottomChrome(
                navigationBarBottom = navigationBarBottom,
                categories = categories,
                selectedPage = pagerState.targetPage.coerceIn(0, categories.lastIndex),
                selectedPagePosition = if (!searchExpanded && pagerState.isScrollInProgress) {
                    pagerState.pagePosition.coerceIn(
                        0f,
                        categories.lastIndex.coerceAtLeast(0).toFloat(),
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
                searchPlaceholder = aboutSearchPlaceholder,
                backdrop = bottomBarBackdrop,
                isLiquidEffectEnabled = true,
                miuixMainNavigationEnabled = miuixMainNavigationEnabled,
                onSelectCategory = selectAboutCategory,
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
                bottomExtra = contentBottomPadding + AppChromeTokens.floatingBottomBarOuterHeight,
                sectionSpacing = 14.dp,
            ) {
                if (matchingSearchTargets.isEmpty()) {
                    item(key = "about_search_empty") {
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
                        aboutCardItem(target.card)
                    }
                }
            }
        } else {
            MainLoadedPager(
                state = pagerState,
                userScrollEnabled = !searchExpanded,
                animationsEnabled = transitionAnimationsEnabled,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = farJumpAlpha.value }
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .layerBackdrop(topBarBackdrop)
                    .layerBackdrop(bottomBarBackdrop),
            ) { pageIndex ->
                val renderHeavyContent = pageIndex == pagerState.currentPage ||
                        pageIndex == pagerState.settledPage ||
                        pageIndex == pagerState.targetPage ||
                        abs(pageIndex - pagerState.pagePosition) <= 1.05f
                if (renderHeavyContent) {
                    val category = categories[pageIndex]
                    val pageListState = when (category) {
                        AboutCategory.Overview -> overviewListState
                        AboutCategory.System -> systemListState
                        AboutCategory.Tech -> techListState
                        AboutCategory.Lab -> labListState
                    }
                    AppPageLazyColumn(
                        innerPadding = innerPadding,
                        state = pageListState,
                        modifier = Modifier.fillMaxSize(),
                        bottomExtra = contentBottomPadding + AppChromeTokens.floatingBottomBarOuterHeight,
                        sectionSpacing = 14.dp,
                    ) {
                        aboutCategoryCards(category)
                    }
                }
            }
        }
    }
}

private fun aboutPagerSwitchDurationMillis(distance: Int): Int {
    return (100 * distance.coerceAtLeast(1) + 100).coerceIn(180, 420)
}
