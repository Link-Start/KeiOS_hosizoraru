package os.kei.ui.page.main.widget.sheet

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import kotlin.test.assertTrue

private const val SheetTag = "liquid-sheet"
private const val FirstContentTag = "liquid-sheet-first-content"

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = LiquidGlassBottomSheetTestApp::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi"
)
class LiquidGlassBottomSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun opensAtThreeQuarterDetentForShortContent() {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                LiquidGlassBottomSheet(
                    show = true,
                    modifier = Modifier.testTag(SheetTag),
                    title = "Sheet"
                ) {
                    SheetContentColumn(verticalSpacing = 0.dp) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(96.dp)
                                .background(Color.Gray)
                        )
                    }
                }
            }
        }

        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()

        val height = sheetHeight()
        assertTrue(height in 600.dp..720.dp, "Expected 3/4 detent height, got $height")
    }

    @Test
    fun expandsToFullDetentWhenOpeningDetentOverflows() {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                LiquidGlassBottomSheet(
                    show = true,
                    modifier = Modifier.testTag(SheetTag),
                    title = "Sheet"
                ) {
                    SheetContentColumn(verticalSpacing = 0.dp) {
                        repeat(24) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .background(Color.Gray)
                            )
                        }
                    }
                }
            }
        }

        composeRule.mainClock.advanceTimeBy(3_000)
        composeRule.waitForIdle()

        val height = sheetHeight()
        assertTrue(height >= 820.dp, "Expected full detent height, got $height")
    }

    @Test
    fun keepsContentBelowTopChrome() {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                LiquidGlassBottomSheet(
                    show = true,
                    modifier = Modifier.testTag(SheetTag),
                    title = "Actions"
                ) {
                    SheetContentColumn(verticalSpacing = 0.dp) {
                        Box(
                            modifier = Modifier
                                .testTag(FirstContentTag)
                                .fillMaxWidth()
                                .height(96.dp)
                                .background(Color.Gray)
                        )
                    }
                }
            }
        }

        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()

        val sheetTop = composeRule.onNodeWithTag(SheetTag)
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        val contentTop = composeRule.onNodeWithTag(FirstContentTag, useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        val minimumTopChromeHeight = with(composeRule.density) { 44.dp.toPx() }
        val maximumTopChromeHeight = with(composeRule.density) { 72.dp.toPx() }
        val topChromeHeight = contentTop - sheetTop

        assertTrue(
            actual = topChromeHeight in minimumTopChromeHeight..maximumTopChromeHeight,
            message = "Expected sheet content to start below top chrome, sheetTop=$sheetTop contentTop=$contentTop"
        )
    }

    @Test
    fun predictiveBackMovesDismissibleSheetTowardOffscreenOffset() {
        val halfwayOffset = liquidSheetPredictiveBackOffsetFraction(
            sheetFraction = 0.75f,
            progress = 0.5f,
            allowDismiss = true
        )
        val completedOffset = liquidSheetPredictiveBackOffsetFraction(
            sheetFraction = 0.75f,
            progress = 1f,
            allowDismiss = true
        )
        val completedScrim = liquidSheetPredictiveBackScrimFactor(
            sheetFraction = 0.75f,
            offsetFraction = completedOffset,
            allowDismiss = true
        )

        assertTrue(halfwayOffset in 0f..0.75f)
        assertTrue(completedOffset >= 0.74f)
        assertTrue(completedScrim <= 0.01f)
    }

    @Test
    fun predictiveBackKeepsBlockedSheetNearCurrentPosition() {
        val completedOffset = liquidSheetPredictiveBackOffsetFraction(
            sheetFraction = 0.75f,
            progress = 1f,
            allowDismiss = false
        )
        val completedScrim = liquidSheetPredictiveBackScrimFactor(
            sheetFraction = 0.75f,
            offsetFraction = completedOffset,
            allowDismiss = false
        )

        assertTrue(completedOffset in 0.07f..0.08f)
        assertTrue(completedScrim >= 0.99f)
    }

    private fun sheetHeight(): Dp {
        val heightPx = composeRule.onNodeWithTag(SheetTag)
            .fetchSemanticsNode()
            .boundsInRoot
            .height
        return with(composeRule.density) { heightPx.toDp() }
    }
}

class LiquidGlassBottomSheetTestApp : Application()
