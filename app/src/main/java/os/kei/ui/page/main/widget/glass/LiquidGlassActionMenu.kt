package os.kei.ui.page.main.widget.glass

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.motion.appMotionFloatState
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class LiquidGlassActionMenuQuickAction(
    val id: String,
    val icon: ImageVector,
    val label: String,
    val contentDescription: String = label,
    val enabled: Boolean = true,
    val variant: GlassVariant = GlassVariant.SheetAction,
    val onClick: () -> Unit
)

sealed interface LiquidGlassActionMenuItem {
    val id: String
}

data class LiquidGlassActionMenuActionRow(
    override val id: String,
    val text: String,
    val onClick: () -> Unit,
    val leadingIcon: ImageVector? = null,
    val trailingIcon: ImageVector? = null,
    val subtitle: String? = null,
    val enabled: Boolean = true,
    val highlighted: Boolean = false,
    val variant: GlassVariant = GlassVariant.SheetAction
) : LiquidGlassActionMenuItem

data class LiquidGlassActionMenuSubmenuRow(
    override val id: String,
    val text: String,
    val submenuItems: List<LiquidGlassActionMenuSingleChoiceRow>,
    val leadingIcon: ImageVector? = null,
    val trailingIcon: ImageVector? = null,
    val subtitle: String? = null,
    val enabled: Boolean = true,
    val variant: GlassVariant = GlassVariant.SheetAction
) : LiquidGlassActionMenuItem

data class LiquidGlassActionMenuSingleChoiceRow(
    override val id: String,
    val text: String,
    val selected: Boolean,
    val onClick: () -> Unit,
    val leadingIcon: ImageVector? = null,
    val trailingIcon: ImageVector? = null,
    val subtitle: String? = null,
    val enabled: Boolean = true,
    val variant: GlassVariant = GlassVariant.SheetAction
) : LiquidGlassActionMenuItem

@Composable
fun LiquidGlassActionMenu(
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    quickActions: List<LiquidGlassActionMenuQuickAction> = emptyList(),
    items: List<LiquidGlassActionMenuItem>,
    accentColor: Color = MiuixTheme.colorScheme.primary,
    minWidth: Dp = 264.dp,
    maxWidth: Dp = 320.dp,
    maxHeight: Dp = 440.dp,
    submenuMinWidth: Dp = 220.dp,
    submenuMaxWidth: Dp = 300.dp,
    initialExpandedSubmenuId: String? = null,
    onDismissRequest: () -> Unit = {}
) {
    var expandedSubmenuId by remember(items, initialExpandedSubmenuId) {
        mutableStateOf(initialExpandedSubmenuId)
    }
    val expandedSubmenu = items
        .filterIsInstance<LiquidGlassActionMenuSubmenuRow>()
        .firstOrNull { item ->
            item.id == expandedSubmenuId &&
                    item.enabled &&
                    item.submenuItems.isNotEmpty()
        }
    val visibleItems = if (expandedSubmenu != null) {
        items.takeWhile { item -> item.id != expandedSubmenu.id }
    } else {
        items
    }
    AppLiquidGlassDropdownColumn(
        modifier = modifier.animateContentSize(),
        minWidth = minWidth,
        maxWidth = maxWidth,
        maxHeight = maxHeight,
        accentColor = accentColor,
        backdrop = backdrop,
        material = LiquidGlassDropdownMaterial.ActionMenu
    ) {
        if (quickActions.isNotEmpty()) {
            LiquidGlassActionMenuQuickActionsRow(
                quickActions = quickActions,
                accentColor = accentColor,
                onActionClick = { action ->
                    action.onClick()
                    onDismissRequest()
                }
            )
            LiquidGlassActionMenuDivider()
        }
        visibleItems.forEachIndexed { index, item ->
            LiquidGlassActionMenuItemRow(
                item = item,
                index = index,
                optionSize = if (expandedSubmenu != null) visibleItems.size + 1 else visibleItems.size,
                expanded = expandedSubmenuId == item.id,
                accentColor = accentColor,
                onExpandSubmenu = { id ->
                    expandedSubmenuId = if (expandedSubmenuId == id) null else id
                },
                onDismissRequest = onDismissRequest
            )
        }
        if (expandedSubmenu != null) {
            LiquidGlassActionMenuSubmenuPanel(
                item = expandedSubmenu,
                accentColor = accentColor,
                backdrop = backdrop,
                minWidth = submenuMinWidth,
                maxWidth = submenuMaxWidth,
                onCollapse = { expandedSubmenuId = null },
                onDismissRequest = {
                    expandedSubmenuId = null
                    onDismissRequest()
                }
            )
        }
    }
}

@Composable
private fun LiquidGlassActionMenuItemRow(
    item: LiquidGlassActionMenuItem,
    index: Int,
    optionSize: Int,
    expanded: Boolean,
    accentColor: Color,
    onExpandSubmenu: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    when (item) {
        is LiquidGlassActionMenuActionRow -> {
            LiquidGlassDropdownActionItem(
                text = item.text,
                onClick = {
                    item.onClick()
                    onDismissRequest()
                },
                index = index,
                optionSize = optionSize,
                leadingIcon = item.leadingIcon,
                trailingIcon = item.trailingIcon,
                subtitle = item.subtitle,
                accentColor = accentColor,
                variant = item.variant,
                enabled = item.enabled,
                highlighted = item.highlighted
            )
        }

        is LiquidGlassActionMenuSingleChoiceRow -> {
            LiquidGlassDropdownSingleChoiceItem(
                text = item.text,
                optionSize = optionSize,
                isSelected = item.selected,
                index = index,
                onSelectedIndexChange = {
                    item.onClick()
                    onDismissRequest()
                },
                leadingIcon = item.leadingIcon,
                trailingIcon = item.trailingIcon,
                subtitle = item.subtitle,
                accentColor = accentColor,
                variant = item.variant,
                enabled = item.enabled
            )
        }

        is LiquidGlassActionMenuSubmenuRow -> {
            LiquidGlassDropdownActionItem(
                text = item.text,
                onClick = { onExpandSubmenu(item.id) },
                index = index,
                optionSize = optionSize,
                leadingIcon = item.leadingIcon,
                trailingIcon = item.trailingIcon,
                subtitle = item.subtitle
                    ?: item.submenuItems.firstOrNull { choice -> choice.selected }?.text,
                accentColor = accentColor,
                variant = item.variant,
                enabled = item.enabled && item.submenuItems.isNotEmpty(),
                highlighted = expanded
            )
        }
    }
}

@Composable
private fun LiquidGlassActionMenuSubmenuPanel(
    item: LiquidGlassActionMenuSubmenuRow,
    accentColor: Color,
    backdrop: Backdrop?,
    minWidth: Dp,
    maxWidth: Dp,
    onCollapse: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val selectedIndex = item.submenuItems.indexOfFirst { choice -> choice.selected }
    AppLiquidGlassDropdownColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 0.dp),
        minWidth = minWidth,
        maxWidth = maxWidth,
        initialScrollItemIndex = selectedIndex.takeIf { it >= 0 },
        accentColor = accentColor,
        backdrop = backdrop,
        material = LiquidGlassDropdownMaterial.ActionMenu
    ) {
        LiquidGlassDropdownActionItem(
            text = item.text,
            onClick = onCollapse,
            index = 0,
            optionSize = item.submenuItems.size + 1,
            leadingIcon = item.leadingIcon,
            subtitle = item.subtitle
                ?: item.submenuItems.firstOrNull { choice -> choice.selected }?.text,
            trailingContent = {
                item.trailingIcon?.let { icon ->
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onBackgroundVariant.copy(
                            alpha = if (isSystemInDarkTheme()) 0.82f else 0.70f
                        ),
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer { rotationZ = 90f }
                    )
                }
            },
            accentColor = accentColor,
            variant = item.variant,
            enabled = item.enabled
        )
        LiquidGlassActionMenuDivider()
        item.submenuItems.forEachIndexed { choiceIndex, choice ->
            LiquidGlassDropdownSingleChoiceItem(
                text = choice.text,
                optionSize = item.submenuItems.size,
                isSelected = choice.selected,
                index = choiceIndex,
                onSelectedIndexChange = {
                    choice.onClick()
                    onDismissRequest()
                },
                trailingIcon = choice.trailingIcon,
                subtitle = choice.subtitle,
                accentColor = accentColor,
                variant = choice.variant,
                enabled = choice.enabled
            )
        }
    }
}

@Composable
private fun LiquidGlassActionMenuQuickActionsRow(
    quickActions: List<LiquidGlassActionMenuQuickAction>,
    accentColor: Color,
    onActionClick: (LiquidGlassActionMenuQuickAction) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        quickActions.forEach { action ->
            LiquidGlassActionMenuQuickActionButton(
                action = action,
                accentColor = accentColor,
                onActionClick = onActionClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun LiquidGlassActionMenuQuickActionButton(
    action: LiquidGlassActionMenuQuickAction,
    accentColor: Color,
    onActionClick: (LiquidGlassActionMenuQuickAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by appMotionFloatState(
        targetValue = if (pressed && action.enabled) AppInteractiveTokens.pressedScale else 1f,
        durationMillis = 110,
        label = "liquid_glass_action_menu_quick_action_scale"
    )
    val contentColor = actionMenuContentColor(
        isDark = isDark,
        accentColor = accentColor,
        variant = action.variant,
        enabled = action.enabled,
        primary = true
    )
    val surfaceColor = when {
        pressed -> MiuixTheme.colorScheme.onBackground.copy(alpha = if (isDark) 0.14f else 0.08f)
        action.variant == GlassVariant.SheetPrimaryAction -> accentColor.copy(alpha = if (isDark) 0.18f else 0.12f)
        else -> Color.Transparent
    }
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = 72.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(surfaceColor, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = action.enabled,
                role = Role.Button,
                onClick = { onActionClick(action) }
            )
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = action.contentDescription,
            tint = contentColor,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = action.label,
            color = contentColor,
            fontSize = AppTypographyTokens.Caption.fontSize,
            lineHeight = AppTypographyTokens.Caption.lineHeight,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LiquidGlassActionMenuDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .height(1.dp)
            .background(
                color = MiuixTheme.colorScheme.onBackground.copy(
                    alpha = if (isSystemInDarkTheme()) 0.12f else 0.10f
                )
            )
    )
}

private fun actionMenuContentColor(
    isDark: Boolean,
    accentColor: Color,
    variant: GlassVariant,
    enabled: Boolean,
    primary: Boolean
): Color {
    val color = when (variant) {
        GlassVariant.SheetDangerAction -> Color(0xFFE25B6A)
        GlassVariant.SheetPrimaryAction -> accentColor
        else -> if (primary) {
            if (accentColor == Color.Unspecified) {
                if (isDark) Color(0xFF71ADFF) else Color(0xFF3B82F6)
            } else {
                accentColor
            }
        } else {
            if (isDark) Color.White.copy(alpha = 0.92f) else Color(0xFF111827).copy(alpha = 0.88f)
        }
    }
    return if (enabled) color else color.copy(alpha = 0.38f)
}
