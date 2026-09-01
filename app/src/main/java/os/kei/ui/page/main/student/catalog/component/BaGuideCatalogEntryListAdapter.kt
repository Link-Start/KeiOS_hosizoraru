package os.kei.ui.page.main.student.catalog.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import os.kei.ui.page.main.student.catalog.BaGuideCatalogEntry
import os.kei.ui.page.main.widget.glass.LiquidCircularProgressBar
import os.kei.ui.testing.KeiOsTestTags
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal fun LazyListScope.renderBaGuideCatalogEntryListAdapter(
    laneEntries: List<BaGuideCatalogEntry>,
    hasMoreEntries: Boolean,
    favoriteCatalogEntries: Map<Long, Long>,
    accent: Color,
    loadingMoreText: String,
    laneIndex: Int,
    onOpenGuide: (String) -> Unit,
    onToggleFavorite: (Long) -> Unit,
) {
    itemsIndexed(
        items = laneEntries,
        key = { _, entry -> "${entry.tab.name}-${entry.entryId}-${entry.contentId}" },
        contentType = { _, _ -> "ba_guide_catalog_entry" },
    ) { index, entry ->
        BaGuideCatalogEntryCard(
            entry = entry,
            isFavorite = favoriteCatalogEntries.containsKey(entry.contentId),
            onOpenGuide = onOpenGuide,
            onToggleFavorite = onToggleFavorite,
            // The baseline profile's only way into the student guide: the entries carry student names,
            // which differ per catalog and per language, so nothing else here is a stable handle. It goes
            // on the list's very first entry, which with alternating lanes is the first of the first lane.
            modifier =
                if (index == 0 && laneIndex == 0) {
                    Modifier.testTag(KeiOsTestTags.BaGuideCatalogEntryFirst)
                } else {
                    Modifier
                },
        )
    }

    // Only the leading lane carries the spinner: one list, one "loading more", and two of them side by
    // side would read as two separate loads.
    if (hasMoreEntries && laneIndex == 0) {
        item(
            key = "ba-guide-catalog-loading-more",
            contentType = "ba_guide_catalog_loading_more",
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LiquidCircularProgressBar(
                    progress = { 0.3f },
                    size = 16.dp,
                    strokeWidth = 2.dp,
                    activeColor = accent,
                    inactiveColor = accent.copy(alpha = 0.30f),
                )
                Text(
                    text = loadingMoreText,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    fontSize = 12.sp,
                )
            }
        }
    }
}
