package os.kei.ui.page.main.debug.liquidv2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastRoundToInt
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import os.kei.R
import os.kei.ui.page.main.os.appLucideCloseIcon
import os.kei.ui.page.main.os.appLucideMusicIcon
import os.kei.ui.page.main.os.appLucidePlayIcon
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun V2SampleColumn(
    modifier: Modifier,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = rememberLazyListState(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content
    )
}

@Composable
internal fun V2SampleSection(
    title: String,
    subtitle: String,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = rememberV2LiquidGlassPalette()
    V2GlassSurface(
        backdrop = backdrop,
        modifier = modifier.fillMaxWidth(),
        spec = V2GlassSurfaceSpec(
            shape = RoundedCornerShape(V2LiquidGlassTokens.radiusPanel),
            surfaceColor = palette.panelTint,
            blur = V2LiquidGlassTokens.blurBalanced,
            lensHeight = V2LiquidGlassTokens.lensBalanced,
            lensAmount = V2LiquidGlassTokens.lensStrong
        ),
        contentPadding = PaddingValues(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    color = palette.content,
                    fontSize = AppTypographyTokens.SectionTitle.fontSize,
                    lineHeight = AppTypographyTokens.SectionTitle.lineHeight,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = palette.secondary,
                    fontSize = AppTypographyTokens.Body.fontSize,
                    lineHeight = AppTypographyTokens.Body.lineHeight,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            content()
        }
    }
}

@Composable
internal fun V2SampleBadgeRow(backdrop: Backdrop) {
    val palette = rememberV2LiquidGlassPalette()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        V2GlassStatusCapsule(
            label = stringResource(R.string.debug_v2_liquid_badge_backdrop_v2),
            backdrop = backdrop,
            tint = palette.accent.copy(alpha = 0.16f)
        )
        V2GlassStatusCapsule(
            label = stringResource(R.string.debug_v2_liquid_badge_performance),
            backdrop = backdrop,
            tint = palette.success.copy(alpha = 0.14f)
        )
    }
}

@Composable
internal fun V2BackdropStage(parentBackdrop: Backdrop) {
    val stageBackdrop = rememberLayerBackdrop()
    val palette = rememberV2LiquidGlassPalette()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Transparent, RoundedCornerShape(28.dp))
                .layerBackdrop(stageBackdrop)
        ) {
            V2StagePattern(
                accent = palette.accent,
                modifier = Modifier.matchParentSize()
            )
        }
        V2GlassSurface(
            backdrop = stageBackdrop,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .fillMaxWidth(0.70f)
                .height(118.dp),
            spec = V2GlassSurfaceSpec(
                shape = RoundedCornerShape(28.dp),
                surfaceColor = palette.clearTint,
                blur = V2LiquidGlassTokens.blurBalanced,
                lensHeight = V2LiquidGlassTokens.lensBalanced,
                lensAmount = V2LiquidGlassTokens.lensStrong,
                interactive = true
            ),
            contentPadding = PaddingValues(16.dp),
            onClick = {}
        ) {
            V2StageText(
                title = stringResource(R.string.debug_v2_liquid_surface_stage_title),
                body = stringResource(R.string.debug_v2_liquid_surface_stage_body)
            )
        }
        V2GlassButton(
            text = stringResource(R.string.debug_v2_liquid_surface_clear),
            backdrop = stageBackdrop,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 18.dp, bottom = 18.dp)
                .width(156.dp),
            onClick = {}
        )
        V2GlassButton(
            text = stringResource(R.string.debug_v2_liquid_surface_tinted),
            backdrop = stageBackdrop,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 18.dp)
                .width(158.dp),
            tint = Color(0xFF4FD8C8).copy(alpha = 0.18f),
            onClick = {}
        )
        V2GlassStatusCapsule(
            label = stringResource(R.string.debug_v2_liquid_chip_live),
            backdrop = parentBackdrop,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(18.dp),
            tint = palette.warning.copy(alpha = 0.14f)
        )
    }
}

@Composable
internal fun V2NestedExportedBackdropSample(backdrop: Backdrop) {
    val palette = rememberV2LiquidGlassPalette()
    val childBackdrop = rememberLayerBackdrop()
    V2GlassSurface(
        backdrop = backdrop,
        modifier = Modifier
            .fillMaxWidth()
            .height(178.dp),
        spec = V2GlassSurfaceSpec(
            shape = RoundedCornerShape(32.dp),
            surfaceColor = palette.clearTint,
            blur = V2LiquidGlassTokens.blurBalanced,
            lensHeight = V2LiquidGlassTokens.lensBalanced,
            lensAmount = V2LiquidGlassTokens.lensStrong,
            interactive = true
        ),
        exportedBackdrop = childBackdrop,
        contentPadding = PaddingValues(16.dp),
        onClick = {}
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            V2StageText(
                title = stringResource(R.string.debug_v2_liquid_surface_exported),
                body = stringResource(R.string.debug_v2_liquid_surface_nested_body)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                V2GlassStatusCapsule(
                    label = stringResource(R.string.debug_v2_liquid_surface_status_fast),
                    backdrop = childBackdrop,
                    modifier = Modifier.weight(1f),
                    tint = palette.success.copy(alpha = 0.14f)
                )
                V2GlassStatusCapsule(
                    label = stringResource(R.string.debug_v2_liquid_surface_status_stable),
                    backdrop = childBackdrop,
                    modifier = Modifier.weight(1f),
                    tint = palette.accent.copy(alpha = 0.14f)
                )
            }
            V2GlassStatusCapsule(
                label = stringResource(R.string.debug_v2_liquid_surface_status_safe),
                backdrop = childBackdrop,
                tint = palette.warning.copy(alpha = 0.13f)
            )
        }
    }
}

@Composable
internal fun BoxScope.V2InputSheet(
    visible: Boolean,
    backdrop: Backdrop,
    onDismiss: () -> Unit
) {
    V2GlassSheet(
        visible = visible,
        backdrop = backdrop
    ) { exported ->
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            V2StageText(
                title = stringResource(R.string.debug_v2_liquid_inputs_sheet_title),
                body = stringResource(R.string.debug_v2_liquid_inputs_sheet_body)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                V2GlassButton(
                    text = stringResource(R.string.debug_v2_liquid_chip_profiled),
                    backdrop = exported,
                    modifier = Modifier.weight(1f),
                    onClick = {}
                )
                V2GlassIconButton(
                    icon = appLucideCloseIcon(),
                    contentDescription = stringResource(R.string.common_close),
                    backdrop = exported,
                    onClick = onDismiss
                )
            }
        }
    }
}

@Composable
internal fun V2MiniPlayerDock(backdrop: Backdrop) {
    val palette = rememberV2LiquidGlassPalette()
    V2GlassSurface(
        backdrop = backdrop,
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp),
        spec = V2GlassSurfaceSpec.capsule(
            surfaceColor = palette.clearTint,
            interactive = true
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
        onClick = {}
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            V2GlassSurface(
                backdrop = backdrop,
                modifier = Modifier.size(48.dp),
                spec = V2GlassSurfaceSpec(
                    shape = RoundedCornerShape(16.dp),
                    tint = Color(0xFFFF5C8A).copy(alpha = 0.22f),
                    surfaceColor = palette.clearTint,
                    blur = V2LiquidGlassTokens.blurSoft,
                    lensHeight = 12.dp,
                    lensAmount = 16.dp
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = appLucideMusicIcon(),
                    contentDescription = null,
                    tint = palette.content,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(R.string.debug_v2_liquid_mini_title),
                    color = palette.content,
                    fontSize = AppTypographyTokens.CardHeader.fontSize,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.debug_v2_liquid_mini_subtitle),
                    color = palette.secondary,
                    fontSize = AppTypographyTokens.Caption.fontSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            V2GlassIconButton(
                icon = appLucidePlayIcon(),
                contentDescription = stringResource(R.string.debug_v2_liquid_action_play),
                backdrop = backdrop,
                tint = palette.success.copy(alpha = 0.16f),
                onClick = {}
            )
        }
    }
}

@Composable
internal fun V2ScenarioActionRow(
    icon: ImageVector,
    title: String,
    body: String,
    meta: String,
    action: String,
    tint: Color,
    backdrop: Backdrop
) {
    val palette = rememberV2LiquidGlassPalette()
    V2GlassSurface(
        backdrop = backdrop,
        modifier = Modifier.fillMaxWidth(),
        spec = V2GlassSurfaceSpec(
            shape = RoundedCornerShape(26.dp),
            tint = tint,
            surfaceColor = palette.clearTint,
            blur = V2LiquidGlassTokens.blurBalanced,
            lensHeight = 18.dp,
            lensAmount = 24.dp,
            interactive = true
        ),
        contentPadding = PaddingValues(14.dp),
        onClick = {}
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = palette.content,
                modifier = Modifier.size(22.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = title,
                    color = palette.content,
                    fontSize = AppTypographyTokens.CardHeader.fontSize,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = body,
                    color = palette.secondary,
                    fontSize = AppTypographyTokens.Caption.fontSize,
                    lineHeight = AppTypographyTokens.Caption.lineHeight,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = meta,
                    color = palette.secondary.copy(alpha = 0.82f),
                    fontSize = AppTypographyTokens.Eyebrow.fontSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            V2GlassButton(
                text = action,
                backdrop = backdrop,
                tint = tint,
                onClick = {}
            )
        }
    }
}

@Composable
internal fun V2SandboxPreview(
    corner: Float,
    blur: Float,
    lens: Float,
    tint: Float,
    depth: Boolean,
    backdrop: Backdrop
) {
    val palette = rememberV2LiquidGlassPalette()
    val cornerDp = (16f + corner * 30f).dp
    val blurDp = (4f + blur * 8f).dp
    val lensDp = (10f + lens * 28f).dp
    V2GlassSurface(
        backdrop = backdrop,
        modifier = Modifier
            .fillMaxWidth()
            .height(154.dp),
        spec = V2GlassSurfaceSpec(
            shape = RoundedCornerShape(cornerDp),
            tint = palette.accent.copy(alpha = tint * 0.34f),
            surfaceColor = palette.clearTint,
            blur = blurDp,
            lensHeight = lensDp,
            lensAmount = lensDp,
            depthEffect = depth,
            interactive = true
        ),
        contentPadding = PaddingValues(18.dp),
        onClick = {}
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            V2StageText(
                title = stringResource(R.string.debug_v2_liquid_sandbox_preview_title),
                body = stringResource(R.string.debug_v2_liquid_sandbox_preview_body)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                V2GlassStatusCapsule(
                    label = stringResource(R.string.debug_v2_liquid_chip_live),
                    backdrop = backdrop,
                    tint = palette.success.copy(alpha = 0.14f)
                )
                V2GlassStatusCapsule(
                    label = stringResource(R.string.debug_v2_liquid_chip_profiled),
                    backdrop = backdrop,
                    tint = palette.accent.copy(alpha = 0.13f)
                )
            }
        }
    }
}

@Composable
internal fun V2SliderLine(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    backdrop: Backdrop
) {
    val palette = rememberV2LiquidGlassPalette()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            V2SampleLabel(label)
            Text(
                text = stringResource(
                    R.string.debug_v2_liquid_percent_value,
                    (value * 100f).fastRoundToInt()
                ),
                color = palette.secondary,
                fontSize = AppTypographyTokens.Supporting.fontSize,
                maxLines = 1
            )
        }
        V2GlassSlider(
            value = value,
            onValueChange = onValueChange,
            backdrop = backdrop
        )
    }
}

@Composable
internal fun V2SampleLabel(text: String) {
    val palette = rememberV2LiquidGlassPalette()
    Text(
        text = text,
        color = palette.content,
        fontSize = AppTypographyTokens.BodyEmphasis.fontSize,
        lineHeight = AppTypographyTokens.BodyEmphasis.lineHeight,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
internal fun V2StageText(
    title: String,
    body: String
) {
    val palette = rememberV2LiquidGlassPalette()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            color = palette.content,
            fontSize = AppTypographyTokens.CardHeader.fontSize,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = body,
            color = palette.secondary,
            fontSize = AppTypographyTokens.Caption.fontSize,
            lineHeight = AppTypographyTokens.Caption.lineHeight,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun V2StagePattern(
    accent: Color,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    Canvas(modifier = modifier) {
        val soft = if (isDark) 0.34f else 0.22f
        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(
                    accent.copy(alpha = soft),
                    Color(0xFF4FD8C8).copy(alpha = soft * 0.82f)
                )
            ),
            topLeft = Offset(size.width * 0.05f, size.height * 0.10f),
            size = Size(size.width * 0.72f, size.height * 0.42f),
            cornerRadius = CornerRadius(42.dp.toPx())
        )
        drawCircle(
            color = Color(0xFFFF5C8A).copy(alpha = if (isDark) 0.26f else 0.18f),
            radius = size.minDimension * 0.25f,
            center = Offset(size.width * 0.78f, size.height * 0.34f)
        )
        drawRoundRect(
            color = Color(0xFFFFB43D).copy(alpha = if (isDark) 0.24f else 0.16f),
            topLeft = Offset(size.width * 0.18f, size.height * 0.62f),
            size = Size(size.width * 0.70f, size.height * 0.23f),
            cornerRadius = CornerRadius(32.dp.toPx())
        )
    }
}
