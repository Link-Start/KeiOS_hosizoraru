@file:Suppress("FunctionName")

package os.kei.ui.page.main.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.RoundedRectangle
import os.kei.R
import os.kei.feature.home.model.HomeOverviewCard
import os.kei.ui.page.main.model.BottomPage
import os.kei.ui.page.main.model.toHomeOverviewCardOrNull
import os.kei.ui.page.main.os.appLucideCloseIcon
import os.kei.ui.page.main.widget.glass.AppLiquidIconButton
import os.kei.ui.page.main.widget.glass.AppSwitch
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.sheet.SheetContentColumn
import os.kei.ui.page.main.widget.sheet.SheetControlRow
import os.kei.ui.page.main.widget.sheet.SheetDescriptionText
import os.kei.ui.page.main.widget.sheet.SheetSectionCard
import os.kei.ui.page.main.widget.sheet.SheetSectionTitle
import os.kei.ui.page.main.widget.sheet.SnapshotWindowBottomSheet
import os.kei.ui.page.main.widget.status.StatusPill
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.max
import androidx.compose.ui.graphics.Shadow as ComposeTextShadow

private val HOME_KEI_TITLE_GRADIENT_COLORS =
    listOf(
        Color(0xFFFFD2DE),
        Color(0xFFFFCAD9),
        Color(0xFFFF99BB),
        Color(0xFFFF76A5),
        Color(0xFFFF6098),
        Color(0xFFFF5893),
    )
private val HOME_HERO_SHARED_AVOIDANCE_LIFT = 72.dp
private const val HOME_HERO_AVOIDANCE_ALPHA_WEIGHT = 0.28f
private const val HOME_HERO_FOREGROUND_BLUR_RADIUS_DP = 50f

internal data class HomeHeaderStatusPillState(
    val label: String,
    val color: Color,
    val minWidth: Dp,
    val contentPadding: PaddingValues? = null,
)

@Composable
internal fun HomePageControlSheet(
    show: Boolean,
    actionBarBackdrop: Backdrop,
    visibleBottomPages: Set<BottomPage>,
    visibleOverviewCards: Set<HomeOverviewCard>,
    homeSheetTitle: String,
    tableTitle: String,
    tableDesc: String,
    homeCardMcp: String,
    homeCardGitHub: String,
    homeCardBa: String,
    showCacheFreshnessInCards: Boolean,
    cacheFreshnessToggleLabel: String,
    cacheFreshnessToggleDesc: String,
    debugSectionTitle: String,
    onDismissRequest: () -> Unit,
    onBottomPageVisibilityChange: (BottomPage, Boolean) -> Unit,
    onOverviewCardVisibilityChange: (HomeOverviewCard, Boolean) -> Unit,
    onCacheFreshnessVisibilityChange: (Boolean) -> Unit,
) {
    SnapshotWindowBottomSheet(
        show = show,
        title = homeSheetTitle,
        onDismissRequest = onDismissRequest,
        startAction = {
            AppLiquidIconButton(
                backdrop = actionBarBackdrop,
                variant = GlassVariant.Bar,
                icon = appLucideCloseIcon(),
                contentDescription =
                    androidx.compose.ui.res
                        .stringResource(R.string.common_close),
                onClick = onDismissRequest,
            )
        },
    ) {
        SheetContentColumn(
            scrollable = true,
            verticalSpacing = 10.dp,
        ) {
            SheetSectionTitle(tableTitle)
            SheetSectionCard(verticalSpacing = 8.dp) {
                HomePageVisibilityTableHeader()
                HomePageVisibilityTableRow(
                    page = BottomPage.Home,
                    cardLabel = null,
                    bottomVisible = true,
                    cardVisible = false,
                    bottomFixed = true,
                    cardAvailable = false,
                    onBottomVisibleChange = {},
                    onCardVisibleChange = {},
                )
                BottomPage.entries
                    .filter { it != BottomPage.Home }
                    .forEach { page ->
                        val overviewCard = page.toHomeOverviewCardOrNull()
                        val bottomVisible = visibleBottomPages.contains(page)
                        HomePageVisibilityTableRow(
                            page = page,
                            cardLabel =
                                when (overviewCard) {
                                    HomeOverviewCard.MCP -> homeCardMcp
                                    HomeOverviewCard.GITHUB -> homeCardGitHub
                                    HomeOverviewCard.BA -> homeCardBa
                                    null -> null
                                },
                            bottomVisible = bottomVisible,
                            cardVisible = overviewCard?.let(visibleOverviewCards::contains) == true,
                            bottomFixed = false,
                            cardAvailable = overviewCard != null && bottomVisible,
                            onBottomVisibleChange = { checked ->
                                onBottomPageVisibilityChange(page, checked)
                            },
                            onCardVisibleChange = { checked ->
                                if (overviewCard != null) {
                                    onOverviewCardVisibilityChange(overviewCard, checked)
                                }
                            },
                        )
                    }
                SheetDescriptionText(text = tableDesc)
            }
            SheetSectionTitle(debugSectionTitle)
            SheetSectionCard(verticalSpacing = 10.dp) {
                SheetControlRow(label = cacheFreshnessToggleLabel) {
                    AppSwitch(
                        checked = showCacheFreshnessInCards,
                        onCheckedChange = onCacheFreshnessVisibilityChange,
                    )
                }
                SheetDescriptionText(text = cacheFreshnessToggleDesc)
            }
        }
    }
}

@Composable
private fun HomePageVisibilityTableHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text =
                androidx.compose.ui.res
                    .stringResource(R.string.home_sheet_column_section),
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1.35f),
        )
        Text(
            text =
                androidx.compose.ui.res
                    .stringResource(R.string.home_sheet_column_bottom),
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Text(
            text =
                androidx.compose.ui.res
                    .stringResource(R.string.home_sheet_column_card),
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HomePageVisibilityTableRow(
    page: BottomPage,
    cardLabel: String?,
    bottomVisible: Boolean,
    cardVisible: Boolean,
    bottomFixed: Boolean,
    cardAvailable: Boolean,
    onBottomVisibleChange: (Boolean) -> Unit,
    onCardVisibleChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 58.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier.weight(1.35f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            HomeBottomPageLabel(
                page = page,
                modifier = Modifier.defaultMinSize(minHeight = 24.dp),
            )
            if (cardLabel != null && cardLabel != page.label) {
                Text(
                    text = cardLabel,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                )
            }
        }
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            if (bottomFixed) {
                StatusPill(
                    label =
                        androidx.compose.ui.res
                            .stringResource(R.string.common_status_fixed_visible),
                    color = Color(0xFF2563EB),
                )
            } else {
                AppSwitch(
                    checked = bottomVisible,
                    onCheckedChange = onBottomVisibleChange,
                )
            }
        }
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            if (cardAvailable) {
                AppSwitch(
                    checked = cardVisible,
                    onCheckedChange = onCardVisibleChange,
                )
            } else {
                StatusPill(
                    label =
                        androidx.compose.ui.res
                            .stringResource(R.string.home_sheet_card_unavailable),
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                )
            }
        }
    }
}

@Composable
internal fun HomePageHero(
    foregroundBackdrop: LayerBackdrop?,
    foregroundBlurEnabled: Boolean,
    homeIconHdrEnabled: Boolean,
    hdrSweepProgress: () -> Float,
    homeHeaderSinkOffset: Dp,
    logoPadding: PaddingValues,
    layoutDirection: LayoutDirection,
    homeAppName: String,
    homeTagline: String,
    appVersionText: String,
    avoidanceProgress: () -> Float,
    iconProgress: () -> Float,
    titleProgress: () -> Float,
    summaryProgress: () -> Float,
    statusPills: List<HomeHeaderStatusPillState>,
    onHeroHeightChanged: (Int) -> Unit,
    onIconBottomChanged: (Float) -> Unit,
    onTitleBottomChanged: (Float) -> Unit,
    onSummaryBottomChanged: (Float) -> Unit,
) {
    val density = LocalDensity.current
    val sharedAvoidanceLiftPx = with(density) { HOME_HERO_SHARED_AVOIDANCE_LIFT.toPx() }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    top = logoPadding.calculateTopPadding() + 36.dp + homeHeaderSinkOffset,
                    start = logoPadding.calculateStartPadding(layoutDirection),
                    end = logoPadding.calculateEndPadding(layoutDirection),
                ).onSizeChanged { size -> onHeroHeightChanged(size.height) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(88.dp)
                    .graphicsLayer {
                        val avoidanceValue = avoidanceProgress()
                        val iconValue = iconProgress()
                        val titleValue = titleProgress()
                        val summaryValue = summaryProgress()
                        val sharedLiftProgress =
                            homeHeroSharedLiftProgress(
                                avoidanceProgress = avoidanceValue,
                                iconProgress = iconValue,
                                titleProgress = titleValue,
                                summaryProgress = summaryValue,
                            )
                        val iconExitProgress =
                            homeHeroIconExitProgress(
                                avoidanceProgress = avoidanceValue,
                                iconProgress = iconValue,
                            )
                        alpha = 1f - iconExitProgress
                        translationY = -sharedAvoidanceLiftPx * sharedLiftProgress
                        scaleX = 1f - (iconExitProgress * 0.05f)
                        scaleY = 1f - (iconExitProgress * 0.05f)
                    }.onGloballyPositioned { coordinates ->
                        onIconBottomChanged(coordinates.positionInWindow().y + coordinates.size.height)
                    },
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_kei_logo_color),
                contentDescription = null,
                modifier =
                    Modifier
                        .size(88.dp)
                        .graphicsLayer {
                            val avoidanceValue = avoidanceProgress()
                            val iconValue = iconProgress()
                            val iconExitProgress =
                                homeHeroIconExitProgress(
                                    avoidanceProgress = avoidanceValue,
                                    iconProgress = iconValue,
                                )
                            alpha = (1f - iconExitProgress) * 0.95f
                        }.homeKeiHdrAccent(
                            enabled = homeIconHdrEnabled,
                            sweepProgress = hdrSweepProgress,
                            radialAlpha = 0.30f,
                            radialRadiusScale = 0.72f,
                            radialCenterX = 0.5f,
                            radialCenterY = 0.48f,
                        ),
            )
        }

        val titleTextStyle =
            remember {
                TextStyle(
                    brush =
                        Brush.linearGradient(
                            colors = HOME_KEI_TITLE_GRADIENT_COLORS,
                            start = Offset(14f, 6f),
                            end = Offset(260f, 104f),
                        ),
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                    shadow =
                        ComposeTextShadow(
                            color = Color(0x55FF74A6),
                            offset = Offset(0f, 3f),
                            blurRadius = 16f,
                        ),
                )
            }
        Box(
            modifier =
                Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .onGloballyPositioned { coordinates ->
                        onTitleBottomChanged(coordinates.positionInWindow().y + coordinates.size.height)
                    }.graphicsLayer {
                        val avoidanceValue = avoidanceProgress()
                        val iconValue = iconProgress()
                        val titleValue = titleProgress()
                        val summaryValue = summaryProgress()
                        val sharedLiftProgress =
                            homeHeroSharedLiftProgress(
                                avoidanceProgress = avoidanceValue,
                                iconProgress = iconValue,
                                titleProgress = titleValue,
                                summaryProgress = summaryValue,
                            )
                        val titleExitProgress =
                            homeHeroTitleExitProgress(
                                avoidanceProgress = avoidanceValue,
                                titleProgress = titleValue,
                            )
                        alpha = 1f - titleExitProgress
                        translationY = -sharedAvoidanceLiftPx * sharedLiftProgress
                        scaleX = 1f - (titleExitProgress * 0.05f)
                        scaleY = 1f - (titleExitProgress * 0.05f)
                    }.homeKeiHdrAccent(
                        enabled = homeIconHdrEnabled,
                        sweepProgress = hdrSweepProgress,
                        radialAlpha = 0.26f,
                        radialRadiusScale = 0.82f,
                        radialCenterX = 0.32f,
                        radialCenterY = 0.34f,
                    ),
        ) {
            BasicText(
                text = homeAppName,
                style = titleTextStyle,
            )
            BasicText(
                text = homeAppName,
                style = titleTextStyle.copy(shadow = null),
                modifier =
                    Modifier
                        .graphicsLayer { alpha = 0.42f }
                        .homeHeroForegroundBlur(
                            backdrop = foregroundBackdrop,
                            enabled = foregroundBlurEnabled,
                            shape = RoundedRectangle(18.dp),
                            blurRadiusDp = HOME_HERO_FOREGROUND_BLUR_RADIUS_DP,
                        ),
            )
        }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val avoidanceValue = avoidanceProgress()
                        val iconValue = iconProgress()
                        val titleValue = titleProgress()
                        val summaryValue = summaryProgress()
                        val sharedLiftProgress =
                            homeHeroSharedLiftProgress(
                                avoidanceProgress = avoidanceValue,
                                iconProgress = iconValue,
                                titleProgress = titleValue,
                                summaryProgress = summaryValue,
                            )
                        val summaryExitProgress =
                            homeHeroSummaryExitProgress(
                                avoidanceProgress = avoidanceValue,
                                summaryProgress = summaryValue,
                            )
                        alpha = 1f - summaryExitProgress
                        translationY = -sharedAvoidanceLiftPx * sharedLiftProgress
                        scaleX = 1f - (summaryExitProgress * 0.05f)
                        scaleY = 1f - (summaryExitProgress * 0.05f)
                    }.onGloballyPositioned { coordinates ->
                        onSummaryBottomChanged(coordinates.positionInWindow().y + coordinates.size.height)
                    },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = homeTagline,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            Text(
                text = appVersionText,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp),
            )
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            ) {
                statusPills.forEach { pill ->
                    val modifier = Modifier.defaultMinSize(minWidth = pill.minWidth)
                    if (pill.contentPadding == null) {
                        StatusPill(
                            label = pill.label,
                            color = pill.color,
                            modifier = modifier,
                        )
                    } else {
                        StatusPill(
                            label = pill.label,
                            color = pill.color,
                            modifier = modifier,
                            contentPadding = pill.contentPadding,
                        )
                    }
                }
            }
        }
    }
}

private fun homeHeroSharedLiftProgress(
    avoidanceProgress: Float,
    iconProgress: Float,
    titleProgress: Float,
    summaryProgress: Float,
): Float =
    max(
        avoidanceProgress,
        max(iconProgress, max(titleProgress, summaryProgress)),
    )

private fun homeHeroIconExitProgress(
    avoidanceProgress: Float,
    iconProgress: Float,
): Float = max(iconProgress, avoidanceProgress * HOME_HERO_AVOIDANCE_ALPHA_WEIGHT)

private fun homeHeroTitleExitProgress(
    avoidanceProgress: Float,
    titleProgress: Float,
): Float = max(titleProgress, avoidanceProgress * HOME_HERO_AVOIDANCE_ALPHA_WEIGHT)

private fun homeHeroSummaryExitProgress(
    avoidanceProgress: Float,
    summaryProgress: Float,
): Float = max(summaryProgress, avoidanceProgress * HOME_HERO_AVOIDANCE_ALPHA_WEIGHT)

@Composable
internal fun HomePageHeroSpacer(
    logoHeightDp: Dp,
    logoPadding: PaddingValues,
    listContentPadding: PaddingValues,
    homeHeaderSinkOffset: Dp,
    onLogoHeightPxChanged: (Int) -> Unit,
    onLogoAreaBottomChanged: (Float) -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(
                logoHeightDp + 36.dp +
                    logoPadding.calculateTopPadding() -
                    listContentPadding.calculateTopPadding() + 90.dp +
                    homeHeaderSinkOffset,
            ).onSizeChanged { size -> onLogoHeightPxChanged(size.height) }
            .onGloballyPositioned { coordinates ->
                onLogoAreaBottomChanged(coordinates.positionInWindow().y + coordinates.size.height)
            },
    )
}

@Composable
internal fun HomePageOverviewCards(
    visibleOverviewCards: Set<HomeOverviewCard>,
    homeCardBackdrop: Backdrop?,
    blurEnabled: Boolean,
    homeNa: String,
    homeCardMcp: String,
    mcpStats: List<HomeCardStatItem>,
    homeCardGitHub: String,
    githubStats: List<HomeCardStatItem>,
    onOpenGitHubPage: () -> Unit,
    homeCardBa: String,
    baStats: List<HomeCardStatItem>,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Top,
    ) {
        if (visibleOverviewCards.contains(HomeOverviewCard.MCP)) {
            HomeInfoCard(
                backdrop = homeCardBackdrop,
                blurEnabled = blurEnabled,
            ) {
                HomeInfoGridCard(
                    title = homeCardMcp,
                    naText = homeNa,
                    columns = 3,
                    stats = mcpStats,
                )
            }
        }

        if (visibleOverviewCards.contains(HomeOverviewCard.GITHUB)) {
            HomeInfoCard(
                backdrop = homeCardBackdrop,
                blurEnabled = blurEnabled,
                onClick = onOpenGitHubPage,
            ) {
                HomeInfoGridCard(
                    title = homeCardGitHub,
                    naText = homeNa,
                    columns = 3,
                    stats = githubStats,
                )
            }
        }

        if (visibleOverviewCards.contains(HomeOverviewCard.BA)) {
            HomeInfoCard(
                backdrop = homeCardBackdrop,
                blurEnabled = blurEnabled,
            ) {
                HomeInfoGridCard(
                    title = homeCardBa,
                    naText = homeNa,
                    columns = 3,
                    stats = baStats,
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
    }
}
