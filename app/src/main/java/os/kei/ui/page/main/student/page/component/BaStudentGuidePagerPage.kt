@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.page.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.flow.distinctUntilChanged
import os.kei.R
import os.kei.core.ui.snapshot.rememberAppSnapshotFlowManager
import os.kei.ui.page.main.student.BaStudentGuideInfo
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.GuideBottomTab
import os.kei.ui.page.main.student.page.state.buildBaStudentGuidePagerHeaderState
import os.kei.ui.page.main.student.page.state.resolveBaStudentGuideTabRenderState
import os.kei.ui.page.main.student.tabcontent.renderBaStudentGuideTabContent
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.core.AppAronaLoadingPanel
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.glass.LiquidCircularProgressBar
import os.kei.ui.page.main.widget.glass.LiquidInfoBlock
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun BaStudentGuidePagerPage(
    sourceUrl: String,
    info: BaStudentGuideInfo?,
    error: String?,
    pageIndex: Int,
    bottomTabs: List<GuideBottomTab>,
    pagerState: PagerState,
    activationCount: Int,
    surfaceColor: Color,
    accent: Color,
    innerPadding: PaddingValues,
    syncProgress: Float,
    galleryCacheRevision: Int,
    selectedVoiceLanguage: String,
    playingVoiceUrl: String,
    isVoicePlaying: Boolean,
    voicePlayProgress: Float,
    bgmFavoriteAudioUrls: Set<String>,
    profileLinkTitles: Map<String, String>,
    profileLinkMissingLinks: Set<String>,
    isNpcSatelliteGuide: Boolean,
    mediaAdaptiveRotationEnabled: Boolean,
    includeTargetPageInHeavyRender: Boolean,
    nestedScrollConnection: NestedScrollConnection,
    onOpenExternal: (String) -> Unit,
    onOpenGuide: (String) -> Unit,
    onSaveMedia: (String, String) -> Unit,
    onSaveMediaPack: (List<Pair<String, String>>, String) -> Unit,
    onToggleBgmFavorite: (GuideBgmFavoriteItem) -> Unit,
    onRequestProfileLinkTitles: (List<String>) -> Unit,
    onToggleVoicePlayback: (String) -> Unit,
    onScrollBoundsChange: (canScrollBackward: Boolean, canScrollForward: Boolean) -> Unit,
    onListScrollInProgressChange: (Boolean) -> Unit,
    onSelectedVoiceLanguageChange: (String) -> Unit,
) {
    val context = LocalContext.current
    val tabRenderState =
        remember(
            pageIndex,
            bottomTabs,
            pagerState.currentPage,
            pagerState.settledPage,
            pagerState.targetPage,
            includeTargetPageInHeavyRender,
            playingVoiceUrl,
            isVoicePlaying,
            voicePlayProgress,
            selectedVoiceLanguage,
        ) {
            resolveBaStudentGuideTabRenderState(
                pageIndex = pageIndex,
                bottomTabs = bottomTabs,
                currentPage = pagerState.currentPage,
                settledPage = pagerState.settledPage,
                targetPage = pagerState.targetPage,
                includeTargetPageInHeavyRender = includeTargetPageInHeavyRender,
                playingVoiceUrl = playingVoiceUrl,
                isVoicePlaying = isVoicePlaying,
                voicePlayProgress = voicePlayProgress,
                selectedVoiceLanguage = selectedVoiceLanguage,
            )
        }
    val pageListState =
        rememberSaveable(
            sourceUrl,
            tabRenderState.activeBottomTab.name,
            saver = LazyListState.Saver,
        ) {
            LazyListState()
        }
    val pageBackdrop: LayerBackdrop =
        key("page-$activationCount-$sourceUrl-$pageIndex") {
            rememberLayerBackdrop {
                drawRect(surfaceColor)
                drawContent()
            }
        }
    val isActivePage = pageIndex == pagerState.currentPage
    val snapshotFlowManager = rememberAppSnapshotFlowManager()
    LaunchedEffect(pageListState, isActivePage, snapshotFlowManager) {
        if (!isActivePage) return@LaunchedEffect
        var lastScrollBounds: Pair<Boolean, Boolean>? = null
        var lastScrollInProgress: Boolean? = null
        snapshotFlowManager
            .snapshotFlow {
                Triple(
                    pageListState.canScrollBackward,
                    pageListState.canScrollForward,
                    pageListState.isScrollInProgress,
                )
            }.distinctUntilChanged()
            .collect { (canScrollBackward, canScrollForward, scrolling) ->
                val nextScrollBounds = canScrollBackward to canScrollForward
                if (lastScrollBounds != nextScrollBounds) {
                    lastScrollBounds = nextScrollBounds
                    onScrollBoundsChange(canScrollBackward, canScrollForward)
                }
                if (lastScrollInProgress != scrolling) {
                    lastScrollInProgress = scrolling
                    onListScrollInProgressChange(scrolling)
                }
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (tabRenderState.shouldRenderHeavyContent) {
            val activeBottomTabLabel = stringResource(tabRenderState.activeBottomTab.labelRes)
            val headerState =
                remember(
                    tabRenderState.activeBottomTab,
                    activeBottomTabLabel,
                    sourceUrl,
                    info,
                    error,
                ) {
                    buildBaStudentGuidePagerHeaderState(
                        tabLabel = activeBottomTabLabel,
                        sourceUrl = sourceUrl,
                        info = info,
                        error = error,
                    )
                }

            LazyColumn(
                state = pageListState,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .nestedScroll(nestedScrollConnection),
                contentPadding =
                    PaddingValues(
                        top = innerPadding.calculateTopPadding() + AppChromeTokens.topBarToHeaderGap,
                        bottom = innerPadding.calculateBottomPadding() + 16.dp,
                        start = 16.dp,
                        end = 16.dp,
                    ),
            ) {
                item {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement =
                            androidx.compose.foundation.layout.Arrangement
                                .spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            Text(
                                text = headerState.title,
                                color = MiuixTheme.colorScheme.onBackground,
                                fontSize = AppTypographyTokens.SectionTitle.fontSize,
                                lineHeight = AppTypographyTokens.SectionTitle.lineHeight,
                                fontWeight = AppTypographyTokens.SectionTitle.fontWeight,
                            )
                        }
                        if (headerState.showSyncIndicator) {
                            LiquidCircularProgressBar(
                                progress = { syncProgress },
                                size = 18.dp,
                                strokeWidth = 2.dp,
                                activeColor = headerState.indicatorColor,
                                inactiveColor = headerState.indicatorColor.copy(alpha = 0.30f),
                            )
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(12.dp)) }
                if (sourceUrl.isBlank()) {
                    item {
                        LiquidInfoBlock(
                            backdrop = pageBackdrop,
                            title = stringResource(R.string.guide_empty_student_title),
                            subtitle = stringResource(R.string.guide_empty_student_subtitle),
                            accent = accent,
                        )
                    }
                } else {
                    renderBaStudentGuideTabContent(
                        activeBottomTab = tabRenderState.activeBottomTab,
                        activeBottomTabLabel = activeBottomTabLabel,
                        info = info,
                        error = error,
                        backdrop = pageBackdrop,
                        accent = accent,
                        context = context,
                        sourceUrl = sourceUrl,
                        galleryCacheRevision = galleryCacheRevision,
                        playingVoiceUrl = tabRenderState.playingVoiceUrl,
                        isVoicePlaying = tabRenderState.isVoicePlaying,
                        voicePlayProgress = tabRenderState.voicePlayProgress,
                        selectedVoiceLanguage = tabRenderState.selectedVoiceLanguage,
                        bgmFavoriteAudioUrls = bgmFavoriteAudioUrls,
                        profileLinkTitles = profileLinkTitles,
                        profileLinkMissingLinks = profileLinkMissingLinks,
                        isNpcSatelliteGuide = isNpcSatelliteGuide,
                        mediaAdaptiveRotationEnabled = mediaAdaptiveRotationEnabled,
                        onOpenExternal = onOpenExternal,
                        onOpenGuide = onOpenGuide,
                        onSaveMedia = onSaveMedia,
                        onSaveMediaPack = onSaveMediaPack,
                        onToggleBgmFavorite = onToggleBgmFavorite,
                        onRequestProfileLinkTitles = onRequestProfileLinkTitles,
                        onToggleVoicePlayback = onToggleVoicePlayback,
                        onSelectedVoiceLanguageChange = onSelectedVoiceLanguageChange,
                    )
                }
            }
        }

        BaStudentGuidePagerLoadingOverlay(
            visible =
                tabRenderState.shouldRenderHeavyContent &&
                    sourceUrl.isNotBlank() &&
                    info == null &&
                    error.isNullOrBlank(),
            innerPadding = innerPadding,
            accent = accent,
        )
    }
}

@Composable
private fun BaStudentGuidePagerLoadingOverlay(
    visible: Boolean,
    innerPadding: PaddingValues,
    accent: Color,
) {
    if (!visible) return
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(
                    top = innerPadding.calculateTopPadding() + AppChromeTokens.topBarToHeaderGap,
                    bottom = innerPadding.calculateBottomPadding(),
                    start = 20.dp,
                    end = 20.dp,
                ),
        contentAlignment = Alignment.Center,
    ) {
        AppAronaLoadingPanel(accent = accent)
    }
}
