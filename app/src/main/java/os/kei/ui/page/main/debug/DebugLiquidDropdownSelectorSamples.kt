@file:Suppress("FunctionName")

package os.kei.ui.page.main.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import os.kei.R
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.core.CardLayoutRhythm
import os.kei.ui.page.main.widget.glass.AppDropdownSelector
import os.kei.ui.page.main.widget.glass.GlassVariant
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun DebugLiquidProductionDropdownSelectorSamples(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
) {
    val contentColor = MiuixTheme.colorScheme.onBackground
    val secondaryColor = MiuixTheme.colorScheme.onBackgroundVariant
    val standardOptions =
        listOf(
            stringResource(R.string.debug_component_lab_liquid_selector_option_compact),
            stringResource(R.string.debug_component_lab_liquid_selector_option_balanced),
            stringResource(R.string.debug_component_lab_liquid_selector_option_comfortable),
            stringResource(R.string.debug_component_lab_liquid_selector_option_adaptive),
        )
    val multilingualOptions =
        listOf(
            stringResource(R.string.debug_component_lab_liquid_selector_long_zh),
            stringResource(R.string.debug_component_lab_liquid_selector_long_en),
            stringResource(R.string.debug_component_lab_liquid_selector_long_ja),
        )
    val scrollingOptions =
        (1..DebugLiquidDropdownStressOptionCount).map { index ->
            stringResource(R.string.debug_component_lab_liquid_selector_scroll_option, index)
        }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(CardLayoutRhythm.denseSectionGap),
    ) {
        DebugLiquidDropdownSampleLabel(
            text = stringResource(R.string.debug_component_lab_liquid_selector_production_section),
            color = contentColor,
            emphasized = true,
        )
        DebugLiquidDropdownSampleLabel(
            text = stringResource(R.string.debug_component_lab_liquid_selector_production_hint),
            color = secondaryColor,
        )

        DebugLiquidDropdownSampleLabel(
            text = stringResource(R.string.debug_component_lab_liquid_selector_standard_label),
            color = secondaryColor,
        )
        DebugLiquidProductionDropdownSelector(
            options = standardOptions,
            initialSelectedIndex = 1,
            backdrop = backdrop,
            textColor = contentColor,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(DEBUG_LIQUID_STANDARD_SELECTOR_TAG),
        )

        DebugLiquidDropdownSampleLabel(
            text = stringResource(R.string.debug_component_lab_liquid_selector_multilingual_label),
            color = secondaryColor,
        )
        DebugLiquidProductionDropdownSelector(
            options = multilingualOptions,
            initialSelectedIndex = 0,
            backdrop = backdrop,
            textColor = contentColor,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(DEBUG_LIQUID_MULTILINGUAL_SELECTOR_TAG),
        )

        DebugLiquidDropdownSampleLabel(
            text = stringResource(R.string.debug_component_lab_liquid_selector_scroll_label),
            color = secondaryColor,
        )
        DebugLiquidProductionDropdownSelector(
            options = scrollingOptions,
            initialSelectedIndex = DebugLiquidDropdownStressSelectedIndex,
            popupMaxHeight = DebugLiquidDropdownStressPopupMaxHeight,
            backdrop = backdrop,
            textColor = contentColor,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(DEBUG_LIQUID_SCROLL_SELECTOR_TAG),
        )

        DebugLiquidDropdownSampleLabel(
            text = stringResource(R.string.debug_component_lab_liquid_selector_states_label),
            color = secondaryColor,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CardLayoutRhythm.infoRowGap),
        ) {
            DebugLiquidProductionDropdownSelector(
                options =
                    listOf(
                        stringResource(R.string.debug_component_lab_liquid_selector_disabled),
                    ),
                initialSelectedIndex = 0,
                enabled = false,
                backdrop = backdrop,
                textColor = contentColor,
                modifier =
                    Modifier
                        .weight(1f)
                        .testTag(DEBUG_LIQUID_DISABLED_SELECTOR_TAG),
            )
            DebugLiquidProductionDropdownSelector(
                options = emptyList(),
                initialSelectedIndex = 0,
                unavailableText = stringResource(R.string.debug_component_lab_liquid_selector_empty),
                backdrop = backdrop,
                textColor = contentColor,
                modifier =
                    Modifier
                        .weight(1f)
                        .testTag(DEBUG_LIQUID_EMPTY_SELECTOR_TAG),
            )
        }
    }
}

@Composable
private fun DebugLiquidProductionDropdownSelector(
    options: List<String>,
    initialSelectedIndex: Int,
    backdrop: Backdrop?,
    textColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    unavailableText: String = "",
    popupMaxHeight: Dp? = null,
) {
    var selectedIndex by
        remember(options, initialSelectedIndex) {
            mutableIntStateOf(
                initialSelectedIndex.coerceIn(0, options.lastIndex.coerceAtLeast(0)),
            )
        }
    var expanded by remember { mutableStateOf(false) }
    val selectedText = options.getOrNull(selectedIndex) ?: unavailableText

    AppDropdownSelector(
        selectedText = selectedText,
        options = options,
        selectedIndex = selectedIndex,
        expanded = expanded,
        onExpandedChange = { expanded = it },
        onSelectedIndexChange = { selectedIndex = it },
        modifier = modifier,
        backdrop = backdrop,
        variant = GlassVariant.SheetAction,
        textColor = textColor,
        anchorFillMaxWidth = true,
        anchorTextMaxLines = 1,
        anchorTextOverflow = TextOverflow.Ellipsis,
        anchorTextSoftWrap = false,
        dropdownItemTextMaxLines = 1,
        popupMaxWidth = null,
        popupMaxHeight = popupMaxHeight,
        popupMatchAnchorWidth = true,
        enabled = enabled,
        dropdownItemVariant = GlassVariant.SheetAction,
    )
}

@Composable
private fun DebugLiquidDropdownSampleLabel(
    text: String,
    color: Color,
    emphasized: Boolean = false,
) {
    Text(
        text = text,
        color = color,
        fontSize = AppTypographyTokens.Supporting.fontSize,
        lineHeight = AppTypographyTokens.Supporting.lineHeight,
        fontWeight = if (emphasized) FontWeight.Medium else FontWeight.Normal,
    )
}

internal const val DebugLiquidDropdownStressOptionCount = 16
internal const val DebugLiquidDropdownStressSelectedIndex = 14
internal val DebugLiquidDropdownStressPopupMaxHeight = 220.dp

internal const val DEBUG_LIQUID_STANDARD_SELECTOR_TAG = "debug-liquid-selector-standard"
internal const val DEBUG_LIQUID_MULTILINGUAL_SELECTOR_TAG = "debug-liquid-selector-multilingual"
internal const val DEBUG_LIQUID_SCROLL_SELECTOR_TAG = "debug-liquid-selector-scroll"
internal const val DEBUG_LIQUID_DISABLED_SELECTOR_TAG = "debug-liquid-selector-disabled"
internal const val DEBUG_LIQUID_EMPTY_SELECTOR_TAG = "debug-liquid-selector-empty"
