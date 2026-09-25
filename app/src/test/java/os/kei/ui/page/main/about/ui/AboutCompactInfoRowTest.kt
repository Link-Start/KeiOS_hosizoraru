package os.kei.ui.page.main.about.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import os.kei.ui.page.main.os.appLucideInfoIcon
import os.kei.ui.page.main.widget.support.LocalTextCopyExpandedOverride
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = Application::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class AboutCompactInfoRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun longLocalizedLabelKeepsValueSeparatedAtLargeFont() {
        setAboutRow(fontScale = 1.5f) {
            Box(modifier = Modifier.width(COMPACT_ROW_WIDTH)) {
                AboutCompactInfoRow(
                    title = LONG_LABEL,
                    value = LONG_VALUE,
                    modifier = Modifier.testTag(ROW_TAG),
                )
            }
        }

        val rowBounds = composeRule.onNodeWithTag(ROW_TAG).fetchSemanticsNode().boundsInRoot
        val labelBounds =
            composeRule
                .onNodeWithText(LONG_LABEL, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        val valueBounds =
            composeRule
                .onNodeWithText(LONG_VALUE, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        val maximumLabelWidth = with(composeRule.density) { (COMPACT_ROW_WIDTH * 0.44f).toPx() }
        val minimumGap = with(composeRule.density) { 9.dp.toPx() }
        val tolerance = with(composeRule.density) { 1.dp.toPx() }

        assertTrue(
            labelBounds.width <= maximumLabelWidth + tolerance,
            "Label width ${labelBounds.width} must stay within the dynamic 44% cap $maximumLabelWidth",
        )
        assertTrue(
            valueBounds.left - labelBounds.right >= minimumGap,
            "Label $labelBounds and value $valueBounds must retain the compact 10dp information gap",
        )
        assertTrue(
            valueBounds.right <= rowBounds.right + tolerance,
            "Long value $valueBounds must remain inside row $rowBounds",
        )
    }

    @Test
    fun iconRowKeepsSingleButtonSemanticsAndClickBehavior() {
        var clickCount = 0

        setAboutRow {
            AboutCompactInfoRow(
                title = "Runtime",
                value = "Android",
                titleIcon = appLucideInfoIcon(),
                onClick = { clickCount++ },
            )
        }

        composeRule
            .onNodeWithText("Runtime")
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()

        composeRule.runOnIdle { assertEquals(1, clickCount) }
    }

    private fun setAboutRow(
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

private val COMPACT_ROW_WIDTH = 280.dp
private const val ROW_TAG = "about-compact-info-row"
private const val LONG_LABEL = "Localized runtime component label"
private const val LONG_VALUE = "os.kei.runtime.component.with.a.long.package.name"
