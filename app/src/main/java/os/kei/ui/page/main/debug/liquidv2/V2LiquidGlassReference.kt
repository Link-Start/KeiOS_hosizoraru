package os.kei.ui.page.main.debug.liquidv2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import os.kei.R
import os.kei.ui.page.main.os.appLucideBranchIcon
import os.kei.ui.page.main.os.appLucideConfigIcon
import os.kei.ui.page.main.os.appLucideConfirmIcon
import os.kei.ui.page.main.os.appLucideDownloadIcon
import os.kei.ui.page.main.os.appLucideHeartIcon
import os.kei.ui.page.main.os.appLucideHomeIcon
import os.kei.ui.page.main.os.appLucideLayersIcon
import os.kei.ui.page.main.os.appLucideMediaIcon
import os.kei.ui.page.main.os.appLucideMoreIcon
import os.kei.ui.page.main.os.appLucideMusicIcon
import os.kei.ui.page.main.os.appLucidePackageIcon
import os.kei.ui.page.main.os.appLucideSearchIcon
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text

@Immutable
internal data class V2ReferenceAppIcon(
    val color: Color,
    val icon: ImageVector
)

@Composable
internal fun V2LiquidReferenceStage(
    title: String,
    subtitle: String,
    parentBackdrop: Backdrop,
    modifier: Modifier = Modifier,
    stageKind: V2ReferenceStageKind = V2ReferenceStageKind.Media,
    stageHeight: Dp = 356.dp,
    renderTier: V2GlassRenderTier = V2GlassRenderTier.Full,
    backdropContent: @Composable BoxScope.() -> Unit = {
        V2ReferenceBackdropArt(
            kind = stageKind,
            modifier = Modifier.matchParentSize()
        )
    },
    content: @Composable BoxScope.(V2LiquidGlassScope) -> Unit
) {
    val palette = rememberV2LiquidGlassPalette()
    if (renderTier == V2GlassRenderTier.Shell) {
        V2ReferenceStageShell(
            title = title,
            subtitle = subtitle,
            stageKind = stageKind,
            modifier = modifier.height(stageHeight)
        )
        return
    }
    val stageBackdrop = if (renderTier == V2GlassRenderTier.Full) rememberLayerBackdrop() else null
    val exportedBackdrop: Backdrop = stageBackdrop ?: parentBackdrop
    val combinedBackdrop: Backdrop = if (stageBackdrop != null) {
        rememberCombinedBackdrop(parentBackdrop, stageBackdrop)
    } else {
        parentBackdrop
    }
    val scope = remember(parentBackdrop, exportedBackdrop, combinedBackdrop) {
        V2LiquidGlassScope(
            parentBackdrop = parentBackdrop,
            exportedBackdrop = exportedBackdrop,
            combinedChildBackdrop = combinedBackdrop,
            density = V2GlassContentDensity.Comfortable,
            role = V2GlassRole.Accent,
            defaultMaterialStyle = V2LiquidMaterialStyle.Clear
        )
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(stageHeight)
            .background(Color.Transparent, RoundedCornerShape(34.dp))
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(
                    if (stageBackdrop != null) {
                        Modifier.layerBackdrop(stageBackdrop)
                    } else {
                        Modifier
                    }
                )
        ) {
            backdropContent()
        }
        V2GlassSurface(
            backdrop = exportedBackdrop,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp)
                .fillMaxWidth(0.70f),
            spec = V2GlassSurfaceSpec(
                shape = RoundedCornerShape(24.dp),
                materialStyle = V2LiquidMaterialStyle.Clear,
                parameters = V2LiquidParameterSet.sampleClear,
                surfaceColor = palette.clearTint,
                clearDimmingAlpha = 0.10f,
                rimLightAlpha = 0.34f,
                edgeChromaticAlpha = 0.18f,
                causticAlpha = 0.12f,
                readabilityProfile = V2LiquidReadabilityProfile.BrightClear
            ),
            contentPadding = PaddingValues(14.dp)
        ) {
            V2StageText(title = title, body = subtitle)
        }
        content(scope)
    }
}

internal enum class V2ReferenceStageKind {
    Media,
    HomeScreen,
    Controls
}

@Composable
private fun V2ReferenceStageShell(
    title: String,
    subtitle: String,
    stageKind: V2ReferenceStageKind,
    modifier: Modifier = Modifier
) {
    val palette = rememberV2LiquidGlassPalette()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.clearTint, RoundedCornerShape(34.dp))
            .padding(16.dp)
    ) {
        V2ReferenceBackdropArt(
            kind = stageKind,
            modifier = Modifier.matchParentSize()
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth(0.70f)
                .background(palette.panelTint, RoundedCornerShape(24.dp))
                .padding(14.dp)
        ) {
            V2StageText(title = title, body = subtitle)
        }
        Text(
            text = stringResource(R.string.debug_v2_liquid_badge_performance),
            color = palette.content,
            fontSize = AppTypographyTokens.Caption.fontSize,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .background(palette.panelTint, RoundedCornerShape(18.dp))
                .padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

@Composable
internal fun V2KyantHeroSample(
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    var checked by remember { mutableStateOf(true) }
    val palette = rememberV2LiquidGlassPalette()
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            V2LiquidButton(
                text = stringResource(R.string.debug_v2_liquid_clear_button),
                backdrop = backdrop,
                modifier = Modifier.weight(1f)
            )
            V2LiquidButton(
                text = stringResource(R.string.debug_v2_liquid_blue_button),
                backdrop = backdrop,
                modifier = Modifier.weight(1f),
                tint = palette.accent.copy(alpha = 0.26f),
                role = V2GlassRole.Accent
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            V2LiquidButton(
                text = stringResource(R.string.debug_v2_liquid_orange_button),
                backdrop = backdrop,
                modifier = Modifier.weight(1f),
                tint = palette.warning.copy(alpha = 0.30f),
                role = V2GlassRole.Warning
            )
            V2GlassStatusCapsule(
                label = stringResource(R.string.debug_v2_liquid_chip_live),
                backdrop = backdrop,
                tint = palette.success.copy(alpha = 0.18f)
            )
            V2LiquidToggle(
                checked = checked,
                onCheckedChange = { checked = it },
                backdrop = backdrop
            )
        }
    }
}

@Composable
internal fun V2LiquidButton(
    text: String,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    role: V2GlassRole = V2GlassRole.Neutral,
    icon: ImageVector? = null,
    selected: Boolean = false,
    onClick: () -> Unit = {}
) {
    V2GlassButton(
        text = text,
        icon = icon,
        backdrop = backdrop,
        modifier = modifier,
        tint = tint,
        role = role,
        selected = selected,
        density = V2GlassContentDensity.Comfortable,
        onClick = onClick
    )
}

@Composable
internal fun V2LiquidToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    V2GlassSwitch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        backdrop = backdrop,
        modifier = modifier,
        role = V2GlassRole.Success,
        checkedIcon = appLucideConfirmIcon()
    )
}

@Composable
internal fun V2LiquidParameterPanel(
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    var height by remember { mutableFloatStateOf(0.48f) }
    var amount by remember { mutableFloatStateOf(0.70f) }
    var chromatic by remember { mutableFloatStateOf(0.64f) }
    V2GlassSurface(
        backdrop = backdrop,
        modifier = modifier.fillMaxWidth(),
        spec = V2GlassSurfaceSpec(
            shape = RoundedCornerShape(28.dp),
            materialStyle = V2LiquidMaterialStyle.Widget,
            parameters = V2LiquidParameterSet.controlRegular,
            surfaceColor = rememberV2LiquidGlassPalette().clearTint,
            clearDimmingAlpha = 0.08f,
            rimLightAlpha = 0.34f,
            edgeChromaticAlpha = 0.14f,
            readabilityProfile = V2LiquidReadabilityProfile.RegularText
        ),
        contentPadding = PaddingValues(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            V2SliderLine(
                label = stringResource(R.string.debug_v2_liquid_refraction_height),
                value = height,
                onValueChange = { height = it },
                backdrop = backdrop
            )
            V2SliderLine(
                label = stringResource(R.string.debug_v2_liquid_refraction_amount),
                value = amount,
                onValueChange = { amount = it },
                backdrop = backdrop
            )
            V2SliderLine(
                label = stringResource(R.string.debug_v2_liquid_chromatic_aberration),
                value = chromatic,
                onValueChange = { chromatic = it },
                backdrop = backdrop,
                steps = 3
            )
        }
    }
}

@Composable
internal fun V2LiquidTabBarShowcase(
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    var threeIndex by remember { mutableIntStateOf(1) }
    var fourIndex by remember { mutableIntStateOf(0) }
    val palette = rememberV2LiquidGlassPalette()
    val threeItems = rememberLiquidTabItems(short = true)
    val fourItems = rememberLiquidTabItems(short = false)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        V2SampleLabel(stringResource(R.string.debug_v2_liquid_tabbar_three))
        V2GlassBottomTabs(
            items = threeItems,
            selectedIndex = threeIndex,
            onSelectedIndexChange = { threeIndex = it },
            backdrop = backdrop,
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp),
            activeTint = palette.accent.copy(alpha = 0.18f),
            spec = V2LiquidDockSpec(
                height = 62.dp,
                outerPadding = 5.dp,
                indicatorInset = 5.dp,
                selectedBlobStyle = V2LiquidMaterialStyle.ControlThumb
            )
        )
        V2SampleLabel(stringResource(R.string.debug_v2_liquid_tabbar_four))
        V2GlassBottomTabs(
            items = fourItems,
            selectedIndex = fourIndex,
            onSelectedIndexChange = { fourIndex = it },
            backdrop = backdrop,
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp),
            activeTint = palette.success.copy(alpha = 0.16f),
            spec = V2LiquidDockSpec(
                height = 66.dp,
                outerPadding = 5.dp,
                indicatorInset = 5.dp,
                selectedBlobStyle = V2LiquidMaterialStyle.Dock,
                badgeStyle = palette.danger
            )
        )
    }
}

@Composable
internal fun V2LiquidPhoneMock(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    useOwnBackdrop: Boolean = false,
    drawWallpaper: Boolean = true
) {
    val palette = rememberV2LiquidGlassPalette()
    val phoneLayer = if (useOwnBackdrop) rememberLayerBackdrop() else null
    val glassBackdrop = phoneLayer ?: backdrop
    Box(
        modifier = modifier
            .width(214.dp)
            .height(318.dp)
            .background(Color.Transparent, RoundedCornerShape(38.dp))
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(
                    if (phoneLayer != null) {
                        Modifier.layerBackdrop(phoneLayer)
                    } else {
                        Modifier
                    }
                )
                .background(
                    if (drawWallpaper) V2PhoneWallpaperBrush() else Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Transparent)
                    ),
                    RoundedCornerShape(38.dp)
                )
                .padding(18.dp)
        ) {
            if (drawWallpaper) {
                V2PhoneIconGrid()
            }
        }
        V2GlassSurface(
            backdrop = glassBackdrop,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 22.dp)
                .width(106.dp)
                .height(30.dp),
            spec = V2GlassSurfaceSpec.capsule(
                surfaceColor = palette.clearTint,
                interactive = false
            ).copy(
                materialStyle = V2LiquidMaterialStyle.Clear,
                parameters = V2LiquidParameterSet.sampleClear,
                clearDimmingAlpha = 0.08f,
                rimLightAlpha = 0.36f,
                readabilityProfile = V2LiquidReadabilityProfile.BrightClear
            ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.debug_v2_liquid_phone_title),
                color = Color.White,
                fontSize = AppTypographyTokens.Eyebrow.fontSize,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
        V2GlassSurface(
            backdrop = glassBackdrop,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 18.dp)
                .width(146.dp)
                .height(84.dp),
            spec = V2GlassSurfaceSpec(
                shape = RoundedCornerShape(24.dp),
                materialStyle = V2LiquidMaterialStyle.Widget,
                parameters = V2LiquidParameterSet.controlRegular,
                surfaceColor = palette.clearTint,
                clearDimmingAlpha = 0.12f,
                rimLightAlpha = 0.34f,
                edgeChromaticAlpha = 0.16f,
                readabilityProfile = V2LiquidReadabilityProfile.RegularText
            ),
            contentPadding = PaddingValues(12.dp)
        ) {
            V2StageText(
                title = stringResource(R.string.debug_v2_liquid_phone_subtitle),
                body = stringResource(R.string.debug_v2_liquid_phone_body)
            )
        }
        V2PhoneActionStack(
            backdrop = glassBackdrop,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
        )
        V2GlassBottomTabs(
            items = rememberLiquidTabItems(short = true),
            selectedIndex = 1,
            onSelectedIndexChange = {},
            backdrop = glassBackdrop,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp, vertical = 14.dp)
                .fillMaxWidth()
                .height(58.dp),
            labelPolicy = V2GlassTabLabelPolicy.Never,
            compact = true,
            spec = V2LiquidDockSpec(
                height = 58.dp,
                itemMinWidth = 42.dp,
                outerPadding = 4.dp,
                indicatorInset = 5.dp,
                selectedBlobStyle = V2LiquidMaterialStyle.ControlThumb,
                labelPolicy = V2GlassTabLabelPolicy.Never
            )
        )
    }
}

@Composable
internal fun BoxScope.V2PhoneMockBackdropContent(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(214.dp)
            .height(318.dp)
            .background(V2PhoneWallpaperBrush(), RoundedCornerShape(38.dp))
            .padding(18.dp)
    ) {
        V2PhoneIconGrid()
    }
}

private fun V2PhoneWallpaperBrush(): Brush {
    return Brush.verticalGradient(
        listOf(
            Color(0xFF111826),
            Color(0xFF2553A3),
            Color(0xFFFF8A52)
        )
    )
}

@Composable
internal fun V2LiquidActionStack(
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    V2PhoneActionStack(backdrop = backdrop, modifier = modifier)
}

@Composable
private fun V2PhoneActionStack(
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val palette = rememberV2LiquidGlassPalette()
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        listOf(
            appLucideHomeIcon() to palette.accent,
            appLucideDownloadIcon() to palette.success,
            appLucideMoreIcon() to palette.warning
        ).forEach { (icon, tint) ->
            V2GlassIconButton(
                icon = icon,
                contentDescription = "",
                backdrop = backdrop,
                tint = tint.copy(alpha = 0.16f),
                selected = true,
                size = V2GlassControlSize.Compact,
                onClick = {}
            )
        }
    }
}

@Composable
private fun BoxScope.V2PhoneIconGrid() {
    val icons = rememberPhoneIcons()
    Column(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .padding(top = 52.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(4) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                repeat(4) { column ->
                    val item = icons[(row * 4 + column) % icons.size]
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(item.color, RoundedCornerShape(9.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberLiquidTabItems(short: Boolean): List<V2GlassTabItem> {
    val home = stringResource(R.string.debug_v2_liquid_nav_home)
    val media = stringResource(R.string.debug_v2_liquid_nav_media)
    val tools = stringResource(R.string.debug_v2_liquid_nav_tools)
    val status = stringResource(R.string.debug_v2_liquid_nav_status)
    val homeIcon = appLucideHomeIcon()
    val mediaIcon = appLucideMusicIcon()
    val toolsIcon = appLucideConfigIcon()
    val statusIcon = appLucideLayersIcon()
    return remember(home, media, tools, status, homeIcon, mediaIcon, toolsIcon, statusIcon, short) {
        val all = listOf(
            V2GlassTabItem(home, homeIcon),
            V2GlassTabItem(media, mediaIcon, badge = if (short) null else "3"),
            V2GlassTabItem(tools, toolsIcon),
            V2GlassTabItem(status, statusIcon)
        )
        if (short) all.take(3) else all
    }
}

@Composable
private fun rememberPhoneIcons(): List<V2ReferenceAppIcon> {
    val icons = listOf(
        appLucideHomeIcon(),
        appLucideSearchIcon(),
        appLucideMusicIcon(),
        appLucideDownloadIcon(),
        appLucideHeartIcon(),
        appLucidePackageIcon(),
        appLucideMediaIcon(),
        appLucideBranchIcon()
    )
    return remember(icons) {
        icons.mapIndexed { index, icon ->
            val colors = listOf(
                Color(0xFF4DA3FF),
                Color(0xFF27D17F),
                Color(0xFFFF5C8A),
                Color(0xFFFFB43D),
                Color(0xFF7C65FF),
                Color(0xFF39C5BB)
            )
            V2ReferenceAppIcon(colors[index % colors.size], icon)
        }
    }
}

@Composable
internal fun V2ReferenceBackdropArt(
    kind: V2ReferenceStageKind,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        when (kind) {
            V2ReferenceStageKind.Media -> {
                drawRect(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF0E1220),
                            Color(0xFF1D6FA3),
                            Color(0xFFE96076),
                            Color(0xFFFFB45C)
                        )
                    )
                )
                repeat(8) { index ->
                    val x = size.width * (0.12f + index * 0.11f)
                    val y = size.height * (0.28f + (index % 3) * 0.14f)
                    drawCircle(
                        color = Color.White.copy(alpha = 0.10f),
                        radius = size.minDimension * (0.08f + (index % 2) * 0.025f),
                        center = Offset(x, y)
                    )
                }
            }

            V2ReferenceStageKind.HomeScreen -> {
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color(0xFF172033), Color(0xFF4067C9), Color(0xFFFF9867))
                    )
                )
                repeat(18) { index ->
                    val row = index / 6
                    val column = index % 6
                    drawRoundRect(
                        color = listOf(
                            Color(0xFF4DA3FF),
                            Color(0xFF27D17F),
                            Color(0xFFFF5C8A),
                            Color(0xFFFFB43D)
                        )[index % 4].copy(alpha = 0.78f),
                        topLeft = Offset(
                            size.width * (0.08f + column * 0.145f),
                            size.height * (0.12f + row * 0.16f)
                        ),
                        size = Size(size.minDimension * 0.10f, size.minDimension * 0.10f),
                        cornerRadius = CornerRadius(12.dp.toPx())
                    )
                }
            }

            V2ReferenceStageKind.Controls -> {
                drawRect(
                    Brush.linearGradient(
                        listOf(Color(0xFF10161D), Color(0xFF334A5F), Color(0xFF7D8FA6))
                    )
                )
                repeat(6) { index ->
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.10f + index * 0.012f),
                        topLeft = Offset(size.width * 0.08f, size.height * (0.18f + index * 0.11f)),
                        size = Size(size.width * (0.70f - index * 0.045f), 18.dp.toPx()),
                        cornerRadius = CornerRadius(9.dp.toPx())
                    )
                }
            }
        }
    }
}
