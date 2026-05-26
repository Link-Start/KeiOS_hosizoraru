package os.kei.ui.page.main.student.tabcontent.render

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.R
import os.kei.ui.page.main.student.BaGuideRow
import os.kei.ui.page.main.student.BaStudentGuideInfo
import os.kei.ui.page.main.student.tabcontent.profile.GuideNpcSatelliteProfileState
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
    profileState: GuideNpcSatelliteProfileState?,
    onOpenGuide: (String) -> Unit,
) {
    val resolvedProfileState = profileState ?: buildGuideNpcSatelliteProfileState(guide)

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
                    Modifier
                        .height(10.dp),
            )
        }
    }

    renderNpcProfileGroup(
        titleRes = R.string.guide_profile_section_npc_aliases,
        rows = resolvedProfileState.aliasRows,
    )
    renderNpcProfileGroup(
        titleRes = R.string.guide_profile_section_npc_identity,
        rows = resolvedProfileState.identityRows,
    )
    renderNpcProfileGroup(
        titleRes = R.string.guide_profile_section_npc_intro,
        rows = resolvedProfileState.introRows,
        preferCapsule = false,
    )

    if (resolvedProfileState.relatedRoleItems.isNotEmpty() || resolvedProfileState.relatedRoleHint.isNotBlank()) {
        guideProfileCard(addTopSpacing = true) {
            GuideRelationRoleSection(
                sectionTitle = resolvedProfileState.relatedRoleTitle.ifBlank {
                    stringResource(R.string.guide_profile_related_roles)
                },
                itemFallbackLabel = stringResource(R.string.guide_profile_related_role),
                emptyText = stringResource(R.string.guide_profile_related_roles_empty),
                roleHint = resolvedProfileState.relatedRoleHint,
                roleItems = resolvedProfileState.relatedRoleItems,
                backdrop = backdrop,
                onOpenGuide = onOpenGuide,
            )
        }
    }

    if (resolvedProfileState.sameNameRoleItems.isNotEmpty() || resolvedProfileState.sameNameRoleHint.isNotBlank()) {
        guideProfileCard(addTopSpacing = true) {
            GuideRelationRoleSection(
                sectionTitle = resolvedProfileState.sameNameRoleTitle.ifBlank {
                    stringResource(R.string.guide_profile_related_same_name)
                },
                itemFallbackLabel = stringResource(R.string.guide_profile_same_name),
                emptyText = stringResource(R.string.guide_profile_same_name_empty),
                roleHint = resolvedProfileState.sameNameRoleHint,
                roleItems = resolvedProfileState.sameNameRoleItems,
                backdrop = backdrop,
                onOpenGuide = onOpenGuide,
            )
        }
    }

    if (resolvedProfileState.normalRows.isNotEmpty()) {
        guideProfileCard(addTopSpacing = true) {
            GuideProfileSectionHeader(title = stringResource(R.string.guide_profile_section_npc_extra))
            GuideProfileRowsSection(
                rows = resolvedProfileState.normalRows,
                emptyText = stringResource(R.string.guide_profile_npc_empty),
            )
        }
    } else if (
        resolvedProfileState.aliasRows.isEmpty() &&
        resolvedProfileState.identityRows.isEmpty() &&
        resolvedProfileState.introRows.isEmpty()
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
    preferCapsule: Boolean = true,
) {
    if (rows.isEmpty()) return
    guideProfileCard(addTopSpacing = true) {
        GuideProfileSectionHeader(title = stringResource(titleRes))
        GuideProfileInfoRows(rows = rows) { row ->
            val displayAsCapsule =
                preferCapsule &&
                    row.value.length <= 14 &&
                    !row.value.contains('\n') &&
                    !row.value.contains(" / ")
            GuideProfileInfoItem(
                key = row.key,
                value = row.value.ifBlank { "-" },
                valueColor = npcProfileValueColor(row),
                preferCapsule = displayAsCapsule,
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
