package os.kei.ui.page.main.widget.chrome

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import top.yukonga.miuix.kmp.basic.Icon
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
class AnimatedCompactBottomBarTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun expansionMidpointAssignsInteractionToExpandedTarget() {
        val expanded = mutableStateOf(false)
        var expandedClicks = 0
        var compactClicks = 0
        setAnimatedDock(
            expanded = expanded,
            onExpandedClick = { expandedClicks++ },
            onCompactClick = { compactClicks++ },
        )

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { expanded.value = true }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(TRANSITION_MIDPOINT_MILLIS)

        assertTargetInteraction(
            targetLabel = EXPANDED_DOCK_LABEL,
            outgoingLabel = COMPACT_DOCK_LABEL,
        )
        composeRule.onNodeWithContentDescription(EXPANDED_DOCK_LABEL).performClick()

        composeRule.runOnIdle {
            assertEquals(1 to 0, expandedClicks to compactClicks)
        }
        finishTransition()
    }

    @Test
    fun collapseMidpointAssignsInteractionToCompactTarget() {
        val expanded = mutableStateOf(true)
        var expandedClicks = 0
        var compactClicks = 0
        setAnimatedDock(
            expanded = expanded,
            onExpandedClick = { expandedClicks++ },
            onCompactClick = { compactClicks++ },
        )

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { expanded.value = false }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(TRANSITION_MIDPOINT_MILLIS)

        assertTargetInteraction(
            targetLabel = COMPACT_DOCK_LABEL,
            outgoingLabel = EXPANDED_DOCK_LABEL,
        )
        composeRule.onNodeWithContentDescription(COMPACT_DOCK_LABEL).performClick()

        composeRule.runOnIdle {
            assertEquals(0 to 1, expandedClicks to compactClicks)
        }
        finishTransition()
    }

    private fun setAnimatedDock(
        expanded: MutableState<Boolean>,
        onExpandedClick: () -> Unit,
        onCompactClick: () -> Unit,
    ) {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                AnimatedCompactBottomBar(
                    expanded = expanded.value,
                    expandedContent = { motionModifier, interactionEnabled ->
                        CompactBottomBarDock(
                            backdrop = null,
                            onClick = onExpandedClick,
                            enabled = interactionEnabled,
                            modifier =
                                motionModifier
                                    .align(Alignment.BottomStart),
                        ) {
                            Box(Modifier.semantics { contentDescription = EXPANDED_DOCK_LABEL }) {
                                Icon(
                                    imageVector = MiuixIcons.Basic.Check,
                                    contentDescription = null,
                                )
                            }
                        }
                    },
                    compactContent = { motionModifier, interactionEnabled ->
                        CompactBottomBarDock(
                            backdrop = null,
                            onClick = onCompactClick,
                            enabled = interactionEnabled,
                            modifier =
                                motionModifier
                                    .align(Alignment.BottomStart),
                        ) {
                            Box(Modifier.semantics { contentDescription = COMPACT_DOCK_LABEL }) {
                                Icon(
                                    imageVector = MiuixIcons.Basic.Check,
                                    contentDescription = null,
                                )
                            }
                        }
                    },
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun assertTargetInteraction(
        targetLabel: String,
        outgoingLabel: String,
    ) {
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(1)
        val targetCount = composeRule.onAllNodesWithContentDescription(targetLabel).fetchSemanticsNodes().size
        val outgoingCount = composeRule.onAllNodesWithContentDescription(outgoingLabel).fetchSemanticsNodes().size
        assertEquals(1 to 0, targetCount to outgoingCount)
    }

    private fun finishTransition() {
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
    }
}

private const val TRANSITION_MIDPOINT_MILLIS = 120L
private const val EXPANDED_DOCK_LABEL = "Expanded dock"
private const val COMPACT_DOCK_LABEL = "Compact dock"
