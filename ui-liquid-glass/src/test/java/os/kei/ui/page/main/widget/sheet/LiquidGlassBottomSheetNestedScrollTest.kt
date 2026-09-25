@file:Suppress("FunctionName")

package os.kei.ui.page.main.widget.sheet

import android.app.Application
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val NESTED_SHEET_TAG = "nested-liquid-sheet"
private const val NESTED_CONTENT_TAG = "nested-liquid-sheet-content"

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = Application::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class LiquidGlassBottomSheetNestedScrollTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun contentReachingTopHandsRemainingDownwardDragToSheetInSameGesture() {
        var dismissRequests = 0
        composeRule.setContent {
            LiquidSheetTestTheme {
                LiquidGlassBottomSheet(
                    show = true,
                    modifier = Modifier.testTag(NESTED_SHEET_TAG),
                    title = "Sheet",
                    initialDetent = LiquidSheetInitialDetent.Full,
                    onDismissRequest = { dismissRequests++ },
                ) {
                    SheetContentColumn(
                        modifier = Modifier.testTag(NESTED_CONTENT_TAG),
                        verticalSpacing = 0.dp,
                    ) {
                        GrayRows(count = 48, height = 56.dp)
                    }
                }
            }
        }

        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        val rootHeight = composeRule.rootHeight()

        composeRule.onNodeWithTag(NESTED_CONTENT_TAG).performTouchInput {
            val start = Offset(x = width / 2f, y = height * 0.76f)
            down(start)
            moveBy(Offset(x = 0f, y = -(rootHeight * 0.22f).toPx()))
            up()
        }

        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        val heightBefore = sheetHeight()

        composeRule.onNodeWithTag(NESTED_CONTENT_TAG).performTouchInput {
            val start = Offset(x = width / 2f, y = height * 0.30f)
            down(start)
            moveBy(Offset(x = 0f, y = (rootHeight * 0.52f).toPx()))
            up()
        }

        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()

        val heightAfter = sheetHeight()
        assertEquals(0, dismissRequests)
        assertTrue(
            heightAfter < heightBefore - rootHeight * 0.12f,
            "Expected the unconsumed drag remainder to resize the sheet, before=$heightBefore after=$heightAfter",
        )
    }

    private fun sheetHeight(): Dp = composeRule.nodeBounds(NESTED_SHEET_TAG).let { it.bottom - it.top }
}

