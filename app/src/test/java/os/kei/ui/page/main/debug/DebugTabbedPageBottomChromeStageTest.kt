package os.kei.ui.page.main.debug

import android.app.Application
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import os.kei.R
import os.kei.ui.page.main.widget.chrome.LocalSearchAutoFocusEnabled
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
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
    qualifiers = "w360dp-h800dp-xxhdpi",
)
class DebugTabbedPageBottomChromeStageTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun largeFontStageKeepsThreeTabsAndSearchInsideThe360DpViewport() {
        setStage()
        val viewport =
            composeRule
                .onNodeWithTag(DEBUG_TABBED_CHROME_VIEWPORT_TAG)
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        val overlay =
            composeRule
                .onNodeWithTag(DEBUG_TABBED_CHROME_OVERLAY_TAG)
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot

        assertEquals(360.dp, with(composeRule.density) { viewport.width.toDp() })
        assertInside(viewport, overlay, tolerance = 1f)
        composeRule.onAllNodes(tabRoleMatcher).assertCountEquals(3)
        composeRule.onAllNodes(tabRoleMatcher).fetchSemanticsNodes().forEach { node ->
            with(composeRule.density) {
                assertTrue(node.boundsInRoot.height.toDp() >= 48.dp)
                assertTrue(node.boundsInRoot.width.toDp() >= 48.dp)
            }
        }
        composeRule
            .onNodeWithContentDescription(searchDescription())
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
    }

    @Test
    fun fiveTabsCompactModeInsetAndSearchRemainInteractiveAtLargeFont() {
        setStage()
        composeRule.onNodeWithTag(DEBUG_TABBED_CHROME_TAB_COUNT_TAG).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(tabRoleMatcher).fetchSemanticsNodes().size == 5
        }
        composeRule.onAllNodes(tabRoleMatcher).fetchSemanticsNodes().forEach { node ->
            with(composeRule.density) {
                assertTrue(node.boundsInRoot.height.toDp() >= 48.dp)
                assertTrue(node.boundsInRoot.width.toDp() >= 48.dp)
            }
        }
        composeRule.onAllNodes(tabRoleMatcher)[4].performClick()
        composeRule
            .onAllNodes(tabRoleMatcher and selectedMatcher)
            .assertCountEquals(1)

        composeRule.onNodeWithTag(DEBUG_TABBED_CHROME_DOCK_MODE_TAG).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(tabRoleMatcher).fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag(DEBUG_TABBED_CHROME_DOCK_MODE_TAG).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(tabRoleMatcher).fetchSemanticsNodes().size == 5
        }

        val baselineSearchBottom =
            composeRule
                .onNodeWithContentDescription(searchDescription())
                .fetchSemanticsNode()
                .boundsInRoot
                .bottom
        composeRule.onNodeWithTag(DEBUG_TABBED_CHROME_INSET_TAG).performClick()
        composeRule.waitForIdle()
        val viewport =
            composeRule.onNodeWithTag(DEBUG_TABBED_CHROME_VIEWPORT_TAG).fetchSemanticsNode().boundsInRoot
        val insetOverlay =
            composeRule.onNodeWithTag(DEBUG_TABBED_CHROME_OVERLAY_TAG).fetchSemanticsNode().boundsInRoot
        val insetSearchBottom =
            composeRule
                .onNodeWithContentDescription(searchDescription())
                .fetchSemanticsNode()
                .boundsInRoot
                .bottom
        assertInside(viewport, insetOverlay, tolerance = 1f)
        assertEquals(
            24.dp,
            with(composeRule.density) { (baselineSearchBottom - insetSearchBottom).toDp() },
        )

        composeRule.onNodeWithContentDescription(searchDescription()).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size == 1
        }
        val searchField = composeRule.onNode(hasSetTextAction())
        searchField.assertIsDisplayed()
        searchField.performTextInput("Aru")
        searchField.assertTextContains("Aru")
        composeRule.onAllNodes(tabRoleMatcher).assertCountEquals(0)
    }

    @Test
    fun backClosesStageSearchBeforeTheFullscreenHost() {
        var hostBackCount = 0
        lateinit var backDispatcher: OnBackPressedDispatcher
        setStage(
            onHostBack = { hostBackCount++ },
            onBackDispatcher = { backDispatcher = it },
        )

        composeRule.onNodeWithContentDescription(searchDescription()).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size == 1
        }

        composeRule.runOnIdle { backDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        assertEquals(0, hostBackCount)

        composeRule.runOnIdle { backDispatcher.onBackPressed() }
        assertEquals(1, hostBackCount)
    }

    private fun setStage(
        onHostBack: (() -> Unit)? = null,
        onBackDispatcher: ((OnBackPressedDispatcher) -> Unit)? = null,
    ) {
        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(baseDensity.density, fontScale = 1.5f),
                LocalTransitionAnimationsEnabled provides false,
                LocalSearchAutoFocusEnabled provides false,
            ) {
                val dispatcherOwner = checkNotNull(LocalOnBackPressedDispatcherOwner.current)
                SideEffect { onBackDispatcher?.invoke(dispatcherOwner.onBackPressedDispatcher) }
                if (onHostBack != null) {
                    BackHandler(onBack = onHostBack)
                }
                MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        DebugTabbedPageBottomChromeStage(
                            accent = MiuixTheme.colorScheme.primary,
                            onClose = {},
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }

    private fun searchDescription(): String =
        ApplicationProvider
            .getApplicationContext<Application>()
            .getString(R.string.debug_component_lab_search_placeholder)

    private companion object {
        val tabRoleMatcher = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        val selectedMatcher = SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)
    }
}

private fun assertInside(
    outer: Rect,
    inner: Rect,
    tolerance: Float,
) {
    assertTrue(inner.left >= outer.left - tolerance, "Left edge escaped: outer=$outer, inner=$inner")
    assertTrue(inner.top >= outer.top - tolerance, "Top edge escaped: outer=$outer, inner=$inner")
    assertTrue(inner.right <= outer.right + tolerance, "Right edge escaped: outer=$outer, inner=$inner")
    assertTrue(inner.bottom <= outer.bottom + tolerance, "Bottom edge escaped: outer=$outer, inner=$inner")
}
