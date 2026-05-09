package os.kei.ui.page.main.github.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.R
import os.kei.ui.page.main.github.section.GitHubOverviewEntry
import os.kei.ui.page.main.github.section.orDefaultGitHubOverviewEntries
import os.kei.ui.page.main.os.appLucideCloseIcon
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.glass.AppLiquidCheckbox
import os.kei.ui.page.main.widget.glass.AppLiquidIconButton
import os.kei.ui.page.main.widget.glass.AppLiquidTextButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.sheet.SheetContentColumn
import os.kei.ui.page.main.widget.sheet.SheetDescriptionText
import os.kei.ui.page.main.widget.sheet.SheetSectionCard
import os.kei.ui.page.main.widget.sheet.SheetSectionTitle
import os.kei.ui.page.main.widget.sheet.SnapshotWindowBottomSheet
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun GitHubOverviewEntrySheet(
    show: Boolean,
    backdrop: LayerBackdrop,
    visibleEntries: Set<GitHubOverviewEntry>,
    onEntryVisibleChange: (GitHubOverviewEntry, Boolean) -> Unit,
    onReset: () -> Unit,
    onDismissRequest: () -> Unit
) {
    if (!show) return
    val activeEntries = visibleEntries.orDefaultGitHubOverviewEntries()
    SnapshotWindowBottomSheet(
        show = true,
        title = stringResource(R.string.github_overview_entry_sheet_title),
        onDismissRequest = onDismissRequest,
        startAction = {
            AppLiquidIconButton(
                backdrop = backdrop,
                variant = GlassVariant.Bar,
                icon = appLucideCloseIcon(),
                contentDescription = stringResource(R.string.common_close),
                onClick = onDismissRequest
            )
        }
    ) {
        SheetContentColumn(verticalSpacing = 10.dp) {
            SheetDescriptionText(stringResource(R.string.github_overview_entry_sheet_desc))
            SheetSectionTitle(stringResource(R.string.github_overview_entry_sheet_section))
            SheetSectionCard(verticalSpacing = 4.dp) {
                GitHubOverviewEntry.entries.forEach { entry ->
                    GitHubOverviewEntryRow(
                        entry = entry,
                        checked = entry in activeEntries,
                        onCheckedChange = { checked ->
                            onEntryVisibleChange(entry, checked)
                        }
                    )
                }
            }
            AppLiquidTextButton(
                backdrop = backdrop,
                variant = GlassVariant.SheetAction,
                text = stringResource(R.string.github_overview_entry_sheet_reset),
                onClick = onReset,
                modifier = Modifier.fillMaxWidth(),
                textMaxLines = 1,
                textOverflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun GitHubOverviewEntryRow(
    entry: GitHubOverviewEntry,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppLiquidCheckbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            contentDescription = stringResource(entry.labelRes)
        )
        Text(
            text = stringResource(entry.labelRes),
            color = MiuixTheme.colorScheme.onBackground,
            fontSize = AppTypographyTokens.Body.fontSize,
            lineHeight = AppTypographyTokens.Body.lineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
