package os.kei.ui.page.main.home.state

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.onEach
import os.kei.core.ui.snapshot.rememberAppSnapshotFlowManager
import os.kei.ui.page.main.home.HomeCardStatItem
import os.kei.ui.page.main.home.HomeHeaderStatusPillState
import os.kei.ui.page.main.host.pager.MainPageRuntime
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
import os.kei.ui.page.main.widget.motion.resolvedMotionDuration

private const val HOME_HEADER_SINK_PER_HIDDEN_CARD_DP = 22
private val HOME_HERO_AVOIDANCE_SCROLL_DISTANCE_DP = 128.dp

internal data class HomePageHeroMotionState(
    val bgAlpha: () -> Float,
    val hdrSweepProgress: () -> Float,
    val logoHeightDp: Dp,
    val homeHeaderSinkOffset: Dp,
    val avoidanceProgress: () -> Float,
    val iconProgress: () -> Float,
    val titleProgress: () -> Float,
    val summaryProgress: () -> Float,
    val onHeroHeightPxChanged: (Int) -> Unit,
    val onLogoHeightPxChanged: (Int) -> Unit,
    val onLogoAreaBottomChanged: (Float) -> Unit,
    val onIconBottomChanged: (Float) -> Unit,
    val onTitleBottomChanged: (Float) -> Unit,
    val onSummaryBottomChanged: (Float) -> Unit,
)

@Immutable
internal data class HomePageOverviewCardState(
    val homeHeaderStatusPills: List<HomeHeaderStatusPillState>,
    val mcpOverviewStats: List<HomeCardStatItem>,
    val githubOverviewStats: List<HomeCardStatItem>,
    val webDavOverviewStats: List<HomeCardStatItem>,
    val baOverviewStats: List<HomeCardStatItem>,
)

@Composable
internal fun rememberHomePageHeroMotionState(
    lazyListState: LazyListState,
    homeIconHdrEnabled: Boolean,
    runtime: MainPageRuntime,
    hiddenOverviewCardCount: Int,
): HomePageHeroMotionState {
    val density = LocalDensity.current
    var logoHeightPx by remember { mutableIntStateOf(0) }
    var lastListIndex by remember { mutableIntStateOf(0) }
    var lastListOffsetPx by remember { mutableIntStateOf(0) }
    var bgAlpha by remember { mutableFloatStateOf(1f) }
    var logoHeightDp by remember { mutableStateOf(300.dp) }
    var logoAreaY by remember { mutableFloatStateOf(0f) }
    var iconY by remember { mutableFloatStateOf(0f) }
    var titleY by remember { mutableFloatStateOf(0f) }
    var summaryY by remember { mutableFloatStateOf(0f) }
    var initialLogoAreaY by remember { mutableFloatStateOf(0f) }
    val avoidanceScrollDistancePx =
        remember(density) {
            with(density) { HOME_HERO_AVOIDANCE_SCROLL_DISTANCE_DP.toPx() }
        }
    val transitionAnimationsEnabled = LocalTransitionAnimationsEnabled.current
    val hdrSweepProgressProvider =
        if (
            homeIconHdrEnabled &&
            transitionAnimationsEnabled &&
            runtime.isDataActive &&
            !runtime.isPagerScrollInProgress
        ) {
            val hdrSweep = rememberInfiniteTransition(label = "kei_hdr_sweep")
            val animated =
                hdrSweep.animateFloat(
                    initialValue = -0.35f,
                    targetValue = 1.35f,
                    animationSpec =
                        infiniteRepeatable(
                            animation =
                                tween(
                                    durationMillis = resolvedMotionDuration(4600, transitionAnimationsEnabled),
                                    easing = LinearEasing,
                                ),
                        ),
                    label = "kei_hdr_sweep_progress",
                )
            remember(animated) { { animated.value } }
        } else {
            remember { { 0f } }
        }
    var iconProgress by remember { mutableFloatStateOf(0f) }
    var titleProgress by remember { mutableFloatStateOf(0f) }
    var summaryProgress by remember { mutableFloatStateOf(0f) }
    var avoidanceProgress by remember { mutableFloatStateOf(0f) }
    val snapshotFlowManager = rememberAppSnapshotFlowManager()
    val bgAlphaProvider = remember { { bgAlpha } }
    val avoidanceProgressProvider = remember { { avoidanceProgress } }
    val iconProgressProvider = remember { { iconProgress } }
    val titleProgressProvider = remember { { titleProgress } }
    val summaryProgressProvider = remember { { summaryProgress } }

    LaunchedEffect(lazyListState, snapshotFlowManager) {
        snapshotFlowManager
            .snapshotFlow {
                lazyListState.firstVisibleItemIndex to lazyListState.firstVisibleItemScrollOffset
            }.onEach { (index, offset) ->
                lastListIndex = index
                lastListOffsetPx = offset
                val nextBgAlpha =
                    1f -
                        homeHeroScrollProgress(
                            index = index,
                            offsetPx = offset,
                            logoHeightPx = logoHeightPx,
                        )
                if (bgAlpha != nextBgAlpha) bgAlpha = nextBgAlpha

                if (index > 0) {
                    if (avoidanceProgress != 1f) avoidanceProgress = 1f
                    if (iconProgress != 1f) iconProgress = 1f
                    if (titleProgress != 1f) titleProgress = 1f
                    if (summaryProgress != 1f) summaryProgress = 1f
                    return@onEach
                }
                val nextAvoidanceProgress =
                    homeHeroAvoidanceProgress(
                        offsetPx = offset.toFloat(),
                        distancePx = avoidanceScrollDistancePx,
                    )
                if (avoidanceProgress != nextAvoidanceProgress) {
                    avoidanceProgress = nextAvoidanceProgress
                }

                if (initialLogoAreaY == 0f && logoAreaY > 0f) {
                    initialLogoAreaY = logoAreaY
                }
                val refLogoAreaY = if (initialLogoAreaY > 0f) initialLogoAreaY else logoAreaY

                val stage1 = (refLogoAreaY - summaryY).coerceAtLeast(1f)
                val stage2 = (summaryY - titleY).coerceAtLeast(1f)
                val stage3 = (titleY - iconY).coerceAtLeast(1f)

                val summaryDelay = stage1 * 0.5f
                val nextSummaryProgress =
                    ((offset.toFloat() - summaryDelay) / (stage1 - summaryDelay).coerceAtLeast(1f))
                        .coerceIn(0f, 1f)
                val nextTitleProgress =
                    ((offset.toFloat() - stage1) / stage2)
                        .coerceIn(0f, 1f)
                val nextIconProgress =
                    ((offset.toFloat() - stage1 - stage2) / stage3)
                        .coerceIn(0f, 1f)
                if (summaryProgress != nextSummaryProgress) summaryProgress = nextSummaryProgress
                if (titleProgress != nextTitleProgress) titleProgress = nextTitleProgress
                if (iconProgress != nextIconProgress) iconProgress = nextIconProgress
            }.collect { }
    }

    return remember(
        bgAlphaProvider,
        hdrSweepProgressProvider,
        logoHeightDp,
        hiddenOverviewCardCount,
        avoidanceProgressProvider,
        iconProgressProvider,
        titleProgressProvider,
        summaryProgressProvider,
        density,
    ) {
        HomePageHeroMotionState(
            bgAlpha = bgAlphaProvider,
            hdrSweepProgress = hdrSweepProgressProvider,
            logoHeightDp = logoHeightDp,
            homeHeaderSinkOffset = (hiddenOverviewCardCount * HOME_HEADER_SINK_PER_HIDDEN_CARD_DP).dp,
            avoidanceProgress = avoidanceProgressProvider,
            iconProgress = iconProgressProvider,
            titleProgress = titleProgressProvider,
            summaryProgress = summaryProgressProvider,
            onHeroHeightPxChanged = { heightPx ->
                with(density) { logoHeightDp = heightPx.toDp() }
            },
            onLogoHeightPxChanged = { heightPx ->
                logoHeightPx = heightPx
                val nextBgAlpha =
                    1f -
                        homeHeroScrollProgress(
                            index = lastListIndex,
                            offsetPx = lastListOffsetPx,
                            logoHeightPx = heightPx,
                        )
                if (bgAlpha != nextBgAlpha) bgAlpha = nextBgAlpha
            },
            onLogoAreaBottomChanged = { logoAreaY = it },
            onIconBottomChanged = { bottom -> if (iconY == 0f) iconY = bottom },
            onTitleBottomChanged = { bottom -> if (titleY == 0f) titleY = bottom },
            onSummaryBottomChanged = { bottom -> if (summaryY == 0f) summaryY = bottom },
        )
    }
}

private fun homeHeroScrollProgress(
    index: Int,
    offsetPx: Int,
    logoHeightPx: Int,
): Float {
    if (logoHeightPx <= 0) return 0f
    return if (index > 0) {
        1f
    } else {
        (offsetPx.toFloat() / logoHeightPx).coerceIn(0f, 1f)
    }
}

private fun homeHeroAvoidanceProgress(
    offsetPx: Float,
    distancePx: Float,
): Float {
    val progress = (offsetPx / distancePx.coerceAtLeast(1f)).coerceIn(0f, 1f)
    return progress * progress * (3f - 2f * progress)
}

@Composable
internal fun rememberHomePageOverviewCardState(
    homeStatusMcp: String,
    homeStatusGitHub: String,
    homeStatusWebDav: String,
    homeStatusShizuku: String,
    mcpRunning: Boolean,
    cacheStateColor: Color,
    webDavConfigured: Boolean,
    shizukuGranted: Boolean,
    runningColor: Color,
    stoppedColor: Color,
    inactiveColor: Color,
    shizukuStatusLine: String,
    mcpFocusLine: String,
    githubFocusLine: String,
    baFocusLine: String,
    homeStatStatus: String,
    mcpStatusText: String,
    homeStatRuntime: String,
    mcpRuntimeText: String,
    homeStatClients: String,
    mcpConnectedClients: Int,
    homeStatNetwork: String,
    networkModeText: String,
    homeStatPort: String,
    mcpPort: Int,
    homeStatToken: String,
    mcpTokenStatusText: String,
    homeStatStableUpdates: String,
    githubUpdatableLine: String,
    homeStatPreReleaseUpdates: String,
    githubPreReleaseUpdateLine: String,
    homeStatFailed: String,
    githubFailedLine: String,
    homeStatTracked: String,
    trackedCountLine: String,
    homeStatCached: String,
    cacheHitCountLine: String,
    homeStatCacheState: String,
    githubCacheFreshnessLine: String,
    showCacheFreshnessInCards: Boolean,
    homeStatShare: String,
    githubShareLine: String,
    githubPendingShareImport: Boolean,
    homeStatLastUpdate: String,
    githubLastUpdateLine: String,
    webDavConfiguredLine: String,
    homeStatAutoSync: String,
    webDavAutoSyncLine: String,
    homeStatSyncItems: String,
    webDavSyncItemsLine: String,
    homeStatLastFullSync: String,
    webDavLastFullSyncLine: String,
    baActivationLine: String,
    homeStatAp: String,
    baApLine: String,
    homeStatCafeAp: String,
    baCafeApLine: String,
    homeStatApRemaining: String,
    baApRemainingLine: String,
    homeStatBaServer: String,
    baServerLine: String,
    homeStatBaNotify: String,
    baNotifyLine: String,
    baCacheFreshnessLine: String,
): HomePageOverviewCardState {
    val homeHeaderStatusPills =
        remember(
            homeStatusMcp,
            homeStatusGitHub,
            homeStatusWebDav,
            homeStatusShizuku,
            mcpRunning,
            cacheStateColor,
            webDavConfigured,
            shizukuGranted,
            runningColor,
            stoppedColor,
            inactiveColor,
        ) {
            listOf(
                HomeHeaderStatusPillState(
                    label = homeStatusMcp,
                    color = if (mcpRunning) runningColor else stoppedColor,
                    minWidth = 62.dp,
                ),
                HomeHeaderStatusPillState(
                    label = homeStatusGitHub,
                    color = cacheStateColor,
                    minWidth = 72.dp,
                ),
                HomeHeaderStatusPillState(
                    label = homeStatusWebDav,
                    color = if (webDavConfigured) runningColor else stoppedColor,
                    minWidth = 78.dp,
                ),
                HomeHeaderStatusPillState(
                    label = homeStatusShizuku,
                    color = if (shizukuGranted) runningColor else stoppedColor,
                    minWidth = 70.dp,
                    contentPadding =
                        androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 8.dp,
                            vertical = 5.dp,
                        ),
                ),
            )
        }
    val mcpOverviewStats =
        remember(
            homeStatStatus,
            mcpStatusText,
            homeStatRuntime,
            mcpRuntimeText,
            homeStatClients,
            mcpConnectedClients,
            homeStatNetwork,
            networkModeText,
            homeStatPort,
            mcpPort,
            homeStatToken,
            mcpTokenStatusText,
        ) {
            listOf(
                HomeCardStatItem(label = homeStatStatus, value = mcpStatusText, emphasize = true),
                HomeCardStatItem(label = homeStatRuntime, value = mcpRuntimeText, emphasize = true),
                HomeCardStatItem(label = homeStatClients, value = mcpConnectedClients.toString()),
                HomeCardStatItem(label = homeStatNetwork, value = networkModeText),
                HomeCardStatItem(label = homeStatPort, value = mcpPort.toString()),
                HomeCardStatItem(label = homeStatToken, value = mcpTokenStatusText),
            )
        }
    val githubOverviewStats =
        remember(
            homeStatStableUpdates,
            githubUpdatableLine,
            homeStatPreReleaseUpdates,
            githubPreReleaseUpdateLine,
            homeStatFailed,
            githubFailedLine,
            homeStatTracked,
            trackedCountLine,
            homeStatCached,
            cacheHitCountLine,
            homeStatCacheState,
            githubCacheFreshnessLine,
            showCacheFreshnessInCards,
            homeStatShare,
            githubShareLine,
            githubPendingShareImport,
            homeStatLastUpdate,
            githubLastUpdateLine,
        ) {
            val shareStat =
                HomeCardStatItem(
                    label = homeStatShare,
                    value = githubShareLine,
                    emphasize = githubPendingShareImport,
                )
            val baseStats =
                buildList {
                    add(
                        HomeCardStatItem(
                            label = homeStatStableUpdates,
                            value = githubUpdatableLine,
                            emphasize = true,
                        ),
                    )
                    add(
                        HomeCardStatItem(
                            label = homeStatPreReleaseUpdates,
                            value = githubPreReleaseUpdateLine,
                            emphasize = true,
                        ),
                    )
                    add(HomeCardStatItem(label = homeStatFailed, value = githubFailedLine))
                    add(HomeCardStatItem(label = homeStatTracked, value = trackedCountLine))
                    add(HomeCardStatItem(label = homeStatCached, value = cacheHitCountLine))
                    if (showCacheFreshnessInCards) {
                        add(HomeCardStatItem(label = homeStatCacheState, value = githubCacheFreshnessLine))
                    }
                    add(HomeCardStatItem(label = homeStatLastUpdate, value = githubLastUpdateLine))
                }
            if (githubPendingShareImport) {
                listOf(shareStat) + baseStats
            } else {
                baseStats.take(5) + shareStat + baseStats.drop(5)
            }
        }
    val webDavOverviewStats =
        remember(
            homeStatStatus,
            webDavConfiguredLine,
            homeStatAutoSync,
            webDavAutoSyncLine,
            homeStatSyncItems,
            webDavSyncItemsLine,
            homeStatLastFullSync,
            webDavLastFullSyncLine,
        ) {
            listOf(
                HomeCardStatItem(
                    label = homeStatStatus,
                    value = webDavConfiguredLine,
                    emphasize = true
                ),
                HomeCardStatItem(
                    label = homeStatAutoSync,
                    value = webDavAutoSyncLine,
                    emphasize = true
                ),
                HomeCardStatItem(label = homeStatSyncItems, value = webDavSyncItemsLine),
                HomeCardStatItem(label = homeStatLastFullSync, value = webDavLastFullSyncLine),
            )
        }
    val baOverviewStats =
        remember(
            homeStatStatus,
            baActivationLine,
            homeStatAp,
            baApLine,
            homeStatCafeAp,
            baCafeApLine,
            homeStatApRemaining,
            baApRemainingLine,
            homeStatBaServer,
            baServerLine,
            homeStatBaNotify,
            baNotifyLine,
            homeStatCacheState,
            showCacheFreshnessInCards,
            baCacheFreshnessLine,
        ) {
            buildList {
                add(
                    HomeCardStatItem(
                        label = homeStatStatus,
                        value = baActivationLine,
                        emphasize = true,
                    ),
                )
                add(HomeCardStatItem(label = homeStatAp, value = baApLine, emphasize = true))
                add(HomeCardStatItem(label = homeStatApRemaining, value = baApRemainingLine))
                add(HomeCardStatItem(label = homeStatCafeAp, value = baCafeApLine))
                add(HomeCardStatItem(label = homeStatBaServer, value = baServerLine))
                add(HomeCardStatItem(label = homeStatBaNotify, value = baNotifyLine))
                if (showCacheFreshnessInCards) {
                    add(HomeCardStatItem(label = homeStatCacheState, value = baCacheFreshnessLine))
                }
            }
        }

    return remember(
        homeHeaderStatusPills,
        mcpOverviewStats,
        githubOverviewStats,
        webDavOverviewStats,
        baOverviewStats,
    ) {
        HomePageOverviewCardState(
            homeHeaderStatusPills = homeHeaderStatusPills,
            mcpOverviewStats = mcpOverviewStats,
            githubOverviewStats = githubOverviewStats,
            webDavOverviewStats = webDavOverviewStats,
            baOverviewStats = baOverviewStats,
        )
    }
}
