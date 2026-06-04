package os.kei.ui.page.main.widget.sheet

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
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
            LiquidSheetTestTheme {
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
            LiquidSheetTestTheme {
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
    fun boundsManagedScrollableOverflowAtOpeningDetent() {
        composeRule.setContent {
            LiquidSheetTestTheme {
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
        assertTrue(
            height in (rootHeight() * 0.66f)..(rootHeight() * 0.82f),
            "Expected managed scrollable content to stay near opening detent, got $height"
        )
    }

    @Test
    fun expandsToFullDetentWhenPlainContentExceedsOpeningDetent() {
        composeRule.setContent {
            LiquidSheetTestTheme {
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
            LiquidSheetTestTheme {
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
            LiquidSheetTestTheme {
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
    fun topChromeDownwardDragReducesLengthAndKeepsBottomAnchored() {
        var dismissRequests = 0
        composeRule.setContent {
            LiquidSheetTestTheme {
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
        val heightBefore = sheetHeight()
        val bottomBefore = sheetBottom()
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
        val heightAfter = sheetHeight()
        assertTrue(
            heightAfter < heightBefore - dragDistance * 0.40f,
            "Expected top chrome drag to reduce sheet length, before=$heightBefore after=$heightAfter",
        )
        assertDpNear(
            actual = sheetBottom(),
            expected = bottomBefore,
            tolerance = 4.dp,
            message = "Expected sheet bottom to stay anchored while resizing",
        )
    }

    @Test
    fun contentDownwardDragWhileScrolledDoesNotMoveSheet() {
        var dismissRequests = 0
        composeRule.setContent {
            LiquidSheetTestTheme {
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
        val heightBefore = sheetHeight()
        val topBefore = sheetTop()

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
        assertDpNear(
            actual = sheetHeight(),
            expected = heightBefore,
            tolerance = 8.dp,
            message = "Expected downward content drag to stay with scrolled content",
        )
        assertDpNear(
            actual = sheetTop(),
            expected = topBefore,
            tolerance = 8.dp,
            message = "Expected sheet top to stay fixed while scrolled content consumes drag",
        )
    }

    @Test
    fun topChromeMixedDragResizesFromCurrentLength() {
        composeRule.setContent {
            LiquidSheetTestTheme {
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
        val heightBefore = sheetHeight()
        val bottomBefore = sheetBottom()
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

        val heightAfter = sheetHeight()
        assertTrue(
            heightAfter < heightBefore,
            "Expected mixed drag to leave sheet shorter than initial height, before=$heightBefore after=$heightAfter",
        )
        assertTrue(
            heightAfter > heightBefore - downwardDrag + upwardDrag * 0.45f,
            "Expected upward drag to restore sheet length within the same gesture, after=$heightAfter",
        )
        assertDpNear(
            actual = sheetBottom(),
            expected = bottomBefore,
            tolerance = 4.dp,
            message = "Expected mixed resize drag to keep sheet bottom anchored",
        )
    }

    @Test
    fun topChromeUpwardDragExpandsLengthTowardSafeTop() {
        composeRule.setContent {
            LiquidSheetTestTheme {
                LiquidGlassBottomSheet(
                    show = true,
                    modifier = Modifier.testTag(SHEET_TAG),
                    title = "Sheet",
                    initialDetent = LiquidSheetInitialDetent.Half,
                ) {
                    SheetContentColumn(verticalSpacing = 0.dp) {
                        repeat(6) {
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
        val topBefore = sheetTop()
        val heightBefore = sheetHeight()
        val bottomBefore = sheetBottom()

        composeRule.onNodeWithTag(SHEET_TAG).performTouchInput {
            val start = Offset(x = width / 2f, y = 12.dp.toPx())
            down(start)
            moveBy(Offset(x = 0f, y = -(rootHeight() * 0.80f).toPx()))
            up()
        }

        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()

        val topAfter = sheetTop()
        assertTrue(
            topBefore > rootHeight() * 0.30f,
            "Expected sheet to start away from safe top, got $topBefore",
        )
        assertTrue(
            topAfter < topBefore - rootHeight() * 0.20f,
            "Expected upward drag to expand sheet length toward safe top, before=$topBefore after=$topAfter",
        )
        assertTrue(
            sheetHeight() > heightBefore + rootHeight() * 0.20f,
            "Expected upward drag to increase sheet height, before=$heightBefore after=${sheetHeight()}",
        )
        assertDpNear(
            actual = sheetBottom(),
            expected = bottomBefore,
            tolerance = 4.dp,
            message = "Expected expanded sheet to keep bottom anchored",
        )
    }

    @Test
    fun maxVisibleHeightLeavesSafeTopInset() {
        assertEquals(
            1_040f,
            liquidSheetMaxVisibleHeightPx(
                windowHeightPx = 1_120f,
                topInsetPx = 80f,
            ),
        )
        assertEquals(
            1_024f,
            liquidSheetMaxVisibleHeightPx(
                windowHeightPx = 1_120f,
                topInsetPx = 96f,
            ),
        )
    }

    @Test
    fun backgroundBlurLayerExtendsBehindTopCorners() {
        assertEquals(
            504f,
            liquidSheetBackgroundBlurLayerHeightPx(
                sheetTopOffsetPx = 420f,
                cornerRadiusPx = 84f,
                windowHeightPx = 1_120f,
            ),
        )
        assertEquals(
            1_120f,
            liquidSheetBackgroundBlurLayerHeightPx(
                sheetTopOffsetPx = 1_080f,
                cornerRadiusPx = 84f,
                windowHeightPx = 1_120f,
            ),
        )
    }

    @Test
    fun topChromeDownwardDragClampsAtMinimumFloatingHeightBeforeDismiss() {
        var dismissRequests = 0
        composeRule.setContent {
            LiquidSheetTestTheme {
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
        val heightBefore = sheetHeight()
        val oneThirdHeight = oneThirdRootHeight()
        val dragDistance =
            if (heightBefore > oneThirdHeight) {
                heightBefore - oneThirdHeight + 24.dp
            } else {
                24.dp
            }

        composeRule.onNodeWithTag(SHEET_TAG).performTouchInput {
            val start = Offset(x = width / 2f, y = 12.dp.toPx())
            down(start)
            moveBy(Offset(x = 0f, y = dragDistance.toPx()))
            up()
        }

        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()

        assertEquals(0, dismissRequests)
        assertNearOneThirdRootHeight(
            sheetHeight(),
            "Expected minimum sheet height near 1/3, got ${sheetHeight()}"
        )
    }

    @Test
    fun topChromeDownwardDragDismissesBeyondMinimumFloatingHeight() {
        val show = mutableStateOf(true)
        var dismissRequests = 0
        composeRule.setContent {
            LiquidSheetTestTheme {
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
        val heightBefore = sheetHeight()
        val oneThirdHeight = oneThirdRootHeight()
        val dragDistance =
            if (heightBefore > oneThirdHeight) {
                heightBefore - oneThirdHeight + 128.dp
            } else {
                rootHeight() * 0.40f
            }

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
            LiquidSheetTestTheme {
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
        val heightBefore = sheetHeight()
        val oneThirdHeight = oneThirdRootHeight()
        val dragDistance =
            if (heightBefore > oneThirdHeight) {
                heightBefore - oneThirdHeight + 128.dp
            } else {
                rootHeight() * 0.40f
            }

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
        assertNearOneThirdRootHeight(
            sheetHeight(),
            "Expected blocked dismiss to keep minimum sheet height, got ${sheetHeight()}"
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

    private fun sheetBottom(): Dp {
        val bottomPx =
            composeRule
                .onNodeWithTag(SHEET_TAG)
                .fetchSemanticsNode()
                .boundsInRoot
                .bottom
        return with(composeRule.density) { bottomPx.toDp() }
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

    private fun assertDpNear(
        actual: Dp,
        expected: Dp,
        tolerance: Dp,
        message: String,
    ) {
        assertTrue(
            actual in (expected - tolerance)..(expected + tolerance),
            "$message, expected=$expected actual=$actual tolerance=$tolerance",
        )
    }
}

@Composable
private fun LiquidSheetTestTheme(content: @Composable () -> Unit) {
    MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
        content()
    }
}

class LiquidGlassBottomSheetTestApp : Application()
