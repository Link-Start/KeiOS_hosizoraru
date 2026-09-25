package os.kei.ui.page.main.widget.core

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.math.abs
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import os.kei.ui.page.main.widget.support.LocalTextCopyExpandedOverride
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@RunWith(AndroidJUnit4::class)
@Config(
    application = Application::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class AppInfoRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun defaultLabelWidthRemainsContentDrivenWhenMaximumIsProvided() {
        setInfoRow {
            Box(modifier = Modifier.width(280.dp)) {
                AppInfoRow(
                    label = "ID",
                    value = "150113",
                    labelMinWidth = 70.dp,
                    labelMaxWidth = 123.dp,
                )
            }
        }

        val labelBounds =
            composeRule
                .onNodeWithText("ID", useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        val valueBounds =
            composeRule
                .onNodeWithText("150113", useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        val maximumExpectedWidth = with(composeRule.density) { 71.dp.toPx() }
        val maximumExpectedValueStart = with(composeRule.density) { 81.dp.toPx() }

        assertTrue(
            labelBounds.width <= maximumExpectedWidth,
            "Short labels must keep the 70dp content-driven minimum instead of expanding to the maximum: $labelBounds",
        )
        assertTrue(
            valueBounds.left <= maximumExpectedValueStart,
            "The value must start after the 70dp label and 10dp gap instead of a fixed maximum-width label: $valueBounds",
        )
    }

    @Test
    fun defaultCopyActionExposesLongClickSemantics() {
        setInfoRow {
            AppInfoRow(label = "Build", value = "debug")
        }

        val semantics = composeRule.onNodeWithText("Build").fetchSemanticsNode().config

        assertTrue(semantics.contains(SemanticsActions.OnLongClick))
    }

    @Test
    fun disabledDefaultCopyActionRemovesLongClickSemantics() {
        setInfoRow {
            AppInfoRow(
                label = "Build",
                value = "debug",
                enableLongPressCopy = false,
            )
        }

        val semantics = composeRule.onNodeWithText("Build").fetchSemanticsNode().config

        assertFalse(semantics.contains(SemanticsActions.OnLongClick))
    }

    @Test
    fun topAlignmentKeepsLargeFontMultilineValueAlignedWithLabel() {
        setInfoRow(fontScale = 1.5f) {
            Box(modifier = Modifier.width(360.dp)) {
                AppInfoRow(
                    label = MULTILINE_LABEL,
                    value = MULTILINE_VALUE,
                    modifier = Modifier.testTag(MULTILINE_ROW_TAG),
                    labelWeight = 0.28f,
                    valueWeight = 0.72f,
                    horizontalSpacing = 8.dp,
                    rowVerticalPadding = 0.dp,
                    verticalAlignment = Alignment.Top,
                    valueTextAlign = TextAlign.Start,
                    labelMaxLines = 1,
                    valueMaxLines = 3,
                    labelFontSize = AppTypographyTokens.Supporting.fontSize,
                    labelLineHeight = AppTypographyTokens.Supporting.lineHeight,
                    valueFontSize = AppTypographyTokens.Supporting.fontSize,
                    valueLineHeight = AppTypographyTokens.Supporting.lineHeight,
                    emphasizedValue = false,
                )
            }
        }

        val rowBounds =
            composeRule
                .onNodeWithTag(MULTILINE_ROW_TAG)
                .fetchSemanticsNode()
                .boundsInRoot
        val labelBounds =
            composeRule
                .onNodeWithText(MULTILINE_LABEL, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        val valueBounds =
            composeRule
                .onNodeWithText(MULTILINE_VALUE, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        val tolerance = with(composeRule.density) { 1.dp.toPx() }

        assertTrue(
            abs(labelBounds.top - valueBounds.top) <= tolerance,
            "Top-aligned label $labelBounds must meet multiline value $valueBounds",
        )
        assertTrue(
            valueBounds.height > labelBounds.height,
            "Multiline value $valueBounds must remain taller than label $labelBounds",
        )
        assertTrue(
            valueBounds.right <= rowBounds.right + tolerance,
            "Multiline value $valueBounds must stay inside 360dp row $rowBounds",
        )
    }

    private fun setInfoRow(
        fontScale: Float = 1f,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale),
                LocalTextCopyExpandedOverride provides false,
            ) {
                MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                    content()
                }
            }
        }
    }
}

private const val MULTILINE_ROW_TAG = "app-info-row-multiline"
private const val MULTILINE_LABEL = "Source"
private const val MULTILINE_VALUE = "A long first line\nA second line\nA third line"
