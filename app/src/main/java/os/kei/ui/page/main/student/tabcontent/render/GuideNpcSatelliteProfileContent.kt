package os.kei.ui.page.main.student.tabcontent.render

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.R
import os.kei.ui.page.main.student.BaGuideRow
import os.kei.ui.page.main.student.BaStudentGuideInfo
import os.kei.ui.page.main.student.tabcontent.profile.GuideProfileInfoItem
import os.kei.ui.page.main.student.tabcontent.profile.GuideProfileInfoRows
import os.kei.ui.page.main.student.tabcontent.profile.GuideProfileRowsSection
import os.kei.ui.page.main.student.tabcontent.profile.GuideProfileSectionHeader
import os.kei.ui.page.main.student.tabcontent.profile.buildGuideNpcSatelliteProfileState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal fun LazyListScope.renderGuideNpcSatelliteProfileContent(
    guide: BaStudentGuideInfo,
    error: String?,
    backdrop: LayerBackdrop,
    onOpenGuide: (String) -> Unit,
) {
    val profileState = buildGuideNpcSatelliteProfileState(guide)

    if (!error.isNullOrBlank()) {
        guideProfileCard {
            Text(
                text = error,
                color = MiuixTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        item {
            Spacer(
                modifier =
                    androidx.compose.ui.Modifier
                        .height(10.dp),
            )
        }
    }

    renderNpcProfileGroup(
        titleRes = R.string.guide_profile_section_npc_aliases,
        rows = profileState.aliasRows,
    )
    renderNpcProfileGroup(
        titleRes = R.string.guide_profile_section_npc_identity,
        rows = profileState.identityRows,
    )
    renderNpcProfileGroup(
        titleRes = R.string.guide_profile_section_npc_intro,
        rows = profileState.introRows,
    )

    if (profileState.sameNameRoleItems.isNotEmpty() || profileState.sameNameRoleHint.isNotBlank()) {
        guideProfileCard(addTopSpacing = true) {
            GuideSameNameRoleSection(
                sameNameRoleHint = profileState.sameNameRoleHint,
                sameNameRoleItems = profileState.sameNameRoleItems,
                backdrop = backdrop,
                onOpenGuide = onOpenGuide,
            )
        }
    }

    if (profileState.normalRows.isNotEmpty()) {
        guideProfileCard(addTopSpacing = true) {
            GuideProfileSectionHeader(title = stringResource(R.string.guide_profile_section_npc_extra))
            GuideProfileRowsSection(
                rows = profileState.normalRows,
                emptyText = stringResource(R.string.guide_profile_npc_empty),
            )
        }
    } else if (
        profileState.aliasRows.isEmpty() &&
        profileState.identityRows.isEmpty() &&
        profileState.introRows.isEmpty()
    ) {
        guideProfileCard {
            Text(
                text = stringResource(R.string.guide_profile_npc_empty),
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }
    }
}

private fun LazyListScope.renderNpcProfileGroup(
    titleRes: Int,
    rows: List<BaGuideRow>,
) {
    if (rows.isEmpty()) return
    guideProfileCard(addTopSpacing = true) {
        GuideProfileSectionHeader(title = stringResource(titleRes))
        GuideProfileInfoRows(rows = rows) { row ->
            GuideProfileInfoItem(
                key = row.key,
                value = row.value.ifBlank { "-" },
                valueColor = npcProfileValueColor(row),
                preferCapsule = row.value.length <= 14 && !row.value.contains('\n'),
            )
        }
    }
}

private fun npcProfileValueColor(row: BaGuideRow): Color? {
    val key = row.key.trim()
    return if (key == "所属" || key.contains("首次登场")) {
        Color(0xFF5FA8FF)
    } else {
        null
    }
}
