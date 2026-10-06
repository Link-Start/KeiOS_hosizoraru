@file:Suppress("FunctionName", "PropertyName")

package os.kei.ui.page.main.student.catalog.component

import android.graphics.Rect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.core.ext.showToast
import os.kei.ui.page.main.os.appLucideChevronDownIcon
import os.kei.ui.page.main.os.appLucideChevronUpIcon
import os.kei.ui.page.main.os.appLucideConfirmIcon
import os.kei.ui.page.main.os.appLucideDatabaseIcon
import os.kei.ui.page.main.os.appLucideDownloadIcon
import os.kei.ui.page.main.os.appLucideExternalLinkIcon
import os.kei.ui.page.main.os.appLucideFullscreenIcon
import os.kei.ui.page.main.os.appLucideHeartIcon
import os.kei.ui.page.main.os.appLucideMoreIcon
import os.kei.ui.page.main.os.appLucidePlayIcon
import os.kei.ui.page.main.os.appLucideRefreshIcon
import os.kei.ui.page.main.os.appLucideWarningIcon
import os.kei.ui.page.main.student.BaGuideGalleryItem
import os.kei.ui.page.main.student.GuideRemoteImageAdaptive
import os.kei.ui.page.main.student.GuideVideoFullscreenActivity
import os.kei.ui.page.main.student.catalog.BaGuideCatalogEntry
import os.kei.ui.page.main.student.isRenderableGalleryImageUrl
import os.kei.ui.page.main.student.normalizeGuideMediaSource
import os.kei.ui.page.main.student.section.gallery.GuideWebMemoryLobbyCard
import os.kei.ui.page.main.student.section.gallery.GuideImageFullscreenDialog
import os.kei.ui.page.main.widget.core.AppCompactIconAction
import os.kei.ui.page.main.widget.core.AppStatusPillSize
import os.kei.ui.page.main.widget.core.AppSurfaceCard
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.core.CardLayoutRhythm
import os.kei.ui.page.main.widget.glass.AppDropdownSelector
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.glass.LiquidCircularProgressBar
import os.kei.ui.page.main.widget.glass.LiquidGlassActionMenu
import os.kei.ui.page.main.widget.glass.LiquidGlassActionMenuActionRow
import os.kei.ui.page.main.widget.motion.appExpandIn
import os.kei.ui.page.main.widget.motion.appExpandOut
import os.kei.ui.page.main.widget.sheet.SnapshotPopupPlacement
import os.kei.ui.page.main.widget.sheet.SnapshotWindowListPopup
import os.kei.ui.page.main.widget.sheet.capturePopupAnchor
import os.kei.ui.page.main.widget.status.AppStatusColors
import os.kei.ui.page.main.widget.status.StatusPill
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

private val MemoryLobbyMoreMenuMinWidth = 156.dp
private val MemoryLobbyMoreMenuMaxWidth = 196.dp
private val MemoryLobbyMoreMenuMaxHeight = 240.dp
private val MemoryLobbyVariantMenuMinWidth = 136.dp
private val MemoryLobbyVariantMenuMaxWidth = 196.dp
private val MemoryLobbyVariantMenuMaxHeight = 220.dp
internal val MemoryLobbyHeaderActionsWidth = 72.dp
private const val MemoryLobbyPreviewDecodeMinPx = 960
private const val MemoryLobbyPreviewDecodeMaxPx = 2048

private data class BaGuideMemoryLobbyMediaGroups(
    val videoItems: List<BaGuideGalleryItem>,
    val previewImageUrl: String,
)

@Composable
internal fun BaGuideMemoryLobbyHeader(
    totalCount: Int,
    displayedCount: Int,
    readyCount: Int,
    favoriteCount: Int,
    cachedCount: Int,
    searchActive: Boolean,
    favoritesHidden: Boolean,
    accent: Color,
    onToggleFavoritesHidden: () -> Unit,
) {
    val matchedCount = if (searchActive) displayedCount else totalCount
    val hasFavorites = favoriteCount > 0
    AppSurfaceCard(
        containerColor = MiuixTheme.colorScheme.surface.copy(alpha = 0.62f),
        borderColor = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.16f),
        showIndication = hasFavorites,
        onClick = if (hasFavorites) onToggleFavoritesHidden else null,
    ) {
        BaGuideMemoryLobbyHeaderMetrics(
            matchedLabel =
                stringResource(
                    if (searchActive) {
                        R.string.ba_catalog_student_bgm_metric_matched
                    } else {
                        R.string.ba_catalog_student_bgm_metric_students
                    },
                ),
            matchedCount = matchedCount,
            favoriteLabel = stringResource(R.string.ba_catalog_student_bgm_metric_favorites),
            favoriteCount = favoriteCount,
            readyLabel = stringResource(R.string.ba_catalog_memory_lobby_status_ready),
            readyCount = readyCount,
            cachedLabel = stringResource(R.string.ba_catalog_student_bgm_status_cached_detail),
            cachedCount = cachedCount,
            favoritesHidden = favoritesHidden,
            accent = accent,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BaGuideMemoryLobbyHeaderMetrics(
    matchedLabel: String,
    matchedCount: Int,
    favoriteLabel: String,
    favoriteCount: Int,
    readyLabel: String,
    readyCount: Int,
    cachedLabel: String,
    cachedCount: Int,
    favoritesHidden: Boolean,
    accent: Color,
) {
    FlowRow(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        maxItemsInEachRow = 4,
    ) {
        BaGuideMemoryLobbyMetricPill(
            label = matchedLabel,
            value = matchedCount,
            color = Color(0xFF6366F1),
        )
        BaGuideMemoryLobbyMetricPill(
            label = favoriteLabel,
            value = favoriteCount,
            color =
                if (favoritesHidden) {
                    MiuixTheme.colorScheme.onBackgroundVariant
                } else {
                    Color(0xFFEC4899)
                },
        )
        BaGuideMemoryLobbyMetricPill(
            label = readyLabel,
            value = readyCount,
            color = accent,
        )
        BaGuideMemoryLobbyMetricPill(
            label = cachedLabel,
            value = cachedCount,
            color = MiuixTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun BaGuideMemoryLobbyMetricPill(
    label: String,
    value: Int,
    color: Color,
) {
    StatusPill(
        label = "$label ${value.coerceAtLeast(0)}",
        color = color,
        size = AppStatusPillSize.Compact,
        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
internal fun BaGuideMemoryLobbyCard(
    entry: BaGuideCatalogEntry,
    lookupState: BaGuideMemoryLobbyLookupState,
    expanded: Boolean,
    favorite: Boolean,
    accent: Color,
    mediaAdaptiveRotationEnabled: Boolean,
    onToggleExpanded: () -> Unit,
    onResolve: () -> Unit,
    onOpenGuide: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    LaunchedEffect(expanded, lookupState, entry.contentId) {
        if (expanded && lookupState == BaGuideMemoryLobbyLookupState.Idle) {
            onResolve()
        }
    }
    var fullscreenImageUrl by remember(entry.contentId) { mutableStateOf<String?>(null) }
    val ready = lookupState as? BaGuideMemoryLobbyLookupState.Ready
    val firstFullscreenImageUrl =
        remember(ready?.item?.galleryItems) {
            ready
                ?.item
                ?.galleryItems
                .orEmpty()
                .firstFullscreenImageUrl()
        }
    val borderColor =
        when {
            favorite -> Color(0xFFEC4899).copy(alpha = 0.34f)
            ready != null -> accent.copy(alpha = 0.30f)
            else -> MiuixTheme.colorScheme.outline.copy(alpha = 0.16f)
        }
    val containerColor =
        when {
            favorite -> Color(0xFFEC4899).copy(alpha = 0.08f)
            ready != null -> accent.copy(alpha = 0.10f)
            else -> MiuixTheme.colorScheme.surface.copy(alpha = 0.58f)
        }
    val neutralTint = MiuixTheme.colorScheme.onBackgroundVariant
    val expandStateDescription =
        stringResource(
            if (expanded) {
                R.string.ba_catalog_memory_lobby_action_collapse
            } else {
                R.string.ba_catalog_memory_lobby_action_expand
            },
        )
    AppSurfaceCard(
        containerColor = containerColor,
        borderColor = borderColor,
        onClick = onToggleExpanded,
        onLongClick = onOpenGuide,
        stateDescription = expandStateDescription,
        clipContent = true,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CardLayoutRhythm.infoRowGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BaGuideCatalogEntryAvatar(
                        imageUrl = entry.iconUrl,
                        fallbackRes = R.drawable.ba_tab_skill,
                        size = 48.dp,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = entry.name,
                            modifier = Modifier.weight(1f),
                            color = MiuixTheme.colorScheme.onBackground,
                            fontSize = AppTypographyTokens.CompactTitle.fontSize,
                            lineHeight = AppTypographyTokens.CompactTitle.lineHeight,
                            fontWeight = AppTypographyTokens.CompactTitle.fontWeight,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        BaGuideMemoryLobbyStatusRow(
                            lookupState = lookupState,
                            resolvedItem = ready?.item,
                        )
                    }
                    if (entry.aliasDisplay.isNotBlank()) {
                        Text(
                            text = entry.aliasDisplay,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                            fontSize = AppTypographyTokens.Supporting.fontSize,
                            lineHeight = AppTypographyTokens.Supporting.lineHeight,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(
                    modifier = Modifier.width(MemoryLobbyHeaderActionsWidth),
                    horizontalArrangement = Arrangement.spacedBy(0.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BaGuideMemoryLobbyMoreActions(
                        favorite = favorite,
                        firstFullscreenImageUrl = firstFullscreenImageUrl,
                        iconTint = neutralTint,
                        onToggleFavorite = onToggleFavorite,
                        onOpenFullscreen = { fullscreenImageUrl = firstFullscreenImageUrl },
                        onOpenGuide = onOpenGuide,
                    )
                    Icon(
                        imageVector = if (expanded) appLucideChevronUpIcon() else appLucideChevronDownIcon(),
                        contentDescription = null,
                        tint = if (expanded) accent else neutralTint,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = appExpandIn(),
                exit = appExpandOut(),
            ) {
                BaGuideMemoryLobbyExpandedContent(
                    lookupState = lookupState,
                    accent = accent,
                    onOpenFullscreenImage = { imageUrl -> fullscreenImageUrl = imageUrl },
                )
            }
        }
    }
    fullscreenImageUrl?.let { imageUrl ->
        GuideImageFullscreenDialog(
            imageUrl = imageUrl,
            mediaAdaptiveRotationEnabled = mediaAdaptiveRotationEnabled,
            onDismiss = { fullscreenImageUrl = null },
        )
    }
}

@Composable
private fun BaGuideMemoryLobbyStatusRow(
    lookupState: BaGuideMemoryLobbyLookupState,
    resolvedItem: BaGuideMemoryLobbyResolvedItem?,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val statusLabel =
            when (lookupState) {
                BaGuideMemoryLobbyLookupState.Idle -> R.string.ba_catalog_memory_lobby_status_idle
                BaGuideMemoryLobbyLookupState.Loading -> R.string.ba_catalog_memory_lobby_status_resolving
                BaGuideMemoryLobbyLookupState.Missing -> R.string.ba_catalog_memory_lobby_status_missing
                is BaGuideMemoryLobbyLookupState.Ready -> R.string.ba_catalog_memory_lobby_status_ready
            }
        val statusColor =
            when (lookupState) {
                BaGuideMemoryLobbyLookupState.Idle -> MiuixTheme.colorScheme.onBackgroundVariant
                BaGuideMemoryLobbyLookupState.Loading -> AppStatusColors.Refreshing
                BaGuideMemoryLobbyLookupState.Missing -> AppStatusColors.Failed
                is BaGuideMemoryLobbyLookupState.Ready -> AppStatusColors.Fresh
            }
        val statusIcon =
            when (lookupState) {
                BaGuideMemoryLobbyLookupState.Idle -> appLucideDownloadIcon()
                BaGuideMemoryLobbyLookupState.Loading -> appLucideRefreshIcon()
                BaGuideMemoryLobbyLookupState.Missing -> appLucideWarningIcon()
                is BaGuideMemoryLobbyLookupState.Ready -> appLucideConfirmIcon()
            }
        BaGuideCatalogStatusIconPill(
            label = stringResource(statusLabel),
            color = statusColor,
            icon = statusIcon,
        )
        if (resolvedItem?.fromCache == true) {
            BaGuideCatalogStatusIconPill(
                label = stringResource(R.string.ba_catalog_student_bgm_status_cached_detail),
                color = MiuixTheme.colorScheme.primary,
                icon = appLucideDatabaseIcon(),
            )
        }
    }
}

@Composable
private fun BaGuideMemoryLobbyExpandedContent(
    lookupState: BaGuideMemoryLobbyLookupState,
    accent: Color,
    onOpenFullscreenImage: (String) -> Unit,
) {
    when (lookupState) {
        BaGuideMemoryLobbyLookupState.Idle,
        BaGuideMemoryLobbyLookupState.Loading,
        -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LiquidCircularProgressBar(
                    progress = { 0.35f },
                    size = 16.dp,
                    strokeWidth = 2.dp,
                    activeColor = MiuixTheme.colorScheme.primary,
                    inactiveColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.30f),
                )
                Text(
                    text = stringResource(R.string.ba_catalog_memory_lobby_loading_body),
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    fontSize = AppTypographyTokens.Supporting.fontSize,
                    lineHeight = AppTypographyTokens.Supporting.lineHeight,
                )
            }
        }

        BaGuideMemoryLobbyLookupState.Missing -> {
            Text(
                text = stringResource(R.string.ba_catalog_memory_lobby_missing_body),
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                fontSize = AppTypographyTokens.Supporting.fontSize,
                lineHeight = AppTypographyTokens.Supporting.lineHeight,
            )
        }

        is BaGuideMemoryLobbyLookupState.Ready -> {
            val item = lookupState.item
            val mediaGroups =
                remember(item.galleryItems) {
                    item.galleryItems.toMemoryLobbyMediaGroups()
                }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item.galleryItems.filter { it.webMemoryLobby != null }.forEach { lobby ->
                    GuideWebMemoryLobbyCard(lobby, mediaGroups.previewImageUrl)
                }
                if (mediaGroups.videoItems.isNotEmpty()) {
                    BaGuideMemoryLobbyVideoGroup(
                        items = mediaGroups.videoItems,
                        previewFallbackUrl = mediaGroups.previewImageUrl,
                        unlockLevel = item.memoryUnlockLevel,
                        accent = accent,
                    )
                } else if (item.galleryItems.any { it.webMemoryLobby != null }) {
                    // The dynamic lobby card already owns its poster and entry point.
                } else if (mediaGroups.previewImageUrl.isNotBlank()) {
                    BaGuideMemoryLobbyImagePreviewGroup(
                        previewImageUrl = mediaGroups.previewImageUrl,
                        unlockLevel = item.memoryUnlockLevel,
                        accent = accent,
                        onOpenFullscreen = { onOpenFullscreenImage(mediaGroups.previewImageUrl) },
                    )
                } else {
                    Text(
                        text = stringResource(R.string.ba_catalog_memory_lobby_missing_body),
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        fontSize = AppTypographyTokens.Supporting.fontSize,
                        lineHeight = AppTypographyTokens.Supporting.lineHeight,
                    )
                }
            }
        }
    }
}

@Composable
private fun BaGuideMemoryLobbyMoreActions(
    favorite: Boolean,
    firstFullscreenImageUrl: String,
    iconTint: Color,
    onToggleFavorite: () -> Unit,
    onOpenFullscreen: () -> Unit,
    onOpenGuide: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var menuAnchorBounds by remember { mutableStateOf<IntRect?>(null) }
    val moreIcon = appLucideMoreIcon()
    val favoriteIcon = appLucideHeartIcon()
    val fullscreenIcon = appLucideFullscreenIcon()
    val openGuideIcon = appLucideExternalLinkIcon()
    Box(
        modifier = Modifier.capturePopupAnchor { bounds -> menuAnchorBounds = bounds },
        contentAlignment = Alignment.Center,
    ) {
        AppCompactIconAction(
            icon = moreIcon,
            contentDescription = stringResource(R.string.ba_catalog_memory_lobby_action_more),
            onClick = { menuExpanded = !menuExpanded },
            tint = iconTint,
            visualSize = 38.dp,
        )
        key("ba-guide-memory-lobby-action-popup") {
            SnapshotWindowListPopup(
                show = menuExpanded,
                alignment = PopupPositionProvider.Align.BottomEnd,
                anchorBoundsProvider = { menuAnchorBounds },
                placement = SnapshotPopupPlacement.ButtonEnd,
                onDismissRequest = { menuExpanded = false },
            ) {
                LiquidGlassActionMenu(
                    minWidth = MemoryLobbyMoreMenuMinWidth,
                    maxWidth = MemoryLobbyMoreMenuMaxWidth,
                    maxHeight = MemoryLobbyMoreMenuMaxHeight,
                    items =
                        buildList {
                            add(
                                LiquidGlassActionMenuActionRow(
                                    id = "toggle_favorite",
                                    text =
                                        stringResource(
                                            if (favorite) {
                                                R.string.ba_catalog_memory_lobby_menu_unfavorite
                                            } else {
                                                R.string.ba_catalog_memory_lobby_menu_favorite
                                            },
                                        ),
                                    leadingIcon = favoriteIcon,
                                    onClick = {
                                        menuExpanded = false
                                        onToggleFavorite()
                                    },
                                ),
                            )
                            if (firstFullscreenImageUrl.isNotBlank()) {
                                add(
                                    LiquidGlassActionMenuActionRow(
                                        id = "open_fullscreen",
                                        text = stringResource(R.string.ba_catalog_memory_lobby_action_fullscreen),
                                        leadingIcon = fullscreenIcon,
                                        onClick = {
                                            menuExpanded = false
                                            onOpenFullscreen()
                                        },
                                    ),
                                )
                            }
                            add(
                                LiquidGlassActionMenuActionRow(
                                    id = "open_guide",
                                    text = stringResource(R.string.ba_catalog_memory_lobby_action_open_guide),
                                    leadingIcon = openGuideIcon,
                                    onClick = {
                                        menuExpanded = false
                                        onOpenGuide()
                                    },
                                ),
                            )
                        },
                    onDismissRequest = { menuExpanded = false },
                )
            }
        }
    }
}

@Composable
private fun BaGuideMemoryLobbyVideoGroup(
    items: List<BaGuideGalleryItem>,
    previewFallbackUrl: String,
    unlockLevel: String,
    accent: Color,
) {
    if (items.isEmpty()) return
    val context = LocalContext.current
    val itemKey =
        remember(items) {
            items.joinToString(separator = "|") { item ->
                item.mediaUrl.ifBlank { item.imageUrl }
            }
        }
    var selectedIndex by rememberSaveable(itemKey) { mutableStateOf(0) }
    LaunchedEffect(items.size) {
        if (selectedIndex !in items.indices) selectedIndex = 0
    }
    val selectedItem = items.getOrElse(selectedIndex) { items.first() }
    val displayMediaUrl = remember(selectedItem.mediaUrl) { normalizeGuideMediaSource(selectedItem.mediaUrl) }
    val displayPreviewUrl =
        remember(selectedItem.imageUrl, previewFallbackUrl) {
            val staticPreview = previewFallbackUrl.takeIf(::isRenderableGalleryImageUrl).orEmpty()
            val videoPreview = selectedItem.imageUrl.takeIf(::isRenderableGalleryImageUrl).orEmpty()
            normalizeGuideMediaSource(staticPreview.ifBlank { videoPreview })
        }
    var videoSourceRect by remember(displayMediaUrl) { mutableStateOf<Rect?>(null) }
    val fallbackOptionLabels =
        List(items.size.coerceAtLeast(1)) { index ->
            stringResource(R.string.guide_gallery_video_format, index + 1)
        }
    val optionLabels =
        remember(items, fallbackOptionLabels) {
            if (items.size <= 1) {
                listOf(fallbackOptionLabels.first())
            } else {
                items.mapIndexed { index, item ->
                    item.title
                        .trim()
                        .takeIf { it.isNotBlank() }
                        ?: fallbackOptionLabels.getOrElse(index) { fallbackOptionLabels.first() }
                }
            }
        }
    val openPictureInPicture = {
        if (displayMediaUrl.isBlank()) {
            context.showToast(context.getString(R.string.guide_media_video_url_invalid))
        } else {
            GuideVideoFullscreenActivity.launchInPictureInPicture(
                context = context,
                mediaUrl = displayMediaUrl,
                previewImageUrl = displayPreviewUrl,
                startPositionMs = 0L,
                sourceRectHint = videoSourceRect,
            )
        }
    }
    val openFullscreen = {
        if (displayMediaUrl.isBlank()) {
            context.showToast(context.getString(R.string.guide_media_video_url_invalid))
        } else {
            GuideVideoFullscreenActivity.launch(
                context = context,
                mediaUrl = displayMediaUrl,
                previewImageUrl = displayPreviewUrl,
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.guide_gallery_memorial_lobby),
                color = MiuixTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BaGuideMemoryLobbyUnlockPill(
                unlockLevel = unlockLevel,
                accent = accent,
            )
            BaGuideMemoryLobbyVariantSelector(
                optionLabels = optionLabels,
                selectedIndex = selectedIndex,
                onSelectedIndexChange = { selectedIndex = it },
            )
            if (displayMediaUrl.isNotBlank()) {
                AppCompactIconAction(
                    icon = appLucidePlayIcon(),
                    contentDescription = stringResource(R.string.guide_gallery_memorial_lobby_pip_play),
                    onClick = openPictureInPicture,
                    tint = Color(0xFF3B82F6),
                    visualSize = 38.dp,
                )
                AppCompactIconAction(
                    icon = appLucideFullscreenIcon(),
                    contentDescription = stringResource(R.string.guide_gallery_memorial_lobby_pip_fullscreen),
                    onClick = openFullscreen,
                    tint = Color(0xFF3B82F6),
                    visualSize = 38.dp,
                )
            }
        }
        if (displayMediaUrl.isBlank()) {
            Text(
                text = stringResource(R.string.guide_gallery_video_not_found),
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                fontSize = AppTypographyTokens.Supporting.fontSize,
                lineHeight = AppTypographyTokens.Supporting.lineHeight,
            )
        } else if (displayPreviewUrl.isNotBlank()) {
            BaGuideMemoryLobbyVideoPreview(
                previewImageUrl = displayPreviewUrl,
                onBoundsChanged = { rect ->
                    if (videoSourceRect != rect) {
                        videoSourceRect = rect
                    }
                },
                onClick = openPictureInPicture,
            )
        }
    }
}

@Composable
internal fun BaGuideMemoryLobbyVariantSelector(
    optionLabels: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember(optionLabels) { mutableStateOf(false) }
    if (optionLabels.size <= 1) return

    AppDropdownSelector(
        selectedText =
            optionLabels.getOrElse(selectedIndex) {
                stringResource(R.string.guide_gallery_video_format, 1)
            },
        options = optionLabels,
        selectedIndex = selectedIndex,
        expanded = showPicker,
        onExpandedChange = { showPicker = it },
        onSelectedIndexChange = onSelectedIndexChange,
        modifier = modifier.widthIn(min = 54.dp, max = 96.dp),
        variant = GlassVariant.Compact,
        textColor = Color(0xFF3B82F6),
        minHeight = 36.dp,
        horizontalPadding = 8.dp,
        verticalPadding = 6.dp,
        anchorTextMaxLines = 1,
        anchorTextOverflow = TextOverflow.Ellipsis,
        anchorTextSoftWrap = false,
        anchorTextSize = AppTypographyTokens.Supporting.fontSize,
        anchorTextLineHeight = AppTypographyTokens.Supporting.lineHeight,
        dropdownItemTextMaxLines = 1,
        popupMinWidth = MemoryLobbyVariantMenuMinWidth,
        popupMaxWidth = MemoryLobbyVariantMenuMaxWidth,
        popupMaxHeight = MemoryLobbyVariantMenuMaxHeight,
        dropdownItemVariant = GlassVariant.SheetAction,
        anchorAlignment = Alignment.Center,
        alignment = PopupPositionProvider.Align.BottomEnd,
        placement = SnapshotPopupPlacement.ButtonEnd,
    )
}

@Composable
private fun BaGuideMemoryLobbyImagePreviewGroup(
    previewImageUrl: String,
    unlockLevel: String,
    accent: Color,
    onOpenFullscreen: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.guide_gallery_memorial_lobby),
                color = MiuixTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BaGuideMemoryLobbyUnlockPill(
                unlockLevel = unlockLevel,
                accent = accent,
            )
            AppCompactIconAction(
                icon = appLucideFullscreenIcon(),
                contentDescription = stringResource(R.string.ba_catalog_memory_lobby_action_fullscreen),
                onClick = onOpenFullscreen,
                tint = Color(0xFF3B82F6),
                visualSize = 38.dp,
            )
        }
        BaGuideMemoryLobbyVideoPreview(
            previewImageUrl = previewImageUrl,
            onBoundsChanged = {},
            onClick = onOpenFullscreen,
        )
    }
}

@Composable
private fun BaGuideMemoryLobbyUnlockPill(
    unlockLevel: String,
    accent: Color,
) {
    val label = remember(unlockLevel) { unlockLevel.memoryLobbyUnlockLevelPillLabel() }
    if (label.isBlank()) return
    StatusPill(
        label = label,
        color = accent,
        size = AppStatusPillSize.Compact,
    )
}

@Composable
private fun BaGuideMemoryLobbyVideoPreview(
    previewImageUrl: String,
    onBoundsChanged: (Rect?) -> Unit,
    onClick: () -> Unit,
) {
    var loading by remember(previewImageUrl) { mutableStateOf(false) }
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInWindow()
                    val rect =
                        Rect(
                            bounds.left.roundToInt(),
                            bounds.top.roundToInt(),
                            bounds.right.roundToInt(),
                            bounds.bottom.roundToInt(),
                        )
                    onBoundsChanged(rect.takeUnless { it.isEmpty })
                }.clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val previewDecodeDimension =
            remember(maxWidth, density) {
                with(density) {
                    maxWidth
                        .roundToPx()
                        .coerceIn(MemoryLobbyPreviewDecodeMinPx, MemoryLobbyPreviewDecodeMaxPx)
                }
            }
        GuideRemoteImageAdaptive(
            imageUrl = previewImageUrl,
            maxDecodeDimension = previewDecodeDimension,
            onLoadingChanged = { loading = it },
        )
        if (loading) {
            LiquidCircularProgressBar(
                progress = { 0.35f },
                size = 22.dp,
                strokeWidth = 2.5.dp,
                activeColor = Color(0xFF3B82F6),
                inactiveColor = Color(0xFF3B82F6).copy(alpha = 0.24f),
            )
        }
    }
}

private fun String.memoryLobbyUnlockLevelPillLabel(): String {
    val trimmed = trim()
    if (trimmed.isBlank()) return ""
    return Regex("""\d+""").find(trimmed)?.value ?: trimmed
}

private fun BaGuideGalleryItem.isMemoryLobbyVideo(): Boolean = mediaType.equals("video", ignoreCase = true)

private fun List<BaGuideGalleryItem>.toMemoryLobbyMediaGroups(): BaGuideMemoryLobbyMediaGroups {
    val imageItems = filterNot(BaGuideGalleryItem::isMemoryLobbyVideo)
    val videoItems = filter(BaGuideGalleryItem::isMemoryLobbyVideo)
    return BaGuideMemoryLobbyMediaGroups(
        videoItems = videoItems,
        previewImageUrl = imageItems.firstFullscreenImageUrl(),
    )
}

private fun List<BaGuideGalleryItem>.firstFullscreenImageUrl(): String =
    firstOrNull { item ->
        !item.isMemoryLobbyVideo() &&
            item.mediaType.lowercase() != "audio" &&
            (
                isRenderableGalleryImageUrl(item.imageUrl) ||
                    isRenderableGalleryImageUrl(item.mediaUrl)
            )
    }?.let { item ->
        item.imageUrl.ifBlank { item.mediaUrl }
    }.orEmpty()
