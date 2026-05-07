package os.kei.ui.page.main.debug.liquidv2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.capsule.ContinuousCapsule
import os.kei.R
import os.kei.ui.page.main.os.appLucideBranchIcon
import os.kei.ui.page.main.os.appLucideConfigIcon
import os.kei.ui.page.main.os.appLucideDownloadIcon
import os.kei.ui.page.main.os.appLucideHomeIcon
import os.kei.ui.page.main.os.appLucideMusicIcon
import os.kei.ui.page.main.os.appLucidePackageIcon
import os.kei.ui.page.main.os.appLucidePlayIcon
import os.kei.ui.page.main.os.appLucideSearchIcon
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun V2NavigationDockShowcase(
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    var kyantIndex by remember { mutableIntStateOf(1) }
    var splitIndex by remember { mutableIntStateOf(0) }
    val palette = rememberV2LiquidGlassPalette()
    val kyantItems = rememberRound5DockItems(includeBadge = true)
    val splitItems = rememberRound5DockItems(includeBadge = false).take(3)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        V2SampleLabel(stringResource(R.string.debug_v2_liquid_navigation_kyant_dock))
        V2GlassBottomTabs(
            items = kyantItems,
            selectedIndex = kyantIndex,
            onSelectedIndexChange = { kyantIndex = it },
            backdrop = backdrop,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            activeTint = palette.accent.copy(alpha = 0.18f),
            spec = V2LiquidDockSpec(
                height = 72.dp,
                itemMinWidth = 58.dp,
                outerPadding = 6.dp,
                indicatorInset = 7.dp,
                selectedBlobStyle = V2LiquidMaterialStyle.Dock,
                materialSpec = V2LiquidDockMaterialSpec(
                    rimLightAlpha = 0.44f,
                    edgeChromaticAlpha = 0.18f,
                    causticAlpha = 0.13f,
                    readabilityFillAlpha = 0.07f
                ),
                blobSpec = V2LiquidDockBlobSpec(
                    minWidthFraction = 1.00f,
                    stretchOnPress = 0.08f,
                    stretchOnDrag = 0.22f,
                    liftDp = 1.6.dp
                )
            )
        )
        V2SampleLabel(stringResource(R.string.debug_v2_liquid_navigation_split_dock))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            V2GlassBottomTabs(
                items = splitItems,
                selectedIndex = splitIndex,
                onSelectedIndexChange = { splitIndex = it },
                backdrop = backdrop,
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp),
                compact = true,
                labelPolicy = V2GlassTabLabelPolicy.Never,
                activeTint = palette.accent.copy(alpha = 0.16f),
                scrollBehavior = V2LiquidDockScrollBehavior(V2LiquidDockMode.SplitDock),
                spec = V2LiquidDockSpec(
                    height = 64.dp,
                    itemMinWidth = 46.dp,
                    outerPadding = 5.dp,
                    indicatorInset = 6.dp,
                    selectedBlobStyle = V2LiquidMaterialStyle.Dock,
                    labelPolicy = V2GlassTabLabelPolicy.Never,
                    layoutSpec = V2LiquidDockLayoutSpec(
                        mode = V2LiquidDockMode.SplitDock,
                        height = 64.dp,
                        itemMinWidth = 46.dp,
                        outerPadding = 5.dp,
                        indicatorInset = 6.dp,
                        labelPolicy = V2GlassTabLabelPolicy.Never,
                        iconSize = 23.dp
                    )
                )
            )
            V2SplitDockMediaCapsule(backdrop = backdrop)
            V2GlassIconButton(
                icon = appLucideSearchIcon(),
                contentDescription = stringResource(R.string.debug_v2_liquid_inputs_search_placeholder),
                backdrop = backdrop,
                modifier = Modifier.size(64.dp),
                selected = true,
                tint = palette.accent.copy(alpha = 0.14f),
                role = V2GlassRole.Accent,
                onClick = {}
            )
        }
        Text(
            text = stringResource(R.string.debug_v2_liquid_navigation_split_dock_body),
            color = palette.secondary,
            fontSize = AppTypographyTokens.Caption.fontSize,
            lineHeight = AppTypographyTokens.Caption.lineHeight,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun V2SplitDockMediaCapsule(
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val palette = rememberV2LiquidGlassPalette()
    V2GlassSurface(
        backdrop = backdrop,
        modifier = modifier
            .width(112.dp)
            .height(64.dp),
        spec = V2GlassSurfaceSpec(
            shape = ContinuousCapsule,
            materialStyle = V2LiquidMaterialStyle.Dock,
            parameters = V2LiquidParameterSet.dockProminent,
            tint = Color(0xFFFF5C8A).copy(alpha = 0.16f),
            surfaceColor = palette.clearTint,
            rimLightAlpha = 0.40f,
            edgeChromaticAlpha = 0.16f,
            causticAlpha = 0.11f,
            readabilityProfile = V2LiquidReadabilityProfile.BrightClear,
            interactive = true
        ),
        contentPadding = PaddingValues(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = appLucideMusicIcon(),
                contentDescription = null,
                tint = palette.content,
                modifier = Modifier.size(21.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = stringResource(R.string.debug_v2_liquid_mini_title),
                    color = palette.content,
                    fontSize = AppTypographyTokens.Eyebrow.fontSize,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.debug_v2_liquid_mini_time),
                    color = palette.secondary,
                    fontSize = AppTypographyTokens.Eyebrow.fontSize,
                    maxLines = 1
                )
            }
            Icon(
                imageVector = appLucidePlayIcon(),
                contentDescription = null,
                tint = palette.success,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
internal fun V2DockStressRow(
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val palette = rememberV2LiquidGlassPalette()
    var selectedIndex by remember { mutableIntStateOf(1) }
    val items = rememberRound5DockItems(includeBadge = false).take(3)
    V2GlassGroup(
        backdrop = backdrop,
        modifier = modifier.fillMaxWidth(),
        role = V2GlassRole.Accent,
        density = V2GlassContentDensity.Compact,
        spacing = 9.dp,
        contentPadding = PaddingValues(10.dp),
        groupBackdropPolicy = V2GlassGroupBackdropPolicy.CombinedOverlay,
        spec = V2GlassSurfaceSpec(
            shape = RoundedCornerShape(28.dp),
            materialStyle = V2LiquidMaterialStyle.Widget,
            parameters = V2LiquidParameterSet.controlRegular,
            surfaceColor = palette.clearTint,
            rimLightAlpha = 0.34f,
            edgeChromaticAlpha = 0.14f,
            causticAlpha = 0.09f,
            readabilityProfile = V2LiquidReadabilityProfile.RegularText
        )
    ) {
        V2GlassListRow(
            title = stringResource(R.string.debug_v2_liquid_dock_stress_title),
            subtitle = stringResource(R.string.debug_v2_liquid_dock_stress_body),
            meta = stringResource(R.string.debug_v2_liquid_group_stress_meta),
            icon = appLucideBranchIcon(),
            tint = palette.accent.copy(alpha = 0.14f),
            role = V2GlassRole.Accent
        )
        V2GlassBottomTabs(
            items = items,
            selectedIndex = selectedIndex,
            onSelectedIndexChange = { selectedIndex = it },
            backdrop = childBackdrop,
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp),
            compact = true,
            labelPolicy = V2GlassTabLabelPolicy.Selected,
            activeTint = palette.success.copy(alpha = 0.14f),
            spec = V2LiquidDockSpec(
                height = 66.dp,
                itemMinWidth = 54.dp,
                outerPadding = 6.dp,
                indicatorInset = 6.dp,
                selectedBlobStyle = V2LiquidMaterialStyle.Dock,
                labelPolicy = V2GlassTabLabelPolicy.Selected,
                materialSpec = V2LiquidDockMaterialSpec(readabilityFillAlpha = 0.08f),
                blobSpec = V2LiquidDockBlobSpec(minWidthFraction = 0.96f)
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            V2GlassButton(
                text = stringResource(R.string.debug_v2_liquid_command_pull),
                icon = appLucideDownloadIcon(),
                backdrop = childBackdrop,
                role = V2GlassRole.Accent,
                size = V2GlassControlSize.Compact,
                modifier = Modifier.weight(1f),
                onClick = {}
            )
            V2GlassButton(
                text = stringResource(R.string.debug_v2_liquid_command_run),
                icon = appLucidePackageIcon(),
                backdrop = childBackdrop,
                role = V2GlassRole.Success,
                selected = true,
                size = V2GlassControlSize.Compact,
                modifier = Modifier.weight(1f),
                onClick = {}
            )
            V2GlassIconButton(
                icon = appLucideSearchIcon(),
                contentDescription = stringResource(R.string.debug_v2_liquid_inputs_search_placeholder),
                backdrop = childBackdrop,
                size = V2GlassControlSize.Compact,
                tint = if (palette.warning.isSpecified) palette.warning.copy(alpha = 0.16f) else Color.Unspecified,
                role = V2GlassRole.Warning,
                onClick = {}
            )
        }
    }
}

@Composable
private fun rememberRound5DockItems(includeBadge: Boolean): List<V2GlassTabItem> {
    val home = stringResource(R.string.debug_v2_liquid_nav_home)
    val media = stringResource(R.string.debug_v2_liquid_nav_media)
    val tools = stringResource(R.string.debug_v2_liquid_nav_tools)
    val status = stringResource(R.string.debug_v2_liquid_nav_status)
    val homeIcon = appLucideHomeIcon()
    val mediaIcon = appLucideMusicIcon()
    val toolsIcon = appLucideConfigIcon()
    val statusIcon = appLucideBranchIcon()
    return remember(
        home,
        media,
        tools,
        status,
        homeIcon,
        mediaIcon,
        toolsIcon,
        statusIcon,
        includeBadge
    ) {
        listOf(
            V2GlassTabItem(home, homeIcon),
            V2GlassTabItem(media, mediaIcon, badge = if (includeBadge) "3" else null),
            V2GlassTabItem(tools, toolsIcon),
            V2GlassTabItem(status, statusIcon)
        )
    }
}
