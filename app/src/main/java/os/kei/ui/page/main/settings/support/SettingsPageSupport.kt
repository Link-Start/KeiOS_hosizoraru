@file:Suppress("FunctionName")

package os.kei.ui.page.main.settings.support

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.ui.page.main.settings.cache.CacheEntrySummary
import os.kei.ui.page.main.widget.core.AppControlRow
import os.kei.ui.page.main.widget.core.AppFeatureCard
import os.kei.ui.page.main.widget.core.AppInfoRow
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.core.CardLayoutRhythm
import os.kei.ui.page.main.widget.glass.AppInteractiveTokens
import os.kei.ui.page.main.widget.glass.AppStandaloneLiquidTextButton
import os.kei.ui.page.main.widget.glass.AppSwitch
import os.kei.ui.page.main.widget.glass.GlassVariant
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun SettingsGroupCard(
    header: String,
    title: String,
    sectionIcon: ImageVector? = null,
    containerColor: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    AppFeatureCard(
        title = title,
        subtitle = "",
        eyebrow = header,
        sectionIcon = sectionIcon,
        containerColor = containerColor,
        showIndication = false,
        contentVerticalSpacing = CardLayoutRhythm.denseSectionGap,
        contentPadding =
            PaddingValues(
                start = CardLayoutRhythm.cardHorizontalPadding,
                end = CardLayoutRhythm.cardHorizontalPadding,
                bottom = CardLayoutRhythm.cardVerticalPadding,
            ),
        content = content,
    )
}

@Composable
internal fun SettingsActionItem(
    title: String,
    summary: String,
    infoKey: String? = null,
    infoValue: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val contentAlpha = if (enabled) 1f else AppInteractiveTokens.disabledContentAlpha
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .let { base ->
                    if (onClick != null) {
                        base.clickable(
                            enabled = enabled,
                            role = Role.Button,
                            onClick = onClick,
                        )
                    } else {
                        base
                    }
                }.defaultMinSize(minHeight = AppInteractiveTokens.controlRowMinHeight)
                .padding(vertical = CardLayoutRhythm.controlRowVerticalPadding),
        verticalArrangement = Arrangement.spacedBy(CardLayoutRhythm.denseSectionGap),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CardLayoutRhythm.controlRowGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = MiuixTheme.colorScheme.onBackground.copy(alpha = contentAlpha),
                fontSize = AppTypographyTokens.CompactTitle.fontSize,
                lineHeight = AppTypographyTokens.CompactTitle.lineHeight,
                fontWeight = AppTypographyTokens.CompactTitle.fontWeight,
                modifier = Modifier.weight(1f),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(CardLayoutRhythm.infoRowGap),
                verticalAlignment = Alignment.CenterVertically,
                content = trailing,
            )
        }
        if (summary.isNotBlank()) {
            Text(
                text = summary,
                color = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.90f * contentAlpha),
                fontSize = AppTypographyTokens.Supporting.fontSize,
                lineHeight = AppTypographyTokens.Supporting.lineHeight,
            )
        }
        if (!infoKey.isNullOrBlank() && !infoValue.isNullOrBlank()) {
            SettingsInfoItem(
                key = infoKey,
                value = infoValue,
            )
        }
    }
}

@Composable
internal fun SettingsNavigationItem(
    title: String,
    summary: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    infoKey: String? = null,
    infoValue: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    SettingsActionItem(
        title = title,
        summary = summary,
        infoKey = infoKey,
        infoValue = infoValue,
        onClick = onClick,
        enabled = enabled,
        trailing = trailing,
    )
}

@Composable
internal fun SettingsValueItem(
    title: String,
    summary: String,
    infoKey: String? = null,
    infoValue: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    SettingsActionItem(
        title = title,
        summary = summary,
        infoKey = infoKey,
        infoValue = infoValue,
        trailing = trailing,
    )
}

@Composable
internal fun SettingsPickerItem(
    title: String,
    summary: String,
    infoKey: String? = null,
    infoValue: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit,
) {
    SettingsActionItem(
        title = title,
        summary = summary,
        infoKey = infoKey,
        infoValue = infoValue,
        onClick = onClick,
        trailing = trailing,
    )
}

@Composable
internal fun SettingsButtonActionItem(
    title: String,
    summary: String,
    infoKey: String? = null,
    infoValue: String? = null,
    trailing: @Composable RowScope.() -> Unit,
) {
    SettingsActionItem(
        title = title,
        summary = summary,
        infoKey = infoKey,
        infoValue = infoValue,
        trailing = trailing,
    )
}

@Composable
internal fun SettingsToggleItem(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    infoKey: String? = null,
    infoValue: String? = null,
    enabled: Boolean = true,
) {
    SettingsActionItem(
        title = title,
        summary = summary,
        infoKey = infoKey,
        infoValue = infoValue,
        onClick = { onCheckedChange(!checked) },
        enabled = enabled,
        trailing = {
            AppSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        },
    )
}

@Composable
internal fun SettingsInfoItem(
    key: String,
    value: String,
) {
    val resolvedValue = value.ifBlank { stringResource(R.string.common_na) }
    val commonScopeLabel = stringResource(R.string.common_scope)
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val stacked = key == commonScopeLabel || resolvedValue.length >= 32 || maxWidth < 300.dp
        AppInfoRow(
            label = key,
            value = resolvedValue,
            labelColor = MiuixTheme.colorScheme.onBackgroundVariant,
            valueColor = MiuixTheme.colorScheme.onBackground,
            labelMinWidth = 64.dp,
            labelMaxWidth = 112.dp,
            horizontalSpacing = CardLayoutRhythm.infoRowGap,
            rowVerticalPadding = CardLayoutRhythm.infoRowVerticalPadding,
            labelMaxLines = 2,
            valueMaxLines = 6,
            valueOverflow = TextOverflow.Ellipsis,
            labelFontSize = AppTypographyTokens.Supporting.fontSize,
            labelLineHeight = AppTypographyTokens.Supporting.lineHeight,
            valueFontSize = AppTypographyTokens.Body.fontSize,
            valueLineHeight = AppTypographyTokens.Body.lineHeight,
            emphasizedValue = false,
            stacked = stacked,
        )
    }
}

@Composable
internal fun SettingsCacheRow(
    entry: CacheEntrySummary,
    clearing: Boolean,
    onClear: () -> Unit,
) {
    val titleColor = MiuixTheme.colorScheme.onBackground
    val subtitleColor = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.90f)
    val resetLabel = stringResource(R.string.common_reset)
    val actionColor =
        if (entry.clearLabel == resetLabel) {
            MiuixTheme.colorScheme.error
        } else {
            MiuixTheme.colorScheme.primary
        }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CardLayoutRhythm.denseSectionGap),
    ) {
        AppControlRow(
            title = entry.title,
            summary = entry.summary,
            titleColor = titleColor,
            minHeight = 48.dp,
            trailing = {
                if (entry.clearLabel.isNotBlank()) {
                    AppStandaloneLiquidTextButton(
                        variant = GlassVariant.Compact,
                        text = if (clearing) stringResource(R.string.common_processing) else entry.clearLabel,
                        textColor = actionColor,
                        containerColor = actionColor,
                        enabled = !clearing,
                        onClick = onClear,
                    )
                }
            },
        )
        Text(
            text = entry.detail,
            color = subtitleColor,
            fontSize = AppTypographyTokens.Supporting.fontSize,
            lineHeight = AppTypographyTokens.Supporting.lineHeight,
        )
        Text(
            text = entry.activity,
            color = subtitleColor,
            fontSize = AppTypographyTokens.Supporting.fontSize,
            lineHeight = AppTypographyTokens.Supporting.lineHeight,
        )
        Text(
            text = entry.storage,
            color = subtitleColor,
            fontSize = AppTypographyTokens.Supporting.fontSize,
            lineHeight = AppTypographyTokens.Supporting.lineHeight,
        )
    }
}

internal fun formatBytes(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L).toDouble()
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    return when {
        safe >= gb -> String.format(Locale.US, "%.2f GB", safe / gb)
        safe >= mb -> String.format(Locale.US, "%.2f MB", safe / mb)
        safe >= kb -> String.format(Locale.US, "%.2f KB", safe / kb)
        else -> "${safe.toLong()} B"
    }
}

internal fun formatLogTime(timestampMs: Long): String {
    if (timestampMs <= 0L) return ""
    return runCatching {
        SimpleDateFormat("yy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestampMs))
    }.getOrElse { "" }
}

internal fun formatOpacityPercent(alpha: Float): Int = (alpha.coerceIn(0f, 1f) * 100f).roundToInt()

internal fun formatMilliseconds(value: Int): Int = value.coerceAtLeast(0)

internal const val NON_HOME_BACKGROUND_OPACITY_DEFAULT = 0.16f
internal const val NON_HOME_BACKGROUND_OPACITY_MIN = 0.06f
internal const val NON_HOME_BACKGROUND_OPACITY_MAX = 0.40f
internal const val NON_HOME_BACKGROUND_OPACITY_MAGNET_THRESHOLD = 0.03f
internal val NON_HOME_BACKGROUND_OPACITY_KEY_POINTS =
    listOf(
        0.06f,
        0.10f,
        0.13f,
        NON_HOME_BACKGROUND_OPACITY_DEFAULT,
        0.20f,
        0.26f,
        0.33f,
        NON_HOME_BACKGROUND_OPACITY_MAX,
    )
internal const val SUPER_ISLAND_RESTORE_DELAY_DEFAULT_MS = 100f
internal const val SUPER_ISLAND_RESTORE_DELAY_MIN_MS = 50f
internal const val SUPER_ISLAND_RESTORE_DELAY_MAX_MS = 350f
internal const val SUPER_ISLAND_RESTORE_DELAY_MAGNET_THRESHOLD = 6f
internal val SUPER_ISLAND_RESTORE_DELAY_KEY_POINTS =
    listOf(
        SUPER_ISLAND_RESTORE_DELAY_MIN_MS,
        75f,
        SUPER_ISLAND_RESTORE_DELAY_DEFAULT_MS,
        125f,
        150f,
        200f,
        250f,
        300f,
        SUPER_ISLAND_RESTORE_DELAY_MAX_MS,
    )
