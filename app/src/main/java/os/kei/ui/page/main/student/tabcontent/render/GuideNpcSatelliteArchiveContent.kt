package os.kei.ui.page.main.student.tabcontent.render

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.ui.page.main.student.BaGuideRow
import os.kei.ui.page.main.student.BaStudentGuideInfo
import os.kei.ui.page.main.student.GuideRemoteImage
import os.kei.ui.page.main.student.component.GuideLiquidCard
import os.kei.ui.page.main.student.tabcontent.profile.GuideProfileInfoItem
import os.kei.ui.page.main.student.tabcontent.profile.GuideProfileRowsSection
import os.kei.ui.page.main.student.tabcontent.profile.GuideProfileSectionHeader
import os.kei.ui.page.main.student.tabcontent.profile.buildGuideNpcSatelliteProfileState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal fun LazyListScope.renderGuideNpcSatelliteArchiveContent(guide: BaStudentGuideInfo) {
    val profileState = buildGuideNpcSatelliteProfileState(guide)
    val leadingRows =
        buildNpcArchiveLeadingRows(
            identityRows = profileState.identityRows,
            aliasRows = profileState.aliasRows,
        )
    item {
        GuideLiquidCard(
            modifier = Modifier.fillMaxWidth(),
            surfaceColor = Color(0x223B82F6),
            onClick = {},
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GuideProfileSectionHeader(title = stringResource(R.string.guide_archive_section_npc_overview))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(modifier = Modifier.width(112.dp)) {
                        if (guide.imageUrl.isNotBlank()) {
                            GuideRemoteImage(
                                imageUrl = guide.imageUrl,
                                imageHeight = 152.dp,
                                cropAlignment = Alignment.TopCenter,
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.guide_gallery_no_image),
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                            )
                        }
                    }
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(152.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        leadingRows.take(5).forEach { row ->
                            GuideProfileInfoItem(
                                key = row.key,
                                value = row.value.ifBlank { "-" },
                                preferCapsule = row.value.length <= 14 && !row.value.contains('\n'),
                            )
                        }
                    }
                }
                val summaryText = guide.summary.ifBlank { guide.description }
                if (summaryText.isNotBlank()) {
                    Text(
                        text = summaryText,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
    if (profileState.normalRows.isNotEmpty()) {
        item { Spacer(modifier = Modifier.height(10.dp)) }
        guideProfileCard {
            GuideProfileSectionHeader(title = stringResource(R.string.guide_profile_section_npc_extra))
            GuideProfileRowsSection(
                rows = profileState.normalRows,
                emptyText = stringResource(R.string.guide_profile_npc_empty),
            )
        }
    }
}

private fun buildNpcArchiveLeadingRows(
    identityRows: List<BaGuideRow>,
    aliasRows: List<BaGuideRow>,
): List<BaGuideRow> {
    val preferredIdentityKeys = listOf("所属", "声优", "生日", "身高", "首次登场日期", "首次登场")
    val identity =
        preferredIdentityKeys.mapNotNull { key ->
            identityRows.firstOrNull { row -> row.key.trim() == key }
        } +
            identityRows.filterNot { row ->
                preferredIdentityKeys.any { key -> row.key.trim() == key }
            }
    val aliases =
        aliasRows.filter { row ->
            row.key.contains("全名") || row.key.contains("其他译名") || row.key.contains("黑话")
        }
    return (identity + aliases).distinctBy { row -> "${row.key.trim()}|${row.value.trim()}" }
}
