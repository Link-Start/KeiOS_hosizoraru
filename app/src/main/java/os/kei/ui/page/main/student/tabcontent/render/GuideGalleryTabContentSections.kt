@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.tabcontent.render

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.R
import os.kei.ui.page.main.student.BaGuideGalleryItem
import os.kei.ui.page.main.student.BaGuideRow
import os.kei.ui.page.main.student.BaGuideTempMediaCache
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.component.GuideLiquidCard
import os.kei.ui.page.main.student.isExpressionGalleryItem
import os.kei.ui.page.main.student.section.GuideGalleryCardItem
import os.kei.ui.page.main.student.section.gallery.GuideGalleryExpressionCardItem
import os.kei.ui.page.main.student.section.gallery.GuideGalleryUnlockLevelCardItem
import os.kei.ui.page.main.student.section.gallery.GuideGalleryVideoGroupCardItem
import os.kei.ui.page.main.student.tabcontent.profile.GuideGalleryRelatedLinkRows
import os.kei.ui.page.main.student.tabcontent.profile.GuideProfileSectionHeader
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal fun LazyListScope.renderGuideGalleryStateContent(
    state: GuideGalleryTabResolvedState,
    error: String?,
    backdrop: LayerBackdrop,
    context: Context,
    sourceUrl: String,
    studentTitle: String,
    studentImageUrl: String,
    galleryCacheRevision: Int,
    bgmFavoriteAudioUrls: Set<String>,
    mediaAdaptiveRotationEnabled: Boolean,
    onOpenExternal: (String) -> Unit,
    onSaveMedia: (url: String, title: String) -> Unit,
    onSaveMediaPack: (items: List<Pair<String, String>>, packTitle: String) -> Unit,
    onToggleBgmFavorite: (GuideBgmFavoriteItem) -> Unit,
) {
    if (!error.isNullOrBlank()) {
        item(
            key = guideGalleryListKey("error", error.hashCode()),
            contentType = GuideGalleryContentType.ERROR,
        ) {
            GuideGalleryErrorCard(error = error)
        }
        item(
            key = guideGalleryListKey("spacer", "error"),
            contentType = GuideGalleryContentType.SPACER,
        ) {
            Spacer(modifier = Modifier.height(10.dp))
        }
    }

    if (!state.hasRenderableContent) {
        item(
            key = "guide-gallery-empty",
            contentType = GuideGalleryContentType.EMPTY,
        ) {
            GuideGalleryEmptyCard()
        }
        return
    }

    val mediaUrlResolver: (String) -> String = { raw ->
        galleryCacheRevision.let {
            BaGuideTempMediaCache.resolveCachedUrl(
                context = context,
                sourceUrl = sourceUrl,
                rawUrl = raw,
            )
        }
    }

    var renderedCount = 0
    var insertedUnlockLevel = false
    var insertedMemoryHallVideoNearGallery = false
    var insertedPvRoleAfterOfficial = false
    var insertedGalleryRelatedLinks = false

    state.displayGalleryItems.forEachIndexed { index, item ->
        val isExpression = isExpressionGalleryItem(item)
        if (isExpression && index != state.firstExpressionIndex) {
            return@forEachIndexed
        }
        if (renderedCount > 0) {
            item(
                key = guideGalleryListKey("spacer", "display", renderedCount, index),
                contentType = GuideGalleryContentType.SPACER,
            ) {
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        if (!insertedUnlockLevel &&
            state.memoryUnlockLevel.isNotBlank() &&
            index == state.firstMemoryHallIndex
        ) {
            item(
                key = guideGalleryListKey("unlock", state.memoryUnlockLevel, index),
                contentType = GuideGalleryContentType.UNLOCK,
            ) {
                GuideGalleryUnlockLevelCardItem(
                    level = state.memoryUnlockLevel,
                    backdrop = backdrop,
                )
            }
            item(
                key = guideGalleryListKey("spacer", "unlock", index),
                contentType = GuideGalleryContentType.SPACER,
            ) {
                Spacer(modifier = Modifier.height(10.dp))
            }
            insertedUnlockLevel = true
        }

        item(
            key =
                if (isExpression && state.expressionItems.isNotEmpty()) {
                    guideGalleryListKey(
                        "expression",
                        state.firstExpressionIndex,
                        state.expressionItems.size,
                    )
                } else {
                    guideGalleryItemStableKey(item = item, index = index)
                },
            contentType =
                if (isExpression && state.expressionItems.isNotEmpty()) {
                    GuideGalleryContentType.EXPRESSION
                } else {
                    GuideGalleryContentType.MEDIA
                },
        ) {
            if (isExpression && state.expressionItems.isNotEmpty()) {
                GuideGalleryExpressionCardItem(
                    title = stringResource(R.string.guide_gallery_expression_title),
                    items = state.expressionItems,
                    backdrop = backdrop,
                    onOpenMedia = onOpenExternal,
                    onSaveMedia = onSaveMedia,
                    onSaveMediaPack = onSaveMediaPack,
                    mediaUrlResolver = mediaUrlResolver,
                    mediaAdaptiveRotationEnabled = mediaAdaptiveRotationEnabled,
                )
            } else {
                GuideGalleryCardItem(
                    item = item,
                    backdrop = backdrop,
                    onOpenMedia = onOpenExternal,
                    onSaveMedia = onSaveMedia,
                    audioLoopScopeKey = sourceUrl,
                    mediaUrlResolver = mediaUrlResolver,
                    bgmFavoriteStudentTitle = studentTitle,
                    bgmFavoriteStudentImageUrl = studentImageUrl,
                    bgmFavoriteSourceUrl = sourceUrl,
                    bgmFavoriteAudioUrls = bgmFavoriteAudioUrls,
                    onToggleBgmFavorite = onToggleBgmFavorite,
                    mediaAdaptiveRotationEnabled = mediaAdaptiveRotationEnabled,
                )
            }
        }
        renderedCount += 1

        if (!insertedPvRoleAfterOfficial &&
            state.pvAndRoleVideoGroups.isNotEmpty() &&
            index == state.lastOfficialIntroIndex
        ) {
            state.pvAndRoleVideoGroups.forEachIndexed { groupIndex, (title, items) ->
                if (renderedCount > 0) {
                    item(
                        key = guideGalleryListKey("spacer", "pv-role", groupIndex, renderedCount),
                        contentType = GuideGalleryContentType.SPACER,
                    ) {
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
                item(
                    key = guideGalleryVideoGroupStableKey("pv-role", groupIndex, title, items),
                    contentType = GuideGalleryContentType.VIDEO_GROUP,
                ) {
                    GuideGalleryVideoGroupCardItem(
                        title = title,
                        items = items,
                        previewFallbackUrl = "",
                        backdrop = backdrop,
                        onOpenMedia = onOpenExternal,
                        onSaveMedia = onSaveMedia,
                        mediaUrlResolver = mediaUrlResolver,
                    )
                }
                renderedCount += 1
            }
            insertedPvRoleAfterOfficial = true

            if (!insertedGalleryRelatedLinks && state.galleryRelatedLinkRows.isNotEmpty()) {
                if (renderedCount > 0) {
                    item(
                        key = guideGalleryListKey("spacer", "related", "after-pv", renderedCount),
                        contentType = GuideGalleryContentType.SPACER,
                    ) {
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
                item(
                    key = guideGalleryRelatedLinksStableKey(state.galleryRelatedLinkRows),
                    contentType = GuideGalleryContentType.RELATED_LINKS,
                ) {
                    GuideGalleryRelatedLinksCard(
                        rows = state.galleryRelatedLinkRows,
                        onOpenExternal = onOpenExternal,
                    )
                }
                renderedCount += 1
                insertedGalleryRelatedLinks = true
            }
        }

        if (!insertedMemoryHallVideoNearGallery &&
            state.memoryHallVideoGroup != null &&
            index == state.firstMemoryHallIndex
        ) {
            item(
                key = guideGalleryListKey("spacer", "memory-video", index),
                contentType = GuideGalleryContentType.SPACER,
            ) {
                Spacer(modifier = Modifier.height(10.dp))
            }
            item(
                key =
                    guideGalleryVideoGroupStableKey(
                        "memory-near",
                        index,
                        state.memoryHallVideoGroup.first,
                        state.memoryHallVideoGroup.second,
                    ),
                contentType = GuideGalleryContentType.VIDEO_GROUP,
            ) {
                GuideGalleryVideoGroupCardItem(
                    title = state.memoryHallVideoGroup.first,
                    items = state.memoryHallVideoGroup.second,
                    previewFallbackUrl = state.memoryHallPreview,
                    backdrop = backdrop,
                    onOpenMedia = onOpenExternal,
                    onSaveMedia = onSaveMedia,
                    mediaUrlResolver = mediaUrlResolver,
                )
            }
            renderedCount += 1
            insertedMemoryHallVideoNearGallery = true
        }
    }

    if (!insertedUnlockLevel &&
        state.memoryUnlockLevel.isNotBlank() &&
        state.memoryHallVideoGroup != null
    ) {
        if (renderedCount > 0) {
            item(
                key = guideGalleryListKey("spacer", "unlock-fallback", renderedCount),
                contentType = GuideGalleryContentType.SPACER,
            ) {
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
        item(
            key = guideGalleryListKey("unlock", "fallback", state.memoryUnlockLevel),
            contentType = GuideGalleryContentType.UNLOCK,
        ) {
            GuideGalleryUnlockLevelCardItem(
                level = state.memoryUnlockLevel,
                backdrop = backdrop,
            )
        }
        insertedUnlockLevel = true
        renderedCount += 1
    }

    if (!insertedMemoryHallVideoNearGallery && state.memoryHallVideoGroup != null) {
        if (renderedCount > 0) {
            item(
                key = guideGalleryListKey("spacer", "memory-fallback", renderedCount),
                contentType = GuideGalleryContentType.SPACER,
            ) {
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
        item(
            key =
                guideGalleryVideoGroupStableKey(
                    "memory-fallback",
                    renderedCount,
                    state.memoryHallVideoGroup.first,
                    state.memoryHallVideoGroup.second,
                ),
            contentType = GuideGalleryContentType.VIDEO_GROUP,
        ) {
            GuideGalleryVideoGroupCardItem(
                title = state.memoryHallVideoGroup.first,
                items = state.memoryHallVideoGroup.second,
                previewFallbackUrl = state.memoryHallPreview,
                backdrop = backdrop,
                onOpenMedia = onOpenExternal,
                onSaveMedia = onSaveMedia,
                mediaUrlResolver = mediaUrlResolver,
            )
        }
        renderedCount += 1
    }

    if (!insertedPvRoleAfterOfficial) {
        state.pvAndRoleVideoGroups.forEachIndexed { groupIndex, (title, items) ->
            if (renderedCount > 0) {
                item(
                    key = guideGalleryListKey("spacer", "pv-fallback", groupIndex, renderedCount),
                    contentType = GuideGalleryContentType.SPACER,
                ) {
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
            item(
                key = guideGalleryVideoGroupStableKey("pv-fallback", groupIndex, title, items),
                contentType = GuideGalleryContentType.VIDEO_GROUP,
            ) {
                GuideGalleryVideoGroupCardItem(
                    title = title,
                    items = items,
                    previewFallbackUrl = "",
                    backdrop = backdrop,
                    onOpenMedia = onOpenExternal,
                    onSaveMedia = onSaveMedia,
                    mediaUrlResolver = mediaUrlResolver,
                )
            }
            renderedCount += 1
        }
        insertedPvRoleAfterOfficial = state.pvAndRoleVideoGroups.isNotEmpty()
        if (!insertedGalleryRelatedLinks && state.galleryRelatedLinkRows.isNotEmpty()) {
            if (renderedCount > 0) {
                item(
                    key = guideGalleryListKey("spacer", "related", "pv-fallback", renderedCount),
                    contentType = GuideGalleryContentType.SPACER,
                ) {
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
            item(
                key = guideGalleryRelatedLinksStableKey(state.galleryRelatedLinkRows),
                contentType = GuideGalleryContentType.RELATED_LINKS,
            ) {
                GuideGalleryRelatedLinksCard(
                    rows = state.galleryRelatedLinkRows,
                    onOpenExternal = onOpenExternal,
                )
            }
            renderedCount += 1
            insertedGalleryRelatedLinks = true
        }
    }

    state.otherTrailingVideoGroups.forEachIndexed { groupIndex, (title, items) ->
        if (renderedCount > 0) {
            item(
                key = guideGalleryListKey("spacer", "trailing", groupIndex, renderedCount),
                contentType = GuideGalleryContentType.SPACER,
            ) {
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
        item(
            key = guideGalleryVideoGroupStableKey("trailing", groupIndex, title, items),
            contentType = GuideGalleryContentType.VIDEO_GROUP,
        ) {
            GuideGalleryVideoGroupCardItem(
                title = title,
                items = items,
                previewFallbackUrl = "",
                backdrop = backdrop,
                onOpenMedia = onOpenExternal,
                onSaveMedia = onSaveMedia,
                mediaUrlResolver = mediaUrlResolver,
            )
        }
        renderedCount += 1
    }

    if (!insertedGalleryRelatedLinks && state.galleryRelatedLinkRows.isNotEmpty()) {
        if (renderedCount > 0) {
            item(
                key = guideGalleryListKey("spacer", "related", "trailing", renderedCount),
                contentType = GuideGalleryContentType.SPACER,
            ) {
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
        item(
            key = guideGalleryRelatedLinksStableKey(state.galleryRelatedLinkRows),
            contentType = GuideGalleryContentType.RELATED_LINKS,
        ) {
            GuideGalleryRelatedLinksCard(
                rows = state.galleryRelatedLinkRows,
                onOpenExternal = onOpenExternal,
            )
        }
    }
}

private object GuideGalleryContentType {
    const val ERROR = "guide_gallery_error"
    const val EMPTY = "guide_gallery_empty"
    const val SPACER = "guide_gallery_spacer"
    const val UNLOCK = "guide_gallery_unlock"
    const val MEDIA = "guide_gallery_media"
    const val EXPRESSION = "guide_gallery_expression"
    const val VIDEO_GROUP = "guide_gallery_video_group"
    const val RELATED_LINKS = "guide_gallery_related_links"
}

private fun guideGalleryListKey(
    type: String,
    vararg parts: Any?,
): String =
    buildString {
        append("guide-gallery-")
        append(type)
        parts.forEach { part ->
            append('-')
            append(part?.toString().orEmpty().hashCode())
        }
    }

private fun guideGalleryItemStableKey(
    item: BaGuideGalleryItem,
    index: Int,
): String =
    guideGalleryListKey(
        type = "media",
        item.title,
        item.mediaType,
        item.mediaUrl,
        item.imageUrl,
        item.note,
        index,
    )

private fun guideGalleryVideoGroupStableKey(
    groupType: String,
    groupIndex: Int,
    title: String,
    items: List<BaGuideGalleryItem>,
): String =
    guideGalleryListKey(
        type = "video",
        groupType,
        groupIndex,
        title,
        items.size,
        items.firstOrNull()?.mediaUrl.orEmpty(),
        items.lastOrNull()?.mediaUrl.orEmpty(),
    )

private fun guideGalleryRelatedLinksStableKey(rows: List<BaGuideRow>): String =
    guideGalleryListKey(
        type = "related",
        rows.size,
        rows.firstOrNull()?.key.orEmpty(),
        rows.lastOrNull()?.value.orEmpty(),
    )

@Composable
private fun GuideGalleryErrorCard(error: String) {
    GuideLiquidCard(
        modifier = Modifier.fillMaxWidth(),
        surfaceColor = Color(0x223B82F6),
        onClick = {},
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = error,
                color = MiuixTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun GuideGalleryEmptyCard() {
    GuideLiquidCard(
        modifier = Modifier.fillMaxWidth(),
        surfaceColor = Color(0x223B82F6),
        onClick = {},
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.guide_gallery_empty),
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }
    }
}

@Composable
private fun GuideGalleryRelatedLinksCard(
    rows: List<BaGuideRow>,
    onOpenExternal: (String) -> Unit,
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
            GuideProfileSectionHeader(
                title = stringResource(R.string.guide_gallery_related_links),
            )
            GuideGalleryRelatedLinkRows(
                rows = rows,
                onOpenExternal = onOpenExternal,
            )
        }
    }
}
