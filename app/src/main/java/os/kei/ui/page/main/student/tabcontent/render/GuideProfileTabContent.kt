package os.kei.ui.page.main.student.tabcontent.render

import android.content.Context
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.R
import os.kei.ui.page.main.student.BaStudentGuideInfo
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.tabcontent.profile.GuideGiftPreferenceGrid
import os.kei.ui.page.main.student.tabcontent.profile.GuideNpcSatelliteProfileState
import os.kei.ui.page.main.student.tabcontent.profile.GuideProfileInfoItem
import os.kei.ui.page.main.student.tabcontent.profile.GuideProfileInfoRows
import os.kei.ui.page.main.student.tabcontent.profile.GuideProfileRowsSection
import os.kei.ui.page.main.student.tabcontent.profile.GuideProfileSectionHeader
import os.kei.ui.page.main.student.tabcontent.profile.extractProfileExternalLink
import os.kei.ui.page.main.student.tabcontent.profile.fallbackProfileLinkTitle
import os.kei.ui.page.main.student.tabcontent.profile.normalizeProfileFieldKey
import os.kei.ui.page.main.student.tabcontent.profile.profileRoleReferenceFieldKey
import os.kei.ui.page.main.widget.glass.LiquidInfoBlock
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal fun LazyListScope.renderGuideProfileTabContent(
    tabLabel: String,
    info: BaStudentGuideInfo?,
    error: String?,
    backdrop: LayerBackdrop,
    accent: Color,
    context: Context,
    sourceUrl: String,
    galleryCacheRevision: Int,
    bgmFavoriteAudioUrls: Set<String>,
    profileLinkTitles: Map<String, String>,
    profileLinkMissingLinks: Set<String>,
    isNpcSatelliteGuide: Boolean,
    mediaAdaptiveRotationEnabled: Boolean,
    profileHeaderState: GuideProfileTabHeaderState?,
    npcProfileState: GuideNpcSatelliteProfileState?,
    onOpenExternal: (String) -> Unit,
    onOpenGuide: (String) -> Unit,
    onSaveMedia: (url: String, title: String) -> Unit,
    onToggleBgmFavorite: (GuideBgmFavoriteItem) -> Unit,
    onRequestProfileLinkTitles: (List<String>) -> Unit,
) {
    val guide = info
    if (guide == null) {
        item {
            LiquidInfoBlock(
                backdrop = backdrop,
                title = tabLabel,
                subtitle = info?.subtitle?.ifBlank { "GameKee" } ?: "GameKee",
                accent = accent,
                content = {
                    error?.takeIf { it.isNotBlank() }?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = it,
                            color = MiuixTheme.colorScheme.error,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
            )
        }
        return
    }

    if (isNpcSatelliteGuide) {
        renderGuideNpcSatelliteProfileContent(
            guide = guide,
            error = error,
            backdrop = backdrop,
            profileState = npcProfileState,
            onOpenGuide = onOpenGuide,
        )
        return
    }

    val headerState = profileHeaderState ?: buildGuideProfileTabHeaderState(guide)

    if (!error.isNullOrBlank()) {
        guideProfileCard(key = "guide-profile-error") {
            Text(
                text = error.orEmpty(),
                color = MiuixTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        item { Spacer(modifier = Modifier.height(10.dp)) }
    }

    if (headerState.nicknameRows.isNotEmpty()) {
        guideProfileCard(key = "guide-profile-nickname") {
            GuideProfileSectionHeader(title = stringResource(R.string.guide_profile_section_nickname))
            GuideProfileInfoRows(rows = headerState.nicknameRows) { row ->
                GuideProfileInfoItem(
                    key = row.key,
                    value = row.value.ifBlank { "-" },
                )
            }
        }
        item { Spacer(modifier = Modifier.height(10.dp)) }
    }

    if (headerState.studentInfoRows.isNotEmpty()) {
        guideProfileCard(key = "guide-profile-student-info") {
            GuideProfileSectionHeader(title = stringResource(R.string.guide_profile_section_info))
            GuideProfileInfoRows(rows = headerState.studentInfoRows) { row ->
                val normalizedKey = normalizeProfileFieldKey(row.key)
                if (normalizedKey == profileRoleReferenceFieldKey) {
                    val externalLink =
                        remember(row.value) {
                            extractProfileExternalLink(row.value)
                        }
                    LaunchedEffect(
                        externalLink,
                        profileLinkTitles[externalLink],
                        profileLinkMissingLinks.contains(externalLink),
                        onRequestProfileLinkTitles,
                    ) {
                        if (
                            externalLink.isNotBlank() &&
                            !profileLinkTitles.containsKey(externalLink) &&
                            !profileLinkMissingLinks.contains(externalLink)
                        ) {
                            onRequestProfileLinkTitles(listOf(externalLink))
                        }
                    }
                    val resolvedTitle = profileLinkTitles[externalLink].orEmpty()
                    val displayValue =
                        when {
                            externalLink.isBlank() -> row.value.ifBlank { "-" }
                            resolvedTitle.isNotBlank() -> resolvedTitle
                            else -> fallbackProfileLinkTitle(externalLink)
                        }
                    GuideProfileInfoItem(
                        key = row.key,
                        value = displayValue,
                        onClick =
                            externalLink.takeIf { it.isNotBlank() }?.let { link ->
                                { onOpenExternal(link) }
                            },
                        valueColor =
                            if (externalLink.isNotBlank()) {
                                Color(0xFF5FA8FF)
                            } else {
                                null
                            },
                        preferCapsule = false,
                    )
                } else {
                    GuideProfileInfoItem(
                        key = row.key,
                        value = row.value.ifBlank { "-" },
                    )
                }
            }
        }
        item { Spacer(modifier = Modifier.height(10.dp)) }
    }

    if (headerState.hobbyRows.isNotEmpty()) {
        guideProfileCard(key = "guide-profile-hobbies") {
            GuideProfileSectionHeader(title = stringResource(R.string.guide_profile_section_hobbies))
            GuideProfileInfoRows(rows = headerState.hobbyRows) { row ->
                GuideProfileInfoItem(
                    key = row.key,
                    value = row.value.ifBlank { "-" },
                    preferCapsule = false,
                )
            }
        }
        item { Spacer(modifier = Modifier.height(10.dp)) }
    }

    if (headerState.giftPreferenceItems.isNotEmpty()) {
        guideProfileCard(key = "guide-profile-gifts") {
            GuideProfileSectionHeader(title = stringResource(R.string.guide_profile_section_gifts))
            GuideGiftPreferenceGrid(items = headerState.giftPreferenceItems)
        }
        item { Spacer(modifier = Modifier.height(10.dp)) }
    }

    guideProfileCard(key = "guide-profile-same-name") {
        GuideSameNameRoleSection(
            sameNameRoleHint = headerState.sameNameRoleHint,
            sameNameRoleItems = headerState.sameNameRoleItems,
            backdrop = backdrop,
            onOpenGuide = onOpenGuide,
        )
    }
    item { Spacer(modifier = Modifier.height(10.dp)) }

    if (headerState.normalProfileRows.isNotEmpty()) {
        guideProfileCard(key = "guide-profile-normal") {
            GuideProfileRowsSection(
                rows = headerState.normalProfileRows,
                emptyText = stringResource(R.string.guide_profile_empty),
            )
        }
    } else if (
        headerState.nicknameRows.isEmpty() &&
        headerState.studentInfoRows.isEmpty() &&
        headerState.hobbyRows.isEmpty() &&
        headerState.giftPreferenceItems.isEmpty()
    ) {
        guideProfileCard(key = "guide-profile-empty") {
            Text(
                text = stringResource(R.string.guide_profile_empty),
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }
    }

    renderGuideProfileMediaGroup(
        titleRes = R.string.guide_profile_section_chocolate,
        infoRows = headerState.chocolateInfoRows,
        galleryItems = headerState.chocolateGalleryItems,
        backdrop = backdrop,
        context = context,
        sourceUrl = sourceUrl,
        galleryCacheRevision = galleryCacheRevision,
        bgmFavoriteAudioUrls = bgmFavoriteAudioUrls,
        mediaAdaptiveRotationEnabled = mediaAdaptiveRotationEnabled,
        onOpenExternal = onOpenExternal,
        onSaveMedia = onSaveMedia,
        onToggleBgmFavorite = onToggleBgmFavorite,
        preferCapsule = true,
    )

    renderGuideProfileMediaGroup(
        titleRes = R.string.guide_profile_section_furniture,
        infoRows = headerState.furnitureInfoRows,
        galleryItems = headerState.furnitureGalleryItems,
        backdrop = backdrop,
        context = context,
        sourceUrl = sourceUrl,
        galleryCacheRevision = galleryCacheRevision,
        bgmFavoriteAudioUrls = bgmFavoriteAudioUrls,
        mediaAdaptiveRotationEnabled = mediaAdaptiveRotationEnabled,
        onOpenExternal = onOpenExternal,
        onSaveMedia = onSaveMedia,
        onToggleBgmFavorite = onToggleBgmFavorite,
        preferCapsule = false,
    )
}
