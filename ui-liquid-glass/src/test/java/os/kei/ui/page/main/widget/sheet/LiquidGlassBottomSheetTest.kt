package os.kei.ui.page.main.widget.sheet

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.isRoot
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
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val SHEET_TAG = "liquid-sheet"
private const val FIRST_CONTENT_TAG = "liquid-sheet-first-content"
private const val SCROLL_CONTENT_TAG = "liquid-sheet-scroll-content"

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = LiquidGlassBottomSheetTestApp::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class LiquidGlassBottomSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun opensAtAdaptiveHeightForShortSheetContent() {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                LiquidGlassBottomSheet(
                    show = true,
                    modifier = Modifier.testTag(SHEET_TAG),
                    title = "Sheet",
                ) {
                    SheetContentColumn(verticalSpacing = 0.dp) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(96.dp)
                                    .background(Color.Gray),
                        )
                    }
                }
            }
        }

        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()

        val height = sheetHeight()
        assertTrue(
            height < oneThirdRootHeight(),
            "Expected adaptive short sheet below minimum floating height, got $height"
        )
        assertTrue(
            height > 96.dp,
            "Expected adaptive short sheet to include content and chrome, got $height"
        )
    }

    @Test
    fun opensAtAdaptiveHeightForPlainShortContent() {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                LiquidGlassBottomSheet(
                    show = true,
                    modifier = Modifier.testTag(SHEET_TAG),
                    title = "Sheet",
                ) {
                    Column {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(96.dp)
                                    .background(Color.Gray),
                        )
                    }
                }
            }
        }

        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()

        val height = sheetHeight()
        assertTrue(
            height < oneThirdRootHeight(),
            "Expected adaptive plain short sheet below minimum floating height, got $height"
        )
        assertTrue(
            height > 96.dp,
            "Expected adaptive plain short sheet to include content and chrome, got $height"
        )
    }

    @Test
    fun expandsToFullDetentWhenOpeningDetentOverflows() {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                LiquidGlassBottomSheet(
                    show = true,
                    modifier = Modifier.testTag(SHEET_TAG),
                    title = "Sheet",
                ) {
                    SheetContentColumn(verticalSpacing = 0.dp) {
                        repeat(24) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .background(Color.Gray),
                            )
                        }
                    }
                }
            }
        }

        composeRule.mainClock.advanceTimeBy(3_000)
        composeRule.waitForIdle()

        val height = sheetHeight()
        assertTrue(height >= rootHeight() * 0.90f, "Expected full detent height, got $height")
    }

    @Test
    fun expandsToFullDetentWhenPlainContentExceedsOpeningDetent() {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                LiquidGlassBottomSheet(
                    show = true,
                    modifier = Modifier.testTag(SHEET_TAG),
                    title = "Sheet",
                ) {
                    Column {
                        repeat(24) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .background(Color.Gray),
                            )
                        }
                    }
                }
            }
        }

        composeRule.mainClock.advanceTimeBy(3_000)
        composeRule.waitForIdle()

        val height = sheetHeight()
        assertTrue(
            height >= rootHeight() * 0.90f,
            "Expected plain content to expand to full detent, got $height"
        )
    }

    @Test
    fun keepsContentBelowTopChrome() {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                LiquidGlassBottomSheet(
                    show = true,
                    modifier = Modifier.testTag(SHEET_TAG),
                    title = "Actions",
                ) {
                    SheetContentColumn(verticalSpacing = 0.dp) {
                        Box(
                            modifier =
                                Modifier
                                    .testTag(FIRST_CONTENT_TAG)
                                    .fillMaxWidth()
                                    .height(96.dp)
                                    .background(Color.Gray),
                        )
                    }
                }
            }
        }

        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()

        val sheetTop =
            composeRule
                .onNodeWithTag(SHEET_TAG)
                .fetchSemanticsNode()
                .boundsInRoot
                .top
        val contentTop =
            composeRule
                .onNodeWithTag(FIRST_CONTENT_TAG, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
                .top
        val minimumTopChromeHeight = with(composeRule.density) { 44.dp.toPx() }
        val maximumTopChromeHeight = with(composeRule.density) { 72.dp.toPx() }
        val topChromeHeight = contentTop - sheetTop

        assertTrue(
            actual = topChromeHeight in minimumTopChromeHeight..maximumTopChromeHeight,
            message = "Expected sheet content to start below top chrome, sheetTop=$sheetTop contentTop=$contentTop",
        )
    }

    @Test
    fun respectsCustomMaxWidth() {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                LiquidGlassBottomSheet(
                    show = true,
                    modifier = Modifier.testTag(SHEET_TAG),
                    title = "Sheet",
                    sheetMaxWidth = 320.dp,
                ) {
                    SheetContentColumn(verticalSpacing = 0.dp) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(96.dp)
                                    .background(Color.Gray),
                        )
                    }
                }
            }
        }

        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()

        val width = sheetWidth()
        assertTrue(width <= 322.dp, "Expected custom max width to be respected, got $width")
    }

    @Test
    fun topChromeDownwardDragKeepsFreeFloatingPositionWithoutDismiss() {
        var dismissRequests = 0
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                LiquidGlassBottomSheet(
                    show = true,
                    modifier = Modifier.testTag(SHEET_TAG),
                    title = "Sheet",
                    initialDetent = LiquidSheetInitialDetent.Full,
                    onDismissRequest = { dismissRequests++ },
                ) {
                    SheetContentColumn(verticalSpacing = 0.dp) {
                        repeat(24) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .background(Color.Gray),
                            )
                        }
                    }
                }
            }
        }

        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        val offsetBefore = sheetFloatingOffset()
        val heightBefore = sheetHeight()
        assertTrue(
            heightBefore >= rootHeight() * 0.90f,
            "Expected tall sheet before drag, got $heightBefore"
        )
        val dragDistance = rootHeight() * 0.25f

        composeRule.onNodeWithTag(SHEET_TAG).performTouchInput {
            val start = Offset(x = width / 2f, y = 12.dp.toPx())
            down(start)
            moveBy(Offset(x = 0f, y = dragDistance.toPx()))
            up()
        }

        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()

        assertEquals(0, dismissRequests)
        val offsetAfter = sheetFloatingOffset()
        assertTrue(
            offsetAfter > offsetBefore + dragDistance * 0.40f,
            "Expected free floating offset to move down, before=$offsetBefore after=$offsetAfter",
        )
        assertTrue(
            sheetHeight() >= heightBefore - 2.dp,
            "Expected floating sheet to retain content height, got ${sheetHeight()}"
        )
    }

    @Test
    fun contentDownwardDragWhileScrolledDoesNotMoveSheet() {
        var dismissRequests = 0
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                LiquidGlassBottomSheet(
                    show = true,
                    modifier = Modifier.testTag(SHEET_TAG),
                    title = "Sheet",
                    initialDetent = LiquidSheetInitialDetent.Full,
                    onDismissRequest = { dismissRequests++ },
                ) {
                    SheetContentColumn(
                        modifier = Modifier.testTag(SCROLL_CONTENT_TAG),
                        verticalSpacing = 0.dp,
                    ) {
                        repeat(48) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .background(Color.Gray),
                            )
                        }
                    }
                }
            }
        }

        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        val contentScrollDistance = rootHeight() * 0.42f

        composeRule.onNodeWithTag(SCROLL_CONTENT_TAG).performTouchInput {
            val start = Offset(x = width / 2f, y = height * 0.76f)
            down(start)
            moveBy(Offset(x = 0f, y = -contentScrollDistance.toPx()))
            up()
        }

        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        val offsetBefore = sheetFloatingOffset()
        assertTrue(
            offsetBefore < 2.dp,
            "Expected content scrolling to leave sheet fixed, got $offsetBefore"
        )

        val downwardDrag = rootHeight() * 0.72f
        composeRule.onNodeWithTag(SCROLL_CONTENT_TAG).performTouchInput {
            val start = Offset(x = width / 2f, y = height * 0.34f)
            down(start)
            moveBy(Offset(x = 0f, y = downwardDrag.toPx()))
            up()
        }

        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()

        assertEquals(0, dismissRequests)
        val offsetAfter = sheetFloatingOffset()
        assertTrue(
            offsetAfter < 8.dp,
            "Expected downward content drag to stay with content, got $offsetAfter"
        )
    }

    @Test
    fun topChromeUpwardDragRestoresFreeFloatingPosition() {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                LiquidGlassBottomSheet(
                    show = true,
                    modifier = Modifier.testTag(SHEET_TAG),
                    title = "Sheet",
                    initialDetent = LiquidSheetInitialDetent.Full,
                ) {
                    SheetContentColumn(verticalSpacing = 0.dp) {
                        repeat(24) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .background(Color.Gray),
                            )
                        }
                    }
                }
            }
        }

        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        val downwardDrag = rootHeight() * 0.30f
        val upwardDrag = rootHeight() * 0.18f

        composeRule.onNodeWithTag(SHEET_TAG).performTouchInput {
            val start = Offset(x = width / 2f, y = 12.dp.toPx())
            down(start)
            moveBy(Offset(x = 0f, y = downwardDrag.toPx()))
            moveBy(Offset(x = 0f, y = -upwardDrag.toPx()))
            up()
        }

        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()

        val finalMovement = sheetFloatingOffset()
        val downwardOnlyEquivalent = downwardDrag
        assertTrue(
            finalMovement < downwardOnlyEquivalent - upwardDrag * 0.30f,
            "Expected upward drag to restore floating position, movement=$finalMovement",
        )
        assertTrue(
            finalMovement > 0.dp,
            "Expected mixed drag to keep a floating offset, movement=$finalMovement"
        )
    }

    @Test
    fun topChromeDownwardDragClampsAtMinimumFloatingHeightBeforeDismiss() {
        var dismissRequests = 0
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                LiquidGlassBottomSheet(
                    show = true,
                    modifier = Modifier.testTag(SHEET_TAG),
                    title = "Sheet",
                    initialDetent = LiquidSheetInitialDetent.Full,
                    onDismissRequest = { dismissRequests++ },
                ) {
                    SheetContentColumn(verticalSpacing = 0.dp) {
                        repeat(24) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .background(Color.Gray),
                            )
                        }
                    }
                }
            }
        }

        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        val dragDistance = rootHeight() * 0.72f

        composeRule.onNodeWithTag(SHEET_TAG).performTouchInput {
            val start = Offset(x = width / 2f, y = 12.dp.toPx())
            down(start)
            moveBy(Offset(x = 0f, y = dragDistance.toPx()))
            up()
        }

        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()

        assertEquals(0, dismissRequests)
        val visibleHeight = sheetHeight() - sheetFloatingOffset()
        assertNearOneThirdRootHeight(
            visibleHeight,
            "Expected minimum floating height near 1/3, got $visibleHeight"
        )
    }

    @Test
    fun topChromeDownwardDragDismissesBeyondMinimumFloatingHeight() {
        val show = mutableStateOf(true)
        var dismissRequests = 0
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                LiquidGlassBottomSheet(
                    show = show.value,
                    modifier = Modifier.testTag(SHEET_TAG),
                    title = "Sheet",
                    initialDetent = LiquidSheetInitialDetent.Full,
                    onDismissRequest = {
                        dismissRequests++
                        show.value = false
                    },
                ) {
                    SheetContentColumn(verticalSpacing = 0.dp) {
                        repeat(24) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .background(Color.Gray),
                            )
                        }
                    }
                }
            }
        }

        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        val dragDistance = rootHeight() * 1.02f

        composeRule.onNodeWithTag(SHEET_TAG).performTouchInput {
            val start = Offset(x = width / 2f, y = 12.dp.toPx())
            down(start)
            moveBy(Offset(x = 0f, y = dragDistance.toPx()))
            up()
        }

        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()

        assertEquals(1, dismissRequests)
    }

    @Test
    fun blockedDismissBeyondMinimumFloatingHeightCallsBlockedCallbackWithoutClosing() {
        var dismissRequests = 0
        var blockedDismissRequests = 0
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                LiquidGlassBottomSheet(
                    show = true,
                    modifier = Modifier.testTag(SHEET_TAG),
                    title = "Sheet",
                    initialDetent = LiquidSheetInitialDetent.Full,
                    allowDismiss = false,
                    onDismissRequest = { dismissRequests++ },
                    onBlockedDismissRequest = { blockedDismissRequests++ },
                ) {
                    SheetContentColumn(verticalSpacing = 0.dp) {
                        repeat(24) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .background(Color.Gray),
                            )
                        }
                    }
                }
            }
        }

        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        val dragDistance = rootHeight() * 1.02f

        composeRule.onNodeWithTag(SHEET_TAG).performTouchInput {
            val start = Offset(x = width / 2f, y = 12.dp.toPx())
            down(start)
            moveBy(Offset(x = 0f, y = dragDistance.toPx()))
            up()
        }

        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()

        assertEquals(0, dismissRequests)
        assertEquals(1, blockedDismissRequests)
        val visibleHeight = sheetHeight() - sheetFloatingOffset()
        assertNearOneThirdRootHeight(
            visibleHeight,
            "Expected blocked dismiss to keep minimum floating height, got $visibleHeight"
        )
    }

    @Test
    fun adaptiveInitialDetentPromotesOnlyThreeQuarterOverflow() {
        assertEquals(
            LiquidSheetInitialDetent.Full,
            liquidSheetAdaptedInitialDetent(
                initialDetent = LiquidSheetInitialDetent.ThreeQuarter,
                contentOverflowsOpeningDetent = true,
            ),
        )
        assertEquals(
            LiquidSheetInitialDetent.Half,
            liquidSheetAdaptedInitialDetent(
                initialDetent = LiquidSheetInitialDetent.Half,
                contentOverflowsOpeningDetent = true,
            ),
        )
    }

    private fun sheetHeight(): Dp {
        val heightPx =
            composeRule
                .onNodeWithTag(SHEET_TAG)
                .fetchSemanticsNode()
                .boundsInRoot
                .height
        return with(composeRule.density) { heightPx.toDp() }
    }

    private fun sheetWidth(): Dp {
        val widthPx =
            composeRule
                .onNodeWithTag(SHEET_TAG)
                .fetchSemanticsNode()
                .boundsInRoot
                .width
        return with(composeRule.density) { widthPx.toDp() }
    }

    private fun sheetTop(): Dp {
        val topPx =
            composeRule
                .onNodeWithTag(SHEET_TAG)
                .fetchSemanticsNode()
                .boundsInRoot
                .top
        return with(composeRule.density) { topPx.toDp() }
    }

    private fun sheetFloatingOffset(): Dp {
        val offsetPx =
            composeRule
                .onNodeWithTag(SHEET_TAG)
                .fetchSemanticsNode()
                .config[LiquidSheetFloatingOffsetYKey]
        return with(composeRule.density) { offsetPx.toDp() }
    }

    private fun rootHeight(): Dp {
        val heightPx =
            composeRule
                .onAllNodes(isRoot())
                .fetchSemanticsNodes()
                .maxOf { it.boundsInRoot.height }
        return with(composeRule.density) { heightPx.toDp() }
    }

    private fun oneThirdRootHeight(): Dp = rootHeight() / 3f

    private fun assertNearOneThirdRootHeight(
        actual: Dp,
        message: String,
    ) {
        val expected = oneThirdRootHeight()
        assertTrue(
            actual in (expected * 0.86f)..(expected * 1.14f),
            message,
        )
    }
}

class LiquidGlassBottomSheetTestApp : Application()
