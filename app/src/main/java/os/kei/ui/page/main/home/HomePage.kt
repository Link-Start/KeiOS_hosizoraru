@file:Suppress("FunctionName")

package os.kei.ui.page.main.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import os.kei.R
import os.kei.core.ui.effect.background.BgEffectBackground
import os.kei.feature.home.model.HomeAppOverview
import os.kei.feature.home.model.HomeBaOverview
import os.kei.feature.home.model.HomeGitHubOverview
import os.kei.feature.home.model.HomeMcpOverview
import os.kei.feature.home.model.HomeOverviewCard
import os.kei.feature.home.model.HomeWebDavOverview
import os.kei.feature.home.model.defaultHomeOverviewCards
import os.kei.ui.page.main.home.state.rememberHomePageContentState
import os.kei.ui.page.main.home.state.rememberHomePageHeroMotionState
import os.kei.ui.page.main.home.state.rememberHomePageOverviewCardState
import os.kei.ui.page.main.host.pager.MainPageRuntime
import os.kei.ui.page.main.model.BottomPage
import os.kei.ui.page.main.os.appLucideInfoIcon
import os.kei.ui.page.main.os.appLucideLayersIcon
import os.kei.ui.page.main.os.osLucideSettingsIcon
import os.kei.ui.testing.KeiOsTestTags
import os.kei.ui.page.main.widget.chrome.AppScaffold
import os.kei.ui.page.main.widget.chrome.AppTopBarSection
import os.kei.ui.page.main.widget.chrome.AppTopEndActionBarOverlay
import os.kei.ui.page.main.widget.chrome.BindLazyListScrollBoundsEffect
import os.kei.ui.page.main.widget.chrome.appPageSideGutterStart
import os.kei.ui.page.main.widget.chrome.appPageSideGutterEnd
import os.kei.ui.page.main.widget.chrome.LiquidToolbar
import os.kei.ui.page.main.widget.chrome.LiquidToolbarAction
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.kyant.backdrop.backdrops.layerBackdrop as kyantLayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop as rememberActionBarBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop as rememberMiuixLayerBackdrop
import os.kei.core.privilege.PrivilegeStatus

/**
 * Shortest viewport the tall hero is allowed on.
 *
 * Chosen from two measured geometries rather than by feel. On the API 37 AVD a **640dp**-tall viewport
 * (2856×1280 at density 320, a large screen in landscape) overflows: the hero pushes the overview pills under
 * the floating dock. The same AVD in portrait is **952dp** tall and has room to spare. So the boundary lies
 * between them, and 700dp sits above the failing case with margin.
 *
 * Deliberately not tighter. Every phone this app can install on — `minSdk 35` — is 850–1000dp tall in portrait
 * at its stock density, so no phone falls below this in portrait and gets the compact hero by accident. The
 * 1280×720-class device that would have is two Android versions below the floor.
 */
internal val HomePageTallHeroMinHeight: Dp = 700.dp
private val HOME_PAGE_HERO_TOP_EXTRA = 24.dp

/**
 * Whether the viewport is too short for the tall hero, regardless of orientation.
 *
 * This used to read `availableWidth > availableHeight && availableHeight <= 480.dp`, which encodes "a phone
 * held sideways" rather than the constraint that actually matters. Both halves were wrong once the app started
 * targeting SDK 37:
 *
 * - **The orientation term.** On a large screen (`sw >= 600dp`) Android 16 already ignores an activity's
 *   orientation request for `targetSdk >= 36`, and Android 17 removes the
 *   `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY` opt-out entirely at `targetSdk >= 37`. This app declares
 *   `sensorPortrait`, so before that it simply never saw a wide viewport on a tablet — the geometry was
 *   unreachable, and a rule that keyed on it could not be caught being wrong.
 * - **The 480dp cutoff.** A phone in landscape is roughly 426dp tall, so 480dp covered it. A *large screen* in
 *   landscape is 600–800dp tall: too tall to trip the cutoff, and still far shorter than the ~950dp portrait
 *   height the tall hero is drawn for. Measured on the API 37 AVD at 2856×1280 / density 320 — a 1428×640dp
 *   viewport — the hero pushed the overview pill rows underneath the floating dock at rest. Scrollable, so
 *   nothing was unreachable, but the first paint read as broken.
 *
 * So the question is only ever "is there room", and [HomePageTallHeroMinHeight] is the budget. Dropping the
 * orientation term is safe in the other direction too: a tablet in *portrait* is ~1400dp tall and keeps the
 * tall hero, which the old rule also did but for the wrong reason.
 */
internal fun homePageUsesCompactLandscapeLayout(
    availableWidth: Dp,
    availableHeight: Dp,
): Boolean = availableHeight < HomePageTallHeroMinHeight

internal fun homePageLogoTopPadding(
    scaffoldTopPadding: Dp,
    contentTopPadding: Dp,
    compactHeightPresentation: Boolean,
): Dp =
    if (compactHeightPresentation) {
        scaffoldTopPadding
    } else {
        scaffoldTopPadding + contentTopPadding + HOME_PAGE_HERO_TOP_EXTRA
    }

@Composable
fun HomePage(
    privilegeStatus: PrivilegeStatus,
    homeAppOverview: HomeAppOverview = HomeAppOverview(),
    mcpOverview: HomeMcpOverview = HomeMcpOverview(),
    homeGitHubOverview: HomeGitHubOverview = HomeGitHubOverview(),
    homeWebDavOverview: HomeWebDavOverview = HomeWebDavOverview(),
    homeBaOverview: HomeBaOverview = HomeBaOverview(),
    runtimeNowMs: Long,
    homeIconHdrEnabled: Boolean,
    homeDynamicFullEffectEnabled: Boolean = false,
    /** Whether the pager is the top nav entry rather than a layer under a settled route. */
    pagerAtTop: Boolean = true,
    runtime: MainPageRuntime = MainPageRuntime(),
    visibleBottomPages: Set<BottomPage>,
    visibleOverviewCards: Set<HomeOverviewCard> = defaultHomeOverviewCards(),
    showCacheFreshnessInCards: Boolean = false,
    showBottomPageEditor: Boolean = false,
    onBottomPageVisibilityChange: (BottomPage, Boolean) -> Unit,
    onOverviewCardVisibilityChange: (HomeOverviewCard, Boolean) -> Unit = { _, _ -> },
    onCacheFreshnessVisibilityChange: (Boolean) -> Unit = {},
    onBottomPageEditorVisibleChange: (Boolean) -> Unit = {},
    onOpenWebDavSync: () -> Unit = {},
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val configuration = LocalConfiguration.current
    val compactLandscapeLayout =
        homePageUsesCompactLandscapeLayout(
            availableWidth = configuration.screenWidthDp.dp,
            availableHeight = configuration.screenHeightDp.dp,
        )
    val lazyListState = rememberLazyListState()
    val pageScope = rememberCoroutineScope()
    BindLazyListScrollBoundsEffect(
        listState = lazyListState,
        isActive = runtime.isPageActive,
        onScrollBoundsChange = runtime.onScrollBoundsChange,
    )

    val effectBackgroundEnabled = runtime.isPageActive
    // `pagerAtTop` is the same argument the modal-presentation gate in BgEffectBackground makes, one
    // layer out: a settled full-screen route covers this background completely, so the drift cannot
    // be seen -- but the loop still invalidates the draw tree and every glass surface above it still
    // re-rasterizes. Measured on the API 37 phone AVD: Settings open over Home rendered 166 frames in
    // a 3s dwell at p50 36ms, and three captures a second apart were pixel-identical. That is a page
    // rendering 55fps of the same image. `routeAtTop` already stops the pager's backdrop *capture*
    // (MainPagerLayout); this stops the invalidation that was still driving it.
    //
    // Only playback stops. `animTime` accrues from real elapsed time and re-anchors on resume, so the
    // drift picks up where it left off when the route leaves rather than jumping.
    val homeDynamicActive =
        pagerAtTop &&
            (
                runtime.isDataActive ||
                    (homeDynamicFullEffectEnabled && runtime.isPageActive)
            )
    val dynamicBackgroundEnabled =
        homeDynamicActive &&
            (homeDynamicFullEffectEnabled || !runtime.isPagerScrollInProgress)
    val fullBackdropEffectsEnabled =
        runtime.isPageActive &&
            (
                homeDynamicFullEffectEnabled ||
                    !runtime.isPagerScrollInProgress
            )
    val foregroundBlurActive = fullBackdropEffectsEnabled
    val surfaceColor = MiuixTheme.colorScheme.surface
    val actionBarBackdrop =
        rememberActionBarBackdrop {
            drawRect(surfaceColor)
            drawContent()
        }
    val homeCardBackdrop =
        if (fullBackdropEffectsEnabled) {
            rememberActionBarBackdrop {
                drawRect(surfaceColor)
                drawContent()
            }
        } else {
            null
        }
    val foregroundBackdrop =
        if (foregroundBlurActive) {
            rememberMiuixLayerBackdrop()
        } else {
            null
        }
    val contentState =
        rememberHomePageContentState(
            privilegeStatus = privilegeStatus,
            appOverview = homeAppOverview,
            mcpOverview = mcpOverview,
            githubOverview = homeGitHubOverview,
            webDavOverview = homeWebDavOverview,
            baOverview = homeBaOverview,
            runtimeNowMs = runtimeNowMs,
        )

    val layersIcon = appLucideLayersIcon()
    val aboutIcon = appLucideInfoIcon()
    val settingsIcon = osLucideSettingsIcon()
    val editBottomPagesContentDescription = stringResource(R.string.home_cd_edit_bottom_pages)
    val aboutContentDescription = stringResource(R.string.about_page_title)
    val settingsContentDescription = stringResource(R.string.settings_title)
    val homeActionItems =
        remember(
            editBottomPagesContentDescription,
            aboutContentDescription,
            settingsContentDescription,
            onBottomPageEditorVisibleChange,
            onOpenAbout,
            onOpenSettings,
        ) {
            listOf(
                LiquidToolbarAction(
                    icon = layersIcon,
                    contentDescription = editBottomPagesContentDescription,
                    onClick = {
                        onBottomPageEditorVisibleChange(true)
                    },
                ),
                LiquidToolbarAction(
                    icon = aboutIcon,
                    contentDescription = aboutContentDescription,
                    testTag = KeiOsTestTags.HomeAboutButton,
                    onClick = {
                        onOpenAbout()
                    },
                ),
                LiquidToolbarAction(
                    icon = settingsIcon,
                    contentDescription = settingsContentDescription,
                    testTag = KeiOsTestTags.HomeSettingsButton,
                    onClick = {
                        onOpenSettings()
                    },
                ),
            )
        }
    val hiddenOverviewCardCount = (HomeOverviewCard.entries.size - visibleOverviewCards.size).coerceAtLeast(0)
    val heroMotionState =
        rememberHomePageHeroMotionState(
            lazyListState = lazyListState,
            homeIconHdrEnabled = homeIconHdrEnabled,
            runtime = runtime,
            hiddenOverviewCardCount = hiddenOverviewCardCount,
        )
    val overviewCardState =
        rememberHomePageOverviewCardState(
            content = contentState,
            mcpOverview = mcpOverview,
            githubOverview = homeGitHubOverview,
            webDavOverview = homeWebDavOverview,
            baOverview = homeBaOverview,
            showCacheFreshnessInCards = showCacheFreshnessInCards,
        )

    Box(modifier = Modifier.fillMaxSize()) {
        AppScaffold(
            topBar = {
                AppTopBarSection(
                    title = "",
                    largeTitle = "",
                    color = Color.Transparent,
                    onTitleClick = {
                        pageScope.launch {
                            lazyListState.animateScrollToItem(0)
                        }
                    },
                )
            },
        ) { innerPadding ->
            HomePageControlSheet(
                show = showBottomPageEditor,
                actionBarBackdrop = actionBarBackdrop,
                visibleBottomPages = visibleBottomPages,
                visibleOverviewCards = visibleOverviewCards,
                homeSheetTitle = stringResource(R.string.home_sheet_bottom_pages_title),
                tableTitle = stringResource(R.string.home_sheet_table_title),
                tableDesc = stringResource(R.string.home_sheet_table_desc),
                homeCardMcp = contentState.homeCardMcp,
                homeCardGitHub = contentState.homeCardGitHub,
                homeCardWebDav = contentState.homeCardWebDav,
                homeCardBa = contentState.homeCardBa,
                showCacheFreshnessInCards = showCacheFreshnessInCards,
                cacheFreshnessToggleLabel = stringResource(R.string.home_sheet_show_cache_freshness),
                cacheFreshnessToggleDesc = stringResource(R.string.home_sheet_show_cache_freshness_desc),
                debugSectionTitle = stringResource(R.string.home_sheet_debug_title),
                onDismissRequest = { onBottomPageEditorVisibleChange(false) },
                onBottomPageVisibilityChange = onBottomPageVisibilityChange,
                onOverviewCardVisibilityChange = onOverviewCardVisibilityChange,
                onCacheFreshnessVisibilityChange = onCacheFreshnessVisibilityChange,
            )

            val horizontalSafeInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues()
            // Home builds its own padding rather than going through `appPageContentPadding`, so it has to add
            // the large-screen gutter itself. The hero gets it too: the pill rows under the wordmark are the
            // same full-width rows as everywhere else, and leaving them uncapped while the overview cards
            // below them narrow would split the page down the middle. Zero on phones.
            val sideGutterStart = appPageSideGutterStart()
            val sideGutterEnd = appPageSideGutterEnd()
            val listContentPadding =
                PaddingValues(
                    start = horizontalSafeInsets.calculateStartPadding(layoutDirection) + sideGutterStart,
                    top = innerPadding.calculateTopPadding() + runtime.contentTopPadding,
                    end = horizontalSafeInsets.calculateEndPadding(layoutDirection) + sideGutterEnd,
                    bottom = innerPadding.calculateBottomPadding() + runtime.contentBottomPadding + 16.dp,
                )
            val logoPadding =
                PaddingValues(
                    top =
                        homePageLogoTopPadding(
                            scaffoldTopPadding = innerPadding.calculateTopPadding(),
                            contentTopPadding = runtime.contentTopPadding,
                            compactHeightPresentation = compactLandscapeLayout,
                        ),
                    start = horizontalSafeInsets.calculateStartPadding(layoutDirection) + sideGutterStart,
                    end = horizontalSafeInsets.calculateEndPadding(layoutDirection) + sideGutterEnd,
                )

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .kyantLayerBackdrop(actionBarBackdrop),
            ) {
                BgEffectBackground(
                    dynamicBackground = dynamicBackgroundEnabled,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .then(
                                homeCardBackdrop?.let { backdrop ->
                                    Modifier.kyantLayerBackdrop(backdrop)
                                } ?: Modifier,
                            ),
                    bgModifier = foregroundBackdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier,
                    effectBackground = effectBackgroundEnabled,
                    isFullSize = true,
                    alpha = heroMotionState.bgAlpha,
                ) {
                    HomePageHero(
                        foregroundBackdrop = foregroundBackdrop,
                        foregroundBlurEnabled = foregroundBlurActive,
                        homeIconHdrEnabled = homeIconHdrEnabled,
                        hdrSweepProgress = heroMotionState.hdrSweepProgress,
                        homeHeaderSinkOffset = heroMotionState.homeHeaderSinkOffset,
                        logoPadding = logoPadding,
                        layoutDirection = layoutDirection,
                        homeAppName = contentState.homeAppName,
                        homeTagline = contentState.homeTagline,
                        appVersionText = contentState.appVersionText,
                        avoidanceProgress = heroMotionState.avoidanceProgress,
                        iconProgress = heroMotionState.iconProgress,
                        titleProgress = heroMotionState.titleProgress,
                        summaryProgress = heroMotionState.summaryProgress,
                        statusPills = overviewCardState.homeHeaderStatusPills,
                        compactHeightPresentation = compactLandscapeLayout,
                        onHeroHeightChanged = heroMotionState.onHeroHeightPxChanged,
                        onIconBottomChanged = heroMotionState.onIconBottomChanged,
                        onTitleBottomChanged = heroMotionState.onTitleBottomChanged,
                        onSummaryBottomChanged = heroMotionState.onSummaryBottomChanged,
                    )
                }

                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = listContentPadding,
                ) {
                    item(
                        key = "logo_spacer",
                        contentType = "home_logo_spacer",
                    ) {
                        HomePageHeroSpacer(
                            logoHeightDp = heroMotionState.logoHeightDp,
                            logoPadding = logoPadding,
                            listContentPadding = listContentPadding,
                            homeHeaderSinkOffset = heroMotionState.homeHeaderSinkOffset,
                            compactHeightPresentation = compactLandscapeLayout,
                            onLogoHeightPxChanged = heroMotionState.onLogoHeightPxChanged,
                            onLogoAreaBottomChanged = heroMotionState.onLogoAreaBottomChanged,
                        )
                    }

                    item(
                        key = "home_content",
                        contentType = "home_content",
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = listContentPadding.calculateBottomPadding()),
                        ) {
                            HomePageOverviewCards(
                                visibleOverviewCards = visibleOverviewCards,
                                homeCardBackdrop = homeCardBackdrop,
                                blurEnabled = fullBackdropEffectsEnabled,
                                homeNa = contentState.homeNa,
                                mcpPills = overviewCardState.mcpOverviewPills,
                                githubPills = overviewCardState.githubOverviewPills,
                                onOpenWebDavSync = onOpenWebDavSync,
                                webDavPills = overviewCardState.webDavOverviewPills,
                                baPills = overviewCardState.baOverviewPills,
                            )
                        }
                    }
                }
            }
        }

        AppTopEndActionBarOverlay {
            LiquidToolbar(
                backdrop = actionBarBackdrop,
                actions = homeActionItems,
            )
        }
    }
}
