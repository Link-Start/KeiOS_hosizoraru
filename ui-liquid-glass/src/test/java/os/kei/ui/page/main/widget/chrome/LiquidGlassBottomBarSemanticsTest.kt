package os.kei.ui.page.main.widget.chrome

import android.app.Application
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.down
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = Application::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class LiquidGlassBottomBarSemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun itemExposesTabSelectionClickAndOneExplicitLabel() {
        var clickCount = 0

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                Row(Modifier.size(width = 240.dp, height = 64.dp)) {
                    LiquidGlassBottomBarItem(
                        selected = true,
                        tabIndex = 0,
                        onClick = { clickCount++ },
                        label = "Home",
                        modifier = Modifier.testTag("home-tab"),
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Basic.Check,
                            contentDescription = null,
                        )
                        Text("Home")
                    }
                }
            }
        }

        composeRule
            .onNodeWithTag("home-tab")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .assertIsSelected()
            .assertHasClickAction()
            .performClick()

        composeRule
            .onAllNodesWithContentDescription("Home", useUnmergedTree = true)
            .assertCountEquals(1)
        assertEquals(1, clickCount)
    }

    @Test
    fun disabledItemReportsDisabledAndIgnoresPhysicalTap() {
        var clickCount = 0

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                Row(Modifier.size(width = 240.dp, height = 64.dp)) {
                    LiquidGlassBottomBarItem(
                        selected = false,
                        tabIndex = 0,
                        onClick = { clickCount++ },
                        enabled = false,
                        label = "Disabled",
                        modifier = Modifier.testTag("disabled-tab"),
                    ) {
                        Text("Disabled")
                    }
                }
            }
        }

        composeRule
            .onNodeWithTag("disabled-tab")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
            .assertIsNotEnabled()
            .performTouchInput { click() }

        assertEquals(0, clickCount)
    }

    @Test
    fun barExposesSelectableGroupSemantics() {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val backdrop = rememberLayerBackdrop()
                Box(
                    modifier =
                        Modifier
                            .size(width = 240.dp, height = 96.dp)
                            .background(Color(0xFFF3F4F6))
                            .layerBackdrop(backdrop),
                    contentAlignment = Alignment.Center,
                ) {
                    LiquidGlassBottomBar(
                        modifier = Modifier.testTag("tab-group"),
                        selectedIndex = 0,
                        onSelected = {},
                        backdrop = backdrop,
                        tabsCount = 2,
                        isLiquidEffectEnabled = false,
                    ) {
                        repeat(2) { index ->
                            LiquidGlassBottomBarItem(
                                selected = index == 0,
                                tabIndex = index,
                                onClick = {},
                                label = "Tab $index",
                            ) {
                                Text("Tab $index")
                            }
                        }
                    }
                }
            }
        }

        composeRule
            .onNodeWithTag("tab-group")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
    }

    @Test
    fun disabledBarKeepsRenderingWhileRemovingInputAndSemantics() {
        var clickCount = 0
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val backdrop = rememberLayerBackdrop()
                Box(
                    modifier =
                        Modifier
                            .size(width = 240.dp, height = 96.dp)
                            .background(Color(0xFFF3F4F6))
                            .layerBackdrop(backdrop),
                    contentAlignment = Alignment.Center,
                ) {
                    LiquidGlassBottomBar(
                        modifier = Modifier.testTag("disabled-bar"),
                        selectedIndex = 0,
                        onSelected = { clickCount++ },
                        backdrop = backdrop,
                        tabsCount = 2,
                        interactionEnabled = false,
                        isLiquidEffectEnabled = false,
                    ) {
                        repeat(2) { index ->
                            LiquidGlassBottomBarItem(
                                selected = index == 0,
                                tabIndex = index,
                                onClick = { clickCount++ },
                                label = "Disabled tab $index",
                            ) {
                                Text("Disabled tab $index")
                            }
                        }
                    }
                }
            }
        }

        composeRule.onAllNodes(hasClickAction()).assertCountEquals(0)
        composeRule
            .onAllNodesWithContentDescription("Disabled tab 0", useUnmergedTree = true)
            .assertCountEquals(0)
        composeRule.onNodeWithTag("disabled-bar").performTouchInput {
            click(Offset(width * 0.25f, height / 2f))
            down(Offset(width * 0.25f, height / 2f))
            moveBy(Offset(width * 0.5f, 0f))
            up()
        }
        composeRule.waitForIdle()

        assertEquals(0, clickCount)
    }

    @Test
    fun singleItemBarHandlesHorizontalDragWithoutGestureExclusionOrAnimationFailure() {
        lateinit var rootView: View
        val selectedIndices = mutableListOf<Int>()

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                rootView = LocalView.current
                val backdrop = rememberLayerBackdrop()
                Box(
                    modifier =
                        Modifier
                            .size(width = 240.dp, height = 96.dp)
                            .background(Color(0xFFF3F4F6))
                            .layerBackdrop(backdrop),
                    contentAlignment = Alignment.Center,
                ) {
                    LiquidGlassBottomBar(
                        selectedIndex = 0,
                        onSelected = { selectedIndices += it },
                        backdrop = backdrop,
                        tabsCount = 1,
                        isLiquidEffectEnabled = false,
                    ) {
                        LiquidGlassBottomBarItem(
                            selected = true,
                            tabIndex = 0,
                            onClick = { selectedIndices += 0 },
                            label = "Only",
                            modifier = Modifier.testTag("only-tab"),
                        ) {
                            Text("Only")
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithTag("only-tab").performTouchInput {
            down(center)
            moveBy(Offset(48f, 0f))
            up()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("only-tab").assertIsSelected()
        assertTrue(selectedIndices.all { it == 0 })
        assertTrue(rootView.systemGestureExclusionRects.isEmpty())
    }

    @Test
    fun dragSettlementSkipsDisabledTabsInTravelDirection() {
        val enabled: (Int) -> Boolean = { index -> index != 1 }

        assertEquals(
            2,
            liquidBottomBarResolvedEnabledIndex(
                targetIndex = 1,
                currentIndex = 0,
                tabsCount = 3,
                isTabEnabled = enabled,
            ),
        )
        assertEquals(
            0,
            liquidBottomBarResolvedEnabledIndex(
                targetIndex = 1,
                currentIndex = 2,
                tabsCount = 3,
                isTabEnabled = enabled,
            ),
        )
        assertEquals(
            1,
            liquidBottomBarResolvedEnabledIndex(
                targetIndex = 1,
                currentIndex = 1,
                tabsCount = 3,
                isTabEnabled = { false },
            ),
        )
    }

    @Test
    fun externalPositionResolverRejectsNonFiniteValuesAndClampsFiniteValues() {
        assertEquals(null, liquidBottomBarFinitePosition(Float.NaN, tabsCount = 4))
        assertEquals(null, liquidBottomBarFinitePosition(Float.POSITIVE_INFINITY, tabsCount = 4))
        assertEquals(null, liquidBottomBarFinitePosition(Float.NEGATIVE_INFINITY, tabsCount = 4))
        assertEquals(0f, liquidBottomBarFinitePosition(-2f, tabsCount = 4))
        assertEquals(1.5f, liquidBottomBarFinitePosition(1.5f, tabsCount = 4))
        assertEquals(3f, liquidBottomBarFinitePosition(8f, tabsCount = 4))
        assertEquals(0f, liquidBottomBarFinitePosition(8f, tabsCount = 0))
    }
}
