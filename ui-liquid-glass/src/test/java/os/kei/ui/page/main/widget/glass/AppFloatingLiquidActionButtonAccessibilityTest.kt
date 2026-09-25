@file:Suppress("FunctionName")

package os.kei.ui.page.main.widget.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = Application::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class AppFloatingLiquidActionButtonAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun smallVisualButtonKeepsUnifiedAccessibleInteractionRoot() {
        var clicks = 0
        composeRule.setContent {
            FloatingActionTestTheme {
                AppFloatingLiquidActionButton(
                    backdrop = null,
                    icon = MiuixIcons.Basic.Check,
                    contentDescription = "Confirm",
                    onClick = { clicks++ },
                    modifier = Modifier.testTag("small-fab"),
                    size = 36.dp,
                    badgeLabel = "7",
                )
            }
        }

        composeRule
            .onNodeWithTag("small-fab")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Confirm")))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "7"))
            .assertHasClickAction()
            .performClick()

        assertEquals(1, clicks)
        composeRule.onAllNodesWithText("7").assertCountEquals(0)
    }

    @Test
    fun disabledButtonKeepsBadgeDecorativeAndReportsDisabled() {
        composeRule.setContent {
            FloatingActionTestTheme {
                AppFloatingLiquidActionButton(
                    backdrop = null,
                    icon = MiuixIcons.Basic.Check,
                    contentDescription = "Confirm",
                    onClick = {},
                    modifier = Modifier.testTag("disabled-fab"),
                    enabled = false,
                    badgeLabel = "3",
                )
            }
        }

        composeRule
            .onNodeWithTag("disabled-fab")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "3"))
            .assertIsNotEnabled()
        composeRule.onAllNodesWithText("3").assertCountEquals(0)
    }

    @Test
    fun backdropAndFallbackUseTheSameButtonSemantics() {
        composeRule.setContent {
            FloatingActionTestTheme {
                val backdrop = rememberLayerBackdrop()
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color.White)
                            .layerBackdrop(backdrop),
                ) {
                    TestFloatingAction(tag = "backdrop-fab", backdrop = backdrop)
                    TestFloatingAction(tag = "fallback-fab", backdrop = null)
                }
            }
        }

        listOf("backdrop-fab", "fallback-fab").forEach { tag ->
            composeRule
                .onNodeWithTag(tag)
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Confirm")))
                .assertHasClickAction()
        }
    }

    @Test
    fun invalidVisualSizesResolveToTheStableDefault() {
        listOf(Dp.Unspecified, Dp(Float.NaN), Dp(Float.POSITIVE_INFINITY), (-1).dp, 0.dp).forEach { invalid ->
            val metrics = resolveFloatingActionMetrics(invalid)

            assertEquals(AppChromeTokens.floatingBottomBarOuterHeight, metrics.visualSize)
            assertEquals(AppChromeTokens.floatingBottomBarOuterHeight, metrics.touchSize)
        }
    }
}

@androidx.compose.runtime.Composable
private fun TestFloatingAction(
    tag: String,
    backdrop: LayerBackdrop?,
) {
    AppFloatingLiquidActionButton(
        backdrop = backdrop,
        icon = MiuixIcons.Basic.Check,
        contentDescription = "Confirm",
        onClick = {},
        modifier = Modifier.testTag(tag),
        size = 36.dp,
    )
}

@androidx.compose.runtime.Composable
private fun FloatingActionTestTheme(content: @androidx.compose.runtime.Composable () -> Unit) {
    MiuixTheme(controller = ThemeController(ColorSchemeMode.Light), content = content)
}
