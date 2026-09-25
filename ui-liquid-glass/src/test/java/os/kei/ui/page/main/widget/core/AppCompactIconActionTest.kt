@file:Suppress("FunctionName")

package os.kei.ui.page.main.widget.core

import android.app.Application
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
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
class AppCompactIconActionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rootUsesFortyEightDpButtonSemanticsAndHandlesClick() {
        var clicks = 0
        composeRule.setContent {
            CompactActionTestTheme {
                AppCompactIconAction(
                    icon = MiuixIcons.Basic.Check,
                    contentDescription = "Confirm",
                    onClick = { clicks++ },
                    modifier = Modifier.size(36.dp).testTag("action"),
                )
            }
        }

        composeRule
            .onNodeWithTag("action")
            .assertWidthIsEqualTo(48.dp)
            .assertHeightIsEqualTo(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Confirm")))
            .assertHasClickAction()
            .performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun disabledRootReportsDisabledAndIgnoresPointerClicks() {
        var clicks = 0
        composeRule.setContent {
            CompactActionTestTheme {
                AppCompactIconAction(
                    icon = MiuixIcons.Basic.Check,
                    contentDescription = "Unavailable",
                    onClick = { clicks++ },
                    modifier = Modifier.testTag("disabled-action"),
                    enabled = false,
                )
            }
        }

        composeRule
            .onNodeWithTag("disabled-action")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertIsNotEnabled()
            .performTouchInput { click() }

        assertEquals(0, clicks)
    }

    @Test
    fun touchSlotHandlesTapsOutsideTheThirtyDpVisual() {
        var clicks = 0
        composeRule.setContent {
            CompactActionTestTheme {
                AppCompactIconAction(
                    icon = MiuixIcons.Basic.Check,
                    contentDescription = "Confirm",
                    onClick = { clicks++ },
                    modifier = Modifier.testTag("action"),
                    visualSize = 30.dp,
                )
            }
        }

        composeRule.onNodeWithTag("action").performTouchInput {
            click(Offset(x = 4f, y = centerY))
        }

        assertEquals(1, clicks)
    }

    @Test
    fun childActionConsumesTapInsideCombinedClickableParent() {
        var parentClicks = 0
        var childClicks = 0
        composeRule.setContent {
            CompactActionTestTheme {
                Box(
                    modifier =
                        Modifier
                            .size(96.dp)
                            .testTag("parent")
                            .combinedClickable(
                                onClick = { parentClicks++ },
                                onLongClick = {},
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    AppCompactIconAction(
                        icon = MiuixIcons.Basic.Check,
                        contentDescription = "Confirm",
                        onClick = { childClicks++ },
                        modifier = Modifier.testTag("child"),
                    )
                }
            }
        }

        composeRule.onNodeWithTag("child").performTouchInput { click() }

        assertEquals(1, childClicks)
        assertEquals(0, parentClicks)
    }

    @Test
    fun visualSizeResolverFallsBackAndKeepsCompactBounds() {
        listOf(
            Dp.Unspecified,
            Dp(Float.NaN),
            Dp(Float.POSITIVE_INFINITY),
            Dp(Float.NEGATIVE_INFINITY),
            (-1).dp,
            0.dp,
        ).forEach { invalidSize ->
            assertEquals(
                AppCompactIconActionDefaultVisualSize,
                resolveCompactIconActionVisualSize(invalidSize),
            )
        }

        assertEquals(30.dp, resolveCompactIconActionVisualSize(24.dp))
        assertEquals(38.dp, resolveCompactIconActionVisualSize(38.dp))
        assertEquals(42.dp, resolveCompactIconActionVisualSize(56.dp))
    }

    @Test
    fun indicatorKeepsTheVisualSlotWithoutCreatingAnAction() {
        composeRule.setContent {
            CompactActionTestTheme {
                AppCompactIconIndicator(
                    icon = MiuixIcons.Basic.Check,
                    modifier = Modifier.size(36.dp).testTag("indicator"),
                )
            }
        }

        composeRule
            .onNodeWithTag("indicator")
            .assertWidthIsEqualTo(48.dp)
            .assertHeightIsEqualTo(48.dp)
        composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(0)
    }
}

@androidx.compose.runtime.Composable
private fun CompactActionTestTheme(content: @androidx.compose.runtime.Composable () -> Unit) {
    MiuixTheme(controller = ThemeController(ColorSchemeMode.Light), content = content)
}
