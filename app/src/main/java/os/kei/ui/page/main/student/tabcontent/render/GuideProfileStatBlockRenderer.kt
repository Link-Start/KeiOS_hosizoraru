package os.kei.ui.page.main.student.tabcontent.render

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.ui.page.main.student.BaGuideGalleryItem
import os.kei.ui.page.main.student.BaGuideRow
import os.kei.ui.page.main.student.BaGuideTempMediaCache
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.component.GuideLiquidCard
import os.kei.ui.page.main.student.section.GuideGalleryCardItem
import os.kei.ui.page.main.student.tabcontent.profile.GuideProfileInfoItem
import os.kei.ui.page.main.student.tabcontent.profile.GuideProfileInfoRows
import os.kei.ui.page.main.student.tabcontent.profile.GuideProfileSectionHeader

internal fun LazyListScope.guideProfileCard(
    key: String,
    addTopSpacing: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (addTopSpacing) {
        item(
            key = "$key-spacer",
            contentType = GuideProfileContentType.SPACER,
        ) { Spacer(modifier = Modifier.height(10.dp)) }
    }
    item(
        key = key,
        contentType = GuideProfileContentType.CARD,
    ) {
        GuideLiquidCard(
            modifier = Modifier.fillMaxWidth(),
            surfaceColor = Color(0x223B82F6),
            onClick = {},
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                content()
            }
        }
    }
}

internal fun LazyListScope.renderGuideProfileMediaGroup(
    @StringRes titleRes: Int,
    infoRows: List<BaGuideRow>,
    galleryItems: List<BaGuideGalleryItem>,
    backdrop: LayerBackdrop,
    context: Context,
    sourceUrl: String,
    galleryCacheRevision: Int,
    bgmFavoriteAudioUrls: Set<String>,
    mediaAdaptiveRotationEnabled: Boolean,
    onOpenExternal: (String) -> Unit,
    onSaveMedia: (url: String, title: String) -> Unit,
    onToggleBgmFavorite: (GuideBgmFavoriteItem) -> Unit,
    preferCapsule: Boolean,
) {
    if (infoRows.isEmpty() && galleryItems.isEmpty()) return
    val mediaUrlResolverCacheKey = "$sourceUrl#$galleryCacheRevision#$titleRes"
    guideProfileCard(
        key = "guide-profile-media-$titleRes",
        addTopSpacing = true,
    ) {
        GuideProfileSectionHeader(title = stringResource(titleRes))
        GuideProfileInfoRows(rows = infoRows) { row ->
            val value = row.value.ifBlank { "-" }
            GuideProfileInfoItem(
                key = row.key,
                value = value,
                preferCapsule = preferCapsule,
            )
        }
        galleryItems.forEachIndexed { index, galleryItem ->
            if (infoRows.isNotEmpty() || index > 0) {
                Spacer(modifier = Modifier.height(6.dp))
            }
            GuideGalleryCardItem(
                item = galleryItem,
                backdrop = backdrop,
                onOpenMedia = onOpenExternal,
                onSaveMedia = onSaveMedia,
                audioLoopScopeKey = sourceUrl,
                mediaUrlResolver = { raw ->
                    galleryCacheRevision.let {
                        BaGuideTempMediaCache.resolveCachedUrl(
                            context = context,
                            sourceUrl = sourceUrl,
                            rawUrl = raw,
                        )
                    }
                },
                mediaUrlResolverCacheKey = mediaUrlResolverCacheKey,
                embedded = true,
                showMediaTypeLabel = false,
                bgmFavoriteAudioUrls = bgmFavoriteAudioUrls,
                onToggleBgmFavorite = onToggleBgmFavorite,
                mediaAdaptiveRotationEnabled = mediaAdaptiveRotationEnabled,
            )
        }
    }
}

private object GuideProfileContentType {
    const val CARD = "guide_profile_card"
    const val SPACER = "guide_profile_spacer"
}
