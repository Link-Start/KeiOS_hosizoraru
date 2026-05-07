package os.kei.ui.page.main.debug.liquidv2

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import os.kei.ui.page.main.os.appLucideCloseIcon
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text

@Immutable
internal data class V2GlassDropdownItem(
    val label: String,
    val enabled: Boolean = true,
    val leadingIcon: ImageVector? = null,
    val trailingText: String? = null,
    val contentDescription: String? = null,
    val tint: Color = Color.Unspecified
)

@Composable
internal fun V2GlassSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    onTrailingClick: (() -> Unit)? = null,
    showClearAction: Boolean = true,
    clearContentDescription: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    V2GlassSearchField(
        value = TextFieldValue(value),
        onValueChange = { onValueChange(it.text) },
        placeholder = placeholder,
        backdrop = backdrop,
        modifier = modifier,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        onTrailingClick = onTrailingClick,
        showClearAction = showClearAction,
        clearContentDescription = clearContentDescription,
        enabled = enabled,
        readOnly = readOnly,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions
    )
}

@Composable
internal fun V2GlassSearchField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    onTrailingClick: (() -> Unit)? = null,
    showClearAction: Boolean = true,
    clearContentDescription: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    val palette = rememberV2LiquidGlassPalette()
    V2GlassSurface(
        backdrop = backdrop,
        modifier = modifier.height(48.dp),
        spec = V2GlassSurfaceSpec.capsule(
            surfaceColor = palette.clearTint,
            interactive = enabled,
            role = V2GlassRole.Neutral
        ).copy(
            disabled = !enabled,
            readabilityProfile = V2LiquidReadabilityProfile.RegularText
        ),
        contentPadding = PaddingValues(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = palette.secondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                enabled = enabled,
                readOnly = readOnly,
                singleLine = singleLine,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                textStyle = TextStyle(
                    color = palette.content,
                    fontSize = AppTypographyTokens.Body.fontSize
                ),
                decorationBox = { innerTextField ->
                    if (value.text.isBlank()) {
                        Text(
                            text = placeholder,
                            color = palette.secondary,
                            fontSize = AppTypographyTokens.Body.fontSize,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    innerTextField()
                }
            )
            val clearVisible = showClearAction && value.text.isNotBlank() && enabled && !readOnly
            val actionIcon = if (clearVisible) appLucideCloseIcon() else trailingIcon
            val action = if (clearVisible) {
                { onValueChange(TextFieldValue("")) }
            } else {
                onTrailingClick
            }
            if (actionIcon != null && action != null) {
                V2GlassIconButton(
                    icon = actionIcon,
                    contentDescription = clearContentDescription ?: "",
                    backdrop = backdrop,
                    size = V2GlassControlSize.Compact,
                    tint = palette.clearTint,
                    onClick = action
                )
            }
        }
    }
}

@Composable
internal fun V2GlassDropdown(
    label: String,
    items: List<V2GlassDropdownItem>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    maxMenuHeight: Dp = 260.dp,
    dismissOnSelect: Boolean = true,
    onDismiss: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    val palette = rememberV2LiquidGlassPalette()
    val menuBackdrop = rememberLayerBackdrop()
    val safeIndex = selectedIndex.coerceIn(0, items.lastIndex.coerceAtLeast(0))
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        V2GlassButton(
            text = if (items.isEmpty()) label else "$label · ${items[safeIndex].label}",
            backdrop = backdrop,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = null,
            tint = palette.accent.copy(alpha = 0.16f),
            enabled = enabled,
            onClick = { expanded = !expanded }
        )
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            V2GlassSurface(
                backdrop = backdrop,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxMenuHeight),
                spec = V2GlassSurfaceSpec(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                    surfaceColor = palette.panelTint,
                    blur = V2LiquidGlassTokens.blurBalanced,
                    lensHeight = 18.dp,
                    lensAmount = 28.dp,
                    readabilityProfile = V2LiquidReadabilityProfile.RegularText
                ),
                exportedBackdrop = menuBackdrop,
                contentPadding = PaddingValues(8.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items.forEachIndexed { index, item ->
                        val selected = index == safeIndex
                        V2GlassSurface(
                            backdrop = menuBackdrop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp),
                            spec = V2GlassSurfaceSpec.capsule(
                                tint = when {
                                    item.tint.isSpecified -> item.tint
                                    selected -> palette.accent.copy(alpha = 0.22f)
                                    else -> Color.Unspecified
                                },
                                surfaceColor = if (selected) palette.clearTint else Color.Transparent,
                                interactive = item.enabled,
                                role = V2GlassRole.Accent
                            ).copy(
                                selected = selected,
                                disabled = !item.enabled,
                                readabilityProfile = V2LiquidReadabilityProfile.BrightClear
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart,
                            onClick = {
                                if (item.enabled) {
                                    onSelectedIndexChange(index)
                                    if (dismissOnSelect) {
                                        expanded = false
                                        onDismiss()
                                    }
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (item.leadingIcon != null) {
                                    Icon(
                                        imageVector = item.leadingIcon,
                                        contentDescription = item.contentDescription,
                                        tint = palette.content,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = item.label,
                                    color = palette.content,
                                    fontSize = AppTypographyTokens.Body.fontSize,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (item.trailingText != null) {
                                    Text(
                                        text = item.trailingText,
                                        color = palette.secondary,
                                        fontSize = AppTypographyTokens.Supporting.fontSize,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
