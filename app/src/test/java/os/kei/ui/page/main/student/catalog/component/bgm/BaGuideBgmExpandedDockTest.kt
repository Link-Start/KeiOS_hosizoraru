package os.kei.ui.page.main.student.catalog.component.bgm

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import os.kei.ui.page.main.widget.glass.LocalLiquidControlsEnabled
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
class BaGuideBgmExpandedDockTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun expandedDockExposesFourTabsAndExactlyOneSelectedTab() {
        setExpandedDock(selectedDockKey = BaGuideBgmDockKeys.Radio)

        composeRule
            .onAllNodes(tabRoleMatcher)
            .assertCountEquals(4)
        composeRule
            .onAllNodes(selectedTabMatcher)
            .assertCountEquals(1)
        composeRule
            .onNodeWithContentDescription("Radio")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
    }

    @Test
    fun disabledInteractionKeepsVisualsWithoutClickOrTabSemantics() {
        setExpandedDock(interactionEnabled = false)

        composeRule
            .onAllNodes(tabRoleMatcher, useUnmergedTree = true)
            .assertCountEquals(0)
        composeRule
            .onAllNodes(hasClickAction(), useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun clickingCurrentAndUnselectedTabsAlwaysReturnsTheirKeys() {
        val selectedKeys = mutableListOf<String>()
        setExpandedDock(onSelectedDockKeyChange = selectedKeys::add)

        composeRule
            .onNodeWithContentDescription("Home")
            .assertHasClickAction()
            .performClick()
        composeRule
            .onNodeWithContentDescription("Library")
            .assertHasClickAction()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(
                listOf(BaGuideBgmDockKeys.Home, BaGuideBgmDockKeys.Library),
                selectedKeys,
            )
        }
    }

    @Test
    fun emptyTabsComposeWithoutInteractiveSemantics() {
        setExpandedDock(tabs = emptyList(), selectedDockKey = "missing")

        composeRule
            .onAllNodes(tabRoleMatcher, useUnmergedTree = true)
            .assertCountEquals(0)
        composeRule
            .onAllNodes(hasClickAction(), useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun rtlVisualOrderKeepsLogicalTabKeyCallbacks() {
        val selectedKeys = mutableListOf<String>()
        setExpandedDock(
            selectedDockKey = BaGuideBgmDockKeys.Radio,
            layoutDirection = LayoutDirection.Rtl,
            onSelectedDockKeyChange = selectedKeys::add,
        )

        val homeNode = composeRule.onNodeWithContentDescription("Home")
        val libraryNode = composeRule.onNodeWithContentDescription("Library")
        assertTrue(
            homeNode
                .fetchSemanticsNode()
                .boundsInRoot.center.x >
                libraryNode
                    .fetchSemanticsNode()
                    .boundsInRoot.center.x,
        )

        homeNode.performTouchInput { click() }

        composeRule.runOnIdle {
            assertEquals(listOf(BaGuideBgmDockKeys.Home), selectedKeys)
        }
    }

    private fun setExpandedDock(
        tabs: List<BaGuideBgmDockTab> = testTabs(),
        selectedDockKey: String = BaGuideBgmDockKeys.Home,
        interactionEnabled: Boolean = true,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
        onSelectedDockKeyChange: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                CompositionLocalProvider(
                    LocalLayoutDirection provides layoutDirection,
                    LocalLiquidControlsEnabled provides false,
                ) {
                    val backdrop = rememberLayerBackdrop()
                    Box(
                        modifier =
                            Modifier
                                .size(width = 360.dp, height = 96.dp)
                                .background(Color(0xFFF3F4F6))
                                .layerBackdrop(backdrop),
                        contentAlignment = Alignment.Center,
                    ) {
                        BaGuideBgmExpandedDock(
                            tabs = tabs,
                            selectedDockKey = selectedDockKey,
                            selectedPositionProvider = null,
                            interactionEnabled = interactionEnabled,
                            backdrop = backdrop,
                            onSelectedDockKeyChange = onSelectedDockKeyChange,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .testTag("expanded-dock"),
                        )
                    }
                }
            }
        }
    }

    private fun testTabs(): List<BaGuideBgmDockTab> =
        listOf(
            BaGuideBgmDockTab(BaGuideBgmDockKeys.Home, MiuixIcons.Basic.Check, "Home"),
            BaGuideBgmDockTab(BaGuideBgmDockKeys.Discover, MiuixIcons.Basic.Check, "Discover"),
            BaGuideBgmDockTab(BaGuideBgmDockKeys.Radio, MiuixIcons.Basic.Check, "Radio"),
            BaGuideBgmDockTab(BaGuideBgmDockKeys.Library, MiuixIcons.Basic.Check, "Library"),
        )

    private companion object {
        val tabRoleMatcher = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        val selectedTabMatcher = SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)
    }
}
