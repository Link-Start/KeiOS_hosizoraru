package os.kei.ui.page.main.debug.liquidv2

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.capsule.ContinuousCapsule
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text

@Immutable
internal data class V2GlassTabItem(
    val label: String,
    val icon: ImageVector? = null,
    val enabled: Boolean = true,
    val badge: String? = null,
    val contentDescription: String? = null,
    val selectedTint: Color = Color.Unspecified
)

@Composable
internal fun V2GlassBottomTabs(
    items: List<V2GlassTabItem>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    showIcons: Boolean = true,
    compact: Boolean = false,
    labelPolicy: V2GlassTabLabelPolicy = V2GlassTabLabelPolicy.Always,
    selectionStyle: V2GlassSelectionStyle = V2GlassSelectionStyle.Indicator,
    activeTint: Color = Color.Unspecified,
    spec: V2LiquidDockSpec = V2LiquidDockSpec()
) {
    V2LiquidDock(
        items = items,
        selectedIndex = selectedIndex,
        onSelectedIndexChange = onSelectedIndexChange,
        backdrop = backdrop,
        modifier = modifier,
        showIcons = showIcons,
        compact = compact,
        labelPolicy = labelPolicy,
        selectionStyle = selectionStyle,
        activeTint = activeTint,
        spec = spec
    )
}

@Composable
private fun RowScope.V2GlassBottomTabHitBox(
    item: V2GlassTabItem,
    selected: Boolean,
    showIcon: Boolean,
    compact: Boolean,
    labelPolicy: V2GlassTabLabelPolicy,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = rememberV2LiquidGlassPalette()
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = item.enabled,
                role = Role.Tab,
                onClick = onClick
            )
            .alpha(if (item.enabled) 1f else 0.42f),
        contentAlignment = Alignment.Center
    ) {
        V2BottomTabContent(
            item = item,
            selected = selected,
            color = if (selected) palette.content else palette.secondary,
            showIcon = showIcon,
            compact = compact,
            labelPolicy = labelPolicy
        )
    }
}

@Composable
private fun V2BottomTabContent(
    item: V2GlassTabItem,
    selected: Boolean,
    color: Color,
    showIcon: Boolean,
    compact: Boolean,
    labelPolicy: V2GlassTabLabelPolicy,
    modifier: Modifier = Modifier
) {
    val palette = rememberV2LiquidGlassPalette()
    val showLabel = when (labelPolicy) {
        V2GlassTabLabelPolicy.Always -> true
        V2GlassTabLabelPolicy.Selected -> selected
        V2GlassTabLabelPolicy.Never -> false
    }
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            if (compact) 0.dp else 2.dp,
            Alignment.CenterVertically
        )
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            if (showIcon && item.icon != null) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.contentDescription,
                    tint = color,
                    modifier = Modifier.size(if (compact) 15.dp else 17.dp)
                )
            }
            if (!item.badge.isNullOrBlank()) {
                Text(
                    text = item.badge,
                    color = Color.White,
                    fontSize = AppTypographyTokens.Eyebrow.fontSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier
                        .background(palette.danger, ContinuousCapsule)
                        .padding(horizontal = 4.dp)
                )
            }
        }
        if (showLabel) {
            Text(
                text = item.label,
                color = color,
                fontSize = if (compact) AppTypographyTokens.Supporting.fontSize else AppTypographyTokens.Caption.fontSize,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun V2GlassActionBar(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    density: V2GlassContentDensity = V2GlassContentDensity.Compact,
    content: @Composable RowScope.() -> Unit
) {
    val palette = rememberV2LiquidGlassPalette()
    V2GlassSurface(
        backdrop = backdrop,
        modifier = modifier.height(58.dp),
        spec = V2GlassSurfaceSpec.capsule(
            surfaceColor = palette.clearTint,
            interactive = false,
            density = density
        ),
        contentPadding = PaddingValues(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}
