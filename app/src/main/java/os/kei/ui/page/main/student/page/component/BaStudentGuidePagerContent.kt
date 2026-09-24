@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.page.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import os.kei.ui.page.main.student.BaStudentGuideInfo
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.GuideBottomTab
import os.kei.ui.page.main.student.page.state.BaStudentGuideContentPresentationState
import top.yukonga.miuix.kmp.utils.PagerGestureNestedScrollConnection
import top.yukonga.miuix.kmp.utils.PagerInterceptionMode
import top.yukonga.miuix.kmp.utils.PagerNavigationSpringSpec
import top.yukonga.miuix.kmp.utils.pagerGestureOverride

@Composable
internal fun BaStudentGuidePagerContent(
    sourceUrl: String,
    info: BaStudentGuideInfo?,
    error: String?,
    pagerState: PagerState,
    bottomTabs: List<GuideBottomTab>,
    syncProgress: Float,
    activationCount: Int,
    surfaceColor: Color,
    accent: Color,
    innerPadding: PaddingValues,
    farJumpAlphaProvider: () -> Float,
    navBackdrop: LayerBackdrop,
    topBarBackdrop: LayerBackdrop,
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
    contentPresentationState: BaStudentGuideContentPresentationState,
    guidePagerBeyondViewportPageCount: Int,
    chromeNestedScrollConnection: NestedScrollConnection,
    topBarNestedScrollConnection: NestedScrollConnection,
    onPageListStateChange: (pageIndex: Int, listState: LazyListState) -> Unit,
    onOpenExternal: (String) -> Unit,
    onOpenGuide: (String) -> Unit,
    onSaveMedia: (String, String) -> Unit,
    onSaveMediaPack: (List<Pair<String, String>>, String) -> Unit,
    onToggleBgmFavorite: (GuideBgmFavoriteItem) -> Unit,
    onRequestProfileLinkTitles: (List<String>) -> Unit,
    onToggleVoicePlayback: (String) -> Unit,
    scrollToTopSignal: Int,
    onScrollBoundsChange: (canScrollBackward: Boolean, canScrollForward: Boolean) -> Unit,
    onListScrollInProgressChange: (Boolean) -> Unit,
    onSelectedVoiceLanguageChange: (String) -> Unit,
) {
    // The swipe settles on the same spring the tab bar animates with (see animateTabSwitch), so a
    // page lands the same way whichever of the two moved it. Miuix's Cross-Axis mode owns the touch
    // drag (hence userScrollEnabled = false): a horizontal swipe pages even while a page's list is still
    // coasting or bouncing, and the pager's own mutation starts only past touch slop, so taps and
    // vertical scrolls never set isScrollInProgress. Children win their own aligned drags through
    // Foundation's arbitration, so the gallery's audio progress slider keeps its horizontal drag.
    // The fling behaviour is shared with the modifier, which drives wheel and trackpad input with it.
    val flingBehavior =
        PagerDefaults.flingBehavior(
            state = pagerState,
            snapAnimationSpec = PagerNavigationSpringSpec,
        )
    HorizontalPager(
        state = pagerState,
        key = { index -> bottomTabs.getOrNull(index)?.name ?: "stale-$index" },
        overscrollEffect = null,
        flingBehavior = flingBehavior,
        userScrollEnabled = false,
        pageNestedScrollConnection = PagerGestureNestedScrollConnection,
        beyondViewportPageCount = guidePagerBeyondViewportPageCount,
        modifier =
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = farJumpAlphaProvider() }
                .layerBackdrop(topBarBackdrop)
                .layerBackdrop(navBackdrop)
                .pagerGestureOverride(
                    pagerState = pagerState,
                    flingBehavior = flingBehavior,
                    mode = PagerInterceptionMode.CrossAxis,
                ),
    ) { pageIndex ->
        BaStudentGuidePagerPage(
            sourceUrl = sourceUrl,
            info = info,
            error = error,
            pageIndex = pageIndex,
            bottomTabs = bottomTabs,
            pagerState = pagerState,
            activationCount = activationCount,
            surfaceColor = surfaceColor,
            accent = accent,
            innerPadding = innerPadding,
            syncProgress = syncProgress,
            galleryCacheRevision = galleryCacheRevision,
            selectedVoiceLanguage = selectedVoiceLanguage,
            playingVoiceUrl = playingVoiceUrl,
            isVoicePlaying = isVoicePlaying,
            voicePlayProgress = voicePlayProgress,
            bgmFavoriteAudioUrls = bgmFavoriteAudioUrls,
            profileLinkTitles = profileLinkTitles,
            profileLinkMissingLinks = profileLinkMissingLinks,
            isNpcSatelliteGuide = isNpcSatelliteGuide,
            mediaAdaptiveRotationEnabled = mediaAdaptiveRotationEnabled,
            contentPresentationState = contentPresentationState,
            chromeNestedScrollConnection = chromeNestedScrollConnection,
            topBarNestedScrollConnection = topBarNestedScrollConnection,
            onPageListStateChange = onPageListStateChange,
            onOpenExternal = onOpenExternal,
            onOpenGuide = onOpenGuide,
            onSaveMedia = onSaveMedia,
            onSaveMediaPack = onSaveMediaPack,
            onToggleBgmFavorite = onToggleBgmFavorite,
            onRequestProfileLinkTitles = onRequestProfileLinkTitles,
            onToggleVoicePlayback = onToggleVoicePlayback,
            scrollToTopSignal = scrollToTopSignal,
            onScrollBoundsChange = onScrollBoundsChange,
            onListScrollInProgressChange = onListScrollInProgressChange,
            onSelectedVoiceLanguageChange = onSelectedVoiceLanguageChange,
        )
    }
}
