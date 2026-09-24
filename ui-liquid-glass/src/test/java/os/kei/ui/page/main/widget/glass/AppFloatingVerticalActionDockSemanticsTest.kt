package os.kei.ui.page.main.widget.glass

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
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
import kotlin.test.assertTrue

/**
 * The dock keeps both forms composed and leaves the hidden one unplaced (keepComposedUnplaced). A form
 * whose component wraps its modifier left that wrapper outside the unplaced node: the compact button's
 * TooltipBox stayed placed with its long-click semantics while the dock was expanded, over the first
 * action. Android's accessibility tree drops a node covered by a later sibling, so TalkBack and
 * uiautomator lost the BA calendar action, and the baseline profile lost the calendar page with it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class AppFloatingVerticalActionDockSemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val hasLongClick = SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick)

    @Test
    fun onlyTheShownFormExposesSemanticsOverTheActions() {
        val compact = mutableStateOf(false)
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                AppFloatingVerticalActionDock(
                    backdrop = null,
                    actions =
                        TAGS.mapIndexed { index, tag ->
                            AppFloatingDockAction(
                                icon = MiuixIcons.Basic.Check,
                                contentDescription = "action $tag",
                                iconTint = Color.Black,
                                testTag = tag,
                                badgeLabel = if (index == 0) "14" else null,
                                onClick = {},
                            )
                        },
                    compact = compact.value,
                    compactIcon = MiuixIcons.Basic.Check,
                    compactContentDescription = "Expand",
                )
            }
        }

        // The merged tree, as accessibility sees it: a cleared subtree is gone, a sibling left outside it is not.
        // Expanded: the three actions' tooltips, and nothing from the hidden compact button over them.
        val longClickNodes = composeRule.onAllNodes(hasLongClick).fetchSemanticsNodes()
        assertEquals(TAGS.size, longClickNodes.size, "only the expanded actions may carry a long-click node")
        val actionBounds = TAGS.map { composeRule.onNodeWithTag(it, useUnmergedTree = true).getUnclippedBoundsInRoot() }
        actionBounds.forEachIndexed { index, bounds ->
            actionBounds.forEachIndexed { other, otherBounds ->
                if (index != other) assertTrue(!bounds.overlaps(otherBounds), "actions $index and $other overlap")
            }
        }

        // Collapsed: only the compact button.
        compact.value = true
        composeRule.waitForIdle()
        assertEquals(
            1,
            composeRule.onAllNodes(hasLongClick).fetchSemanticsNodes().size,
            "only the compact button may carry a long-click node",
        )
        TAGS.forEach { tag ->
            composeRule.onNodeWithTag(tag).assertDoesNotExist()
        }
    }

    private fun DpRect.overlaps(other: DpRect): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    private companion object {
        val TAGS = listOf("dock_calendar", "dock_catalog", "dock_daily")
    }
}
