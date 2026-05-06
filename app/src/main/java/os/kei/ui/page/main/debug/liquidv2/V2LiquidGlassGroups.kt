package os.kei.ui.page.main.debug.liquidv2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.capsule.ContinuousCapsule
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text

internal enum class V2GlassGroupOrientation {
    Row,
    Column
}

@Stable
internal class V2GlassGroupScope internal constructor(
    val groupBackdrop: Backdrop,
    val childBackdrop: LayerBackdrop,
    val density: V2GlassContentDensity,
    val role: V2GlassRole,
    val defaultSize: V2GlassControlSize
)

@Stable
internal data class V2GlassActionItem(
    val label: String,
    val icon: ImageVector? = null,
    val role: V2GlassRole = V2GlassRole.Neutral,
    val enabled: Boolean = true,
    val selected: Boolean = false,
    val loading: Boolean = false,
    val onClick: () -> Unit
)

@Composable
internal fun V2GlassGroup(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    orientation: V2GlassGroupOrientation = V2GlassGroupOrientation.Column,
    role: V2GlassRole = V2GlassRole.Neutral,
    density: V2GlassContentDensity = V2GlassContentDensity.Comfortable,
    defaultSize: V2GlassControlSize = V2GlassControlSize.Regular,
    spacing: Dp = 10.dp,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    spec: V2GlassSurfaceSpec? = null,
    content: @Composable V2GlassGroupScope.() -> Unit
) {
    val palette = rememberV2LiquidGlassPalette()
    val childBackdrop = rememberLayerBackdrop()
    val scope = remember(backdrop, childBackdrop, density, role, defaultSize) {
        V2GlassGroupScope(
            groupBackdrop = backdrop,
            childBackdrop = childBackdrop,
            density = density,
            role = role,
            defaultSize = defaultSize
        )
    }
    V2GlassSurface(
        backdrop = backdrop,
        modifier = modifier,
        spec = spec ?: V2GlassSurfaceSpec(
            shape = RoundedCornerShape(V2LiquidGlassTokens.radiusCard),
            role = role,
            surfaceColor = palette.clearTint,
            blur = V2LiquidGlassTokens.blurBalanced,
            lensHeight = V2LiquidGlassTokens.lensBalanced,
            lensAmount = V2LiquidGlassTokens.lensStrong,
            backdropPolicy = V2GlassBackdropPolicy.ExportChild
        ),
        exportedBackdrop = childBackdrop,
        contentPadding = contentPadding
    ) {
        when (orientation) {
            V2GlassGroupOrientation.Row -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                scope.content()
            }

            V2GlassGroupOrientation.Column -> Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing)
            ) {
                scope.content()
            }
        }
    }
}

@Composable
internal fun V2GlassGroupScope.V2GlassListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    meta: String? = null,
    icon: ImageVector? = null,
    tint: Color = Color.Unspecified,
    enabled: Boolean = true,
    role: V2GlassRole = this.role,
    action: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val palette = rememberV2LiquidGlassPalette()
    V2GlassSurface(
        backdrop = childBackdrop,
        modifier = modifier.fillMaxWidth(),
        spec = V2GlassSurfaceSpec(
            shape = RoundedCornerShape(24.dp),
            tint = if (tint.isSpecified) tint else palette.roleTint(role, 0.13f),
            surfaceColor = palette.clearTint,
            blur = V2LiquidGlassTokens.blurSoft,
            lensHeight = 14.dp,
            lensAmount = 20.dp,
            interactive = onClick != null,
            disabled = !enabled,
            role = role
        ),
        contentPadding = PaddingValues(12.dp),
        contentAlignment = Alignment.CenterStart,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                V2GlassSurface(
                    backdrop = childBackdrop,
                    modifier = Modifier.size(40.dp),
                    spec = V2GlassSurfaceSpec(
                        shape = ContinuousCapsule,
                        tint = if (tint.isSpecified) tint else palette.roleTint(role, 0.16f),
                        surfaceColor = palette.clearTint,
                        blur = V2LiquidGlassTokens.blurSoft,
                        lensHeight = 10.dp,
                        lensAmount = 14.dp
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = palette.content,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
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
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = palette.secondary,
                        fontSize = AppTypographyTokens.Caption.fontSize,
                        lineHeight = AppTypographyTokens.Caption.lineHeight,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (meta != null) {
                    Text(
                        text = meta,
                        color = palette.secondary.copy(alpha = 0.82f),
                        fontSize = AppTypographyTokens.Eyebrow.fontSize,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (action != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = action
                )
            }
        }
    }
}

@Composable
internal fun V2GlassToolbar(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    role: V2GlassRole = V2GlassRole.Neutral,
    leading: @Composable RowScope.(V2GlassGroupScope) -> Unit = {},
    content: @Composable RowScope.(V2GlassGroupScope) -> Unit,
    trailing: @Composable RowScope.(V2GlassGroupScope) -> Unit = {}
) {
    val palette = rememberV2LiquidGlassPalette()
    val childBackdrop = rememberLayerBackdrop()
    val scope = remember(backdrop, childBackdrop, role) {
        V2GlassGroupScope(
            groupBackdrop = backdrop,
            childBackdrop = childBackdrop,
            density = V2GlassContentDensity.Compact,
            role = role,
            defaultSize = V2GlassControlSize.Compact
        )
    }
    V2GlassSurface(
        backdrop = backdrop,
        modifier = modifier.height(58.dp),
        spec = V2GlassSurfaceSpec.capsule(
            surfaceColor = palette.clearTint,
            interactive = false,
            role = role
        ),
        exportedBackdrop = childBackdrop,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leading(scope)
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                content(scope)
            }
            trailing(scope)
        }
    }
}

@Composable
internal fun V2GlassDockGroup(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    role: V2GlassRole = V2GlassRole.Neutral,
    content: @Composable RowScope.(V2GlassGroupScope) -> Unit
) {
    val palette = rememberV2LiquidGlassPalette()
    val childBackdrop = rememberLayerBackdrop()
    val scope = remember(backdrop, childBackdrop, role) {
        V2GlassGroupScope(
            groupBackdrop = backdrop,
            childBackdrop = childBackdrop,
            density = V2GlassContentDensity.Compact,
            role = role,
            defaultSize = V2GlassControlSize.Compact
        )
    }
    V2GlassSurface(
        backdrop = backdrop,
        modifier = modifier,
        spec = V2GlassSurfaceSpec.capsule(
            surfaceColor = palette.clearTint,
            interactive = true,
            role = role
        ),
        exportedBackdrop = childBackdrop,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            content(scope)
        }
    }
}

@Composable
internal fun V2GlassActionGroup(
    actions: List<V2GlassActionItem>,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    density: V2GlassContentDensity = V2GlassContentDensity.Compact
) {
    val palette = rememberV2LiquidGlassPalette()
    val childBackdrop = rememberLayerBackdrop()
    V2GlassSurface(
        backdrop = backdrop,
        modifier = modifier,
        spec = V2GlassSurfaceSpec(
            shape = RoundedCornerShape(V2LiquidGlassTokens.radiusCard),
            surfaceColor = palette.clearTint,
            blur = V2LiquidGlassTokens.blurBalanced,
            lensHeight = V2LiquidGlassTokens.lensBalanced,
            lensAmount = V2LiquidGlassTokens.lensStrong
        ),
        exportedBackdrop = childBackdrop,
        contentPadding = PaddingValues(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            actions.forEach { item ->
                V2GlassButton(
                    text = item.label,
                    icon = item.icon,
                    backdrop = childBackdrop,
                    role = item.role,
                    selected = item.selected,
                    loading = item.loading,
                    enabled = item.enabled,
                    size = V2GlassControlSize.Compact,
                    density = density,
                    modifier = Modifier.weight(1f),
                    onClick = item.onClick
                )
            }
        }
    }
}
