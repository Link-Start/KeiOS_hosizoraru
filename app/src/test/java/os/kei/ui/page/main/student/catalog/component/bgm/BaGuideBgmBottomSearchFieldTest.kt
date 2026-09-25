package os.kei.ui.page.main.student.catalog.component.bgm

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import os.kei.R
import os.kei.ui.page.main.widget.glass.AppLiquidFloatingSurface
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = BaGuideBgmBottomSearchFieldTestApp::class,
    sdk = [35],
    qualifiers = "w360dp-h800dp-xxhdpi",
)
class BaGuideBgmBottomSearchFieldTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun largeFontKeepsSingleEditableFieldAndCompactProductionGeometry() {
        setSearchField(placeholder = TEST_PLACEHOLDER)
        val context = ApplicationProvider.getApplicationContext<Application>()

        composeRule
            .onAllNodes(hasSetTextAction(), useUnmergedTree = true)
            .assertCountEquals(1)
        composeRule
            .onNodeWithTag(SURFACE_TAG)
            .assertWidthIsEqualTo(328.dp)
            .assertHeightIsEqualTo(62.dp)
        composeRule
            .onNodeWithTag(SEARCH_ROOT_TAG, useUnmergedTree = true)
            .assertWidthIsEqualTo(328.dp)
            .assertHeightIsEqualTo(62.dp)

        val surfaceBounds = composeRule.onNodeWithTag(SURFACE_TAG).fetchSemanticsNode().boundsInRoot
        val rootBounds =
            composeRule
                .onNodeWithTag(SEARCH_ROOT_TAG, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        val iconBounds =
            composeRule
                .onNodeWithContentDescription(
                    context.getString(R.string.ba_catalog_bgm_nav_search),
                    useUnmergedTree = true,
                ).fetchSemanticsNode()
                .boundsInRoot
        val field = composeRule.onNode(hasSetTextAction(), useUnmergedTree = true)
        val fieldBounds = field.fetchSemanticsNode().boundsInRoot
        val placeholderBounds =
            composeRule
                .onNodeWithText(TEST_PLACEHOLDER, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot

        field.assertHeightIsAtLeast(24.dp)
        assertDpDistance(rootBounds.left - surfaceBounds.left, 0.dp)
        assertDpDistance(rootBounds.top - surfaceBounds.top, 0.dp)
        assertDpDistance(surfaceBounds.right - rootBounds.right, 0.dp)
        assertDpDistance(surfaceBounds.bottom - rootBounds.bottom, 0.dp)
        assertDpDistance(iconBounds.left - surfaceBounds.left, 18.dp)
        assertDpDistance(fieldBounds.left - iconBounds.right, 12.dp)
        assertDpDistance(surfaceBounds.right - fieldBounds.right, 18.dp)
        assertTrue(fieldBounds.width > 0f)
        assertTrue(fieldBounds.top >= surfaceBounds.top)
        assertTrue(fieldBounds.bottom <= surfaceBounds.bottom)
        assertTrue(placeholderBounds.left >= fieldBounds.left)
        assertTrue(placeholderBounds.top >= surfaceBounds.top)
        assertTrue(placeholderBounds.right <= surfaceBounds.right)
        assertTrue(placeholderBounds.bottom <= surfaceBounds.bottom)
    }

    @Test
    fun wholeSearchContentRequestsFocusAndSearchImeKeepsTheEditedQuery() {
        val harness = setSearchField(initialQuery = "initial")
        val field = composeRule.onNode(hasSetTextAction(), useUnmergedTree = true)

        composeRule
            .onNodeWithTag(SEARCH_ROOT_TAG, useUnmergedTree = true)
            .performClick()
        field.assertIsFocused()
        field.performTextReplacement("星野")
        field.performImeAction()

        composeRule.runOnIdle {
            assertEquals("星野", harness.query.value)
            assertTrue(true in harness.focusEvents)
            assertFalse(harness.focusEvents.last())
        }
        field.assertIsNotFocused()
    }

    @Test
    fun collapsingTheSearchContentRemovesEditableSemantics() {
        val harness = setSearchField()

        composeRule
            .onAllNodes(hasSetTextAction(), useUnmergedTree = true)
            .assertCountEquals(1)

        composeRule.runOnIdle { harness.visible.value = false }

        composeRule
            .onAllNodes(hasSetTextAction(), useUnmergedTree = true)
            .assertCountEquals(0)
    }

    private fun setSearchField(
        initialQuery: String = "",
        placeholder: String = TEST_PLACEHOLDER,
    ): SearchFieldHarness {
        val harness = SearchFieldHarness()
        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(baseDensity.density, fontScale = 1.5f),
            ) {
                MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                    val query = remember { mutableStateOf(initialQuery) }
                    val visible = remember { mutableStateOf(true) }
                    val focusRequester = remember { FocusRequester() }
                    harness.query = query
                    harness.visible = visible
                    Box(
                        modifier =
                            Modifier
                                .size(width = 328.dp, height = 62.dp)
                                .testTag(SURFACE_TAG),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppLiquidFloatingSurface(
                            modifier = Modifier.fillMaxSize(),
                            backdrop = null,
                        ) {
                            if (visible.value) {
                                BaGuideBgmBottomSearchField(
                                    query = query.value,
                                    placeholder = placeholder,
                                    onQueryChange = { query.value = it },
                                    focusRequester = focusRequester,
                                    onFocusActiveChange = harness.focusEvents::add,
                                    accent = Color(0xFF3B82F6),
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .testTag(SEARCH_ROOT_TAG),
                                )
                            }
                        }
                    }
                }
            }
        }
        return harness
    }

    private fun assertDpDistance(
        actualPx: Float,
        expected: Dp,
    ) {
        val actual = with(composeRule.density) { actualPx.toDp() }
        assertTrue(
            abs(actual.value - expected.value) <= 0.75f,
            "Expected $expected, got $actual",
        )
    }
}

private class SearchFieldHarness {
    lateinit var query: MutableState<String>
    lateinit var visible: MutableState<Boolean>
    val focusEvents = mutableListOf<Boolean>()
}

private const val SURFACE_TAG = "ba-bgm-bottom-search-surface"
private const val SEARCH_ROOT_TAG = "ba-bgm-bottom-search-root"
private const val TEST_PLACEHOLDER = "Search music or students"
class BaGuideBgmBottomSearchFieldTestApp : Application()
