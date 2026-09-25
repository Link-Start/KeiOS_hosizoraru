package os.kei.ui.page.main.widget.core

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import os.kei.ui.testing.boundsOf
import os.kei.ui.testing.distinctRowCount
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = Application::class,
    sdk = [35],
    qualifiers = "zh-rCN-w375dp-h817dp-520dpi",
)
class AppOverviewPillLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun osOverviewPillsStayOnOneRowAtPhysicalDeviceWidth() {
        val labels = listOf("Top 2/22", "参数 1/7", "活动 9/9", "Shell 4/4")

        setPills(labels)

        assertEquals(1, composeRule.distinctRowCount(labels))
    }

    @Test
    fun longOverviewPillRemainsSingleLineHeight() {
        val label = "a-very-long-custom-service-name-that-must-be-ellipsized"

        setPills(listOf(label))

        val bounds = composeRule.boundsOf(label)
        val maxHeightPx = with(composeRule.density) { 28.dp.toPx() }
        assertTrue(
            actual = bounds.height <= maxHeightPx,
            message = "Expected a single-line 28dp pill, bounds=$bounds",
        )
    }

    @Test
    fun osTopPillSharesTheTitleRowWhileOtherMetricsStayBelow() {
        val title = "OS 总览"
        val topPill = AppOverviewPill(label = "Top 2/22", color = Color(0xFF2563EB))
        val bodyLabels = listOf("参数 1/7", "活动 9/9", "Shell 4/4")

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    AppOverviewCard(
                        title = title,
                        titleAccessory = {
                            AppOverviewPillItem(pill = topPill)
                        },
                    ) {
                        AppOverviewPillFlow(
                            pills =
                                bodyLabels.map { label ->
                                    AppOverviewPill(label = label, color = Color(0xFF2563EB))
                                },
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        val titleBounds = composeRule.boundsOf(title)
        val topPillBounds = composeRule.boundsOf(topPill.label)
        val firstBodyPillBounds = composeRule.boundsOf(bodyLabels.first())
        val sameRowTolerancePx = with(composeRule.density) { 2.dp.toPx() }

        assertTrue(
            actual = abs(titleBounds.center.y - topPillBounds.center.y) <= sameRowTolerancePx,
            message = "Expected title and Top pill to share a baseline row: title=$titleBounds, top=$topPillBounds",
        )
        assertTrue(
            actual = firstBodyPillBounds.top > titleBounds.bottom,
            message = "Expected the remaining metrics below the title row: title=$titleBounds, body=$firstBodyPillBounds",
        )
    }

    private fun setPills(labels: List<String>) {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    AppOverviewPillFlow(
                        pills = labels.map { label ->
                            AppOverviewPill(label = label, color = Color(0xFF2563EB))
                        },
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }
}
