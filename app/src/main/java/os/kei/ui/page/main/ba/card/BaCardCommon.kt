package os.kei.ui.page.main.ba.card

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import os.kei.R
import os.kei.ui.page.main.ba.BaLiquidPanel
import os.kei.ui.page.main.widget.glass.AppLiquidIconButton
import os.kei.ui.page.main.widget.glass.AppLiquidTextButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.composables.icons.lucide.R as LucideR

/**
 * @param expandable when true the whole header becomes the disclosure control: a chevron after
 *   [trailing], the row itself clickable, and the collapsed/expanded state exposed to accessibility
 *   services. Mirrors `AppCardHeader`'s affordance so the two header families read the same, without
 *   dragging in its typography and paddings, which are not the BA card's.
 */
@Composable
internal fun BaCardHeader(
    title: String,
    modifier: Modifier = Modifier,
    titleIconRes: Int? = null,
    expandable: Boolean = false,
    expanded: Boolean = false,
    expandTint: Color = MiuixTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val expandStateDescription =
        if (expanded) {
            stringResource(R.string.common_collapse)
        } else {
            stringResource(R.string.common_expand)
        }
    val expandActionDescription =
        if (expanded) {
            stringResource(R.string.common_collapse_details_hint)
        } else {
            stringResource(R.string.common_expand_details_hint)
        }
    val clickModifier =
        if (onClick != null) {
            val interactionSource = remember { MutableInteractionSource() }
            Modifier
                .clickable(
                    interactionSource = interactionSource,
                    // The card behind the header already carries the glass material; a ripple on top
                    // of it reads as a second surface.
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ).then(
                    if (expandable) {
                        Modifier.semantics {
                            stateDescription = expandStateDescription
                            onClick(label = expandActionDescription) {
                                onClick()
                                true
                            }
                        }
                    } else {
                        Modifier
                    },
                )
        } else {
            Modifier
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(clickModifier),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = MiuixTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            titleIconRes?.let { iconRes ->
                Image(
                    painter = painterResource(id = iconRes),
                    contentDescription = title,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        if (trailing != null || expandable) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                trailing?.invoke(this)
                if (expandable) {
                    Icon(
                        imageVector =
                            ImageVector.vectorResource(
                                if (expanded) {
                                    LucideR.drawable.lucide_ic_chevron_up
                                } else {
                                    LucideR.drawable.lucide_ic_chevron_down
                                },
                            ),
                        contentDescription = expandStateDescription,
                        tint = expandTint,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun BaCompactDualMetricPanel(
    backdrop: Backdrop?,
    firstLabel: String,
    firstValue: String,
    secondLabel: String,
    secondValue: String,
    accentColor: Color,
    valueColor: Color = accentColor,
    modifier: Modifier = Modifier,
) {
    BaLiquidPanel(
        backdrop = backdrop,
        // Every caller of this panel is an office card, which exports a uniform layer.
        flattenOverUniformParent = true,
        modifier = modifier.fillMaxWidth(),
        accentColor = accentColor,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BaCompactMetricLine(
                label = firstLabel,
                value = firstValue,
                valueColor = valueColor,
                modifier = Modifier.weight(1f),
            )
            BaCompactMetricLine(
                label = secondLabel,
                value = secondValue,
                valueColor = valueColor,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BaCompactMetricLine(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.9f),
            fontSize = 13.sp,
            lineHeight = 16.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 16.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun BaInlineActionPanel(
    backdrop: Backdrop?,
    buttonText: String,
    buttonIconRes: Int? = null,
    /** Tags the row's button, not the panel: the panel itself has no semantics to hang a node on. */
    buttonTestTag: String? = null,
    countdownText: String,
    timeText: String,
    accentColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val buttonModifier = buttonTestTag?.let { tag -> Modifier.testTag(tag) } ?: Modifier
    val countdownBlue = Color(0xFF60A5FA)
    BaLiquidPanel(
        backdrop = backdrop,
        modifier = Modifier.fillMaxWidth(),
        accentColor = accentColor,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (buttonIconRes != null) {
                AppLiquidIconButton(
                    backdrop = backdrop,
                    modifier = buttonModifier,
                    painter = painterResource(id = buttonIconRes),
                    contentDescription = buttonText,
                    onClick = {
                        if (enabled) onClick()
                    },
                    onLongClick = onLongClick,
                    variant = GlassVariant.Content,
                    width = 52.dp,
                    height = 40.dp,
                    iconTint = Color.Unspecified,
                    containerColor = accentColor
                )
            } else {
                AppLiquidTextButton(
                    backdrop = backdrop,
                    modifier = buttonModifier,
                    text = buttonText,
                    textColor = accentColor,
                    containerColor = accentColor,
                    enabled = enabled,
                    variant = GlassVariant.Content,
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
            }
            Text(
                text = countdownText,
                color = countdownBlue,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = timeText,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun BaLimitValueText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .width(72.dp)
                .heightIn(min = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
