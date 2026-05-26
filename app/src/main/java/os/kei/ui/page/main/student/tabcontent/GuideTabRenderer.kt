package os.kei.ui.page.main.student.tabcontent

import android.content.Context
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.graphics.Color
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.ui.page.main.student.BaStudentGuideInfo
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.GuideBottomTab
import os.kei.ui.page.main.student.page.state.BaStudentGuideContentPresentationState
import os.kei.ui.page.main.student.tabcontent.render.renderGuideArchiveTabContent
import os.kei.ui.page.main.student.tabcontent.render.renderGuideGalleryTabContent
import os.kei.ui.page.main.student.tabcontent.render.renderGuideProfileTabContent
import os.kei.ui.page.main.student.tabcontent.render.renderGuideSimulateTabContent
import os.kei.ui.page.main.student.tabcontent.render.renderGuideSkillsTabContent
import os.kei.ui.page.main.student.tabcontent.render.renderGuideVoiceTabContent
import os.kei.ui.page.main.widget.glass.LiquidInfoBlock

internal fun LazyListScope.renderBaStudentGuideTabContent(
    activeBottomTab: GuideBottomTab,
    activeBottomTabLabel: String,
    info: BaStudentGuideInfo?,
    error: String?,
    backdrop: LayerBackdrop,
    accent: Color,
    context: Context,
    sourceUrl: String,
    galleryCacheRevision: Int,
    playingVoiceUrl: String,
    isVoicePlaying: Boolean,
    voicePlayProgress: Float,
    selectedVoiceLanguage: String,
    bgmFavoriteAudioUrls: Set<String>,
    profileLinkTitles: Map<String, String>,
    profileLinkMissingLinks: Set<String>,
    isNpcSatelliteGuide: Boolean,
    mediaAdaptiveRotationEnabled: Boolean,
    contentPresentationState: BaStudentGuideContentPresentationState?,
    onOpenExternal: (String) -> Unit,
    onOpenGuide: (String) -> Unit,
    onSaveMedia: (url: String, title: String) -> Unit,
    onSaveMediaPack: (items: List<Pair<String, String>>, packTitle: String) -> Unit,
    onToggleBgmFavorite: (GuideBgmFavoriteItem) -> Unit,
    onRequestProfileLinkTitles: (List<String>) -> Unit,
    onToggleVoicePlayback: (String) -> Unit,
    onSelectedVoiceLanguageChange: (String) -> Unit,
) {
    when (activeBottomTab) {
        GuideBottomTab.Archive -> {
            renderGuideArchiveTabContent(
                info = info,
                isNpcSatelliteGuide = isNpcSatelliteGuide,
            )
        }

        GuideBottomTab.Skills -> {
            renderGuideSkillsTabContent(
                tabLabel = activeBottomTabLabel,
                info = info,
                error = error,
                backdrop = backdrop,
                accent = accent,
                mediaAdaptiveRotationEnabled = mediaAdaptiveRotationEnabled,
            )
        }

        GuideBottomTab.Profile -> {
            if (info != null && contentPresentationState == null) {
                renderGuideDerivedContentPendingBlock(
                    tabLabel = activeBottomTabLabel,
                    backdrop = backdrop,
                    accent = accent,
                )
                return
            }
            renderGuideProfileTabContent(
                tabLabel = activeBottomTabLabel,
                info = info,
                error = error,
                backdrop = backdrop,
                accent = accent,
                context = context,
                sourceUrl = sourceUrl,
                galleryCacheRevision = galleryCacheRevision,
                bgmFavoriteAudioUrls = bgmFavoriteAudioUrls,
                profileLinkTitles = profileLinkTitles,
                profileLinkMissingLinks = profileLinkMissingLinks,
                isNpcSatelliteGuide = isNpcSatelliteGuide,
                mediaAdaptiveRotationEnabled = mediaAdaptiveRotationEnabled,
                profileHeaderState = contentPresentationState?.profileHeaderState,
                npcProfileState = contentPresentationState?.npcProfileState,
                onOpenExternal = onOpenExternal,
                onOpenGuide = onOpenGuide,
                onSaveMedia = onSaveMedia,
                onToggleBgmFavorite = onToggleBgmFavorite,
                onRequestProfileLinkTitles = onRequestProfileLinkTitles,
            )
        }

        GuideBottomTab.Voice -> {
            renderGuideVoiceTabContent(
                tabLabel = activeBottomTabLabel,
                info = info,
                error = error,
                backdrop = backdrop,
                accent = accent,
                context = context,
                sourceUrl = sourceUrl,
                galleryCacheRevision = galleryCacheRevision,
                playingVoiceUrl = playingVoiceUrl,
                isVoicePlaying = isVoicePlaying,
                voicePlayProgress = voicePlayProgress,
                selectedVoiceLanguage = selectedVoiceLanguage,
                onToggleVoicePlayback = onToggleVoicePlayback,
                onSelectedVoiceLanguageChange = onSelectedVoiceLanguageChange,
            )
        }

        GuideBottomTab.Gallery -> {
            if (info != null && contentPresentationState == null) {
                renderGuideDerivedContentPendingBlock(
                    tabLabel = activeBottomTabLabel,
                    backdrop = backdrop,
                    accent = accent,
                )
                return
            }
            renderGuideGalleryTabContent(
                tabLabel = activeBottomTabLabel,
                info = info,
                error = error,
                backdrop = backdrop,
                accent = accent,
                context = context,
                sourceUrl = sourceUrl,
                galleryCacheRevision = galleryCacheRevision,
                bgmFavoriteAudioUrls = bgmFavoriteAudioUrls,
                mediaAdaptiveRotationEnabled = mediaAdaptiveRotationEnabled,
                galleryState = contentPresentationState?.galleryState,
                onOpenExternal = onOpenExternal,
                onSaveMedia = onSaveMedia,
                onSaveMediaPack = onSaveMediaPack,
                onToggleBgmFavorite = onToggleBgmFavorite,
            )
        }

        GuideBottomTab.Simulate -> {
            if (info != null && contentPresentationState == null) {
                renderGuideDerivedContentPendingBlock(
                    tabLabel = activeBottomTabLabel,
                    backdrop = backdrop,
                    accent = accent,
                )
                return
            }
            renderGuideSimulateTabContent(
                tabLabel = activeBottomTabLabel,
                info = info,
                error = error,
                backdrop = backdrop,
                accent = accent,
                simulateData = contentPresentationState?.simulateData,
            )
        }
    }
}

private fun LazyListScope.renderGuideDerivedContentPendingBlock(
    tabLabel: String,
    backdrop: LayerBackdrop,
    accent: Color,
) {
    item {
        LiquidInfoBlock(
            backdrop = backdrop,
            title = tabLabel,
            subtitle = "",
            accent = accent,
        )
    }
}
