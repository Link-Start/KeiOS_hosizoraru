@file:Suppress("FunctionName")

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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kyant.backdrop.Backdrop
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import os.kei.ui.page.main.widget.glass.LocalLiquidParentBackdrop
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
            "Expected adaptive short sheet below minimum floating height, got $height",
        )
        assertTrue(
            height > 96.dp,
            "Expected adaptive short sheet to include content and chrome, got $height",
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
            "Expected adaptive plain short sheet below minimum floating height, got $height",
        )
        assertTrue(
            height > 96.dp,
            "Expected adaptive plain short sheet to include content and chrome, got $height",
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
                        GrayRows(count = 24, height = 48.dp)
                    }
                }
            }
        }

        composeRule.mainClock.advanceTimeBy(3_000)
        composeRule.waitForIdle()

        val height = sheetHeight()
        assertTrue(
            height in (composeRule.rootHeight() * 0.66f)..(composeRule.rootHeight() * 0.82f),
            "Expected managed scrollable content to stay near opening detent, got $height",
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
                        GrayRows(count = 24, height = 48.dp)
                    }
                }
            }
        }

        composeRule.mainClock.advanceTimeBy(3_000)
        composeRule.waitForIdle()

        val height = sheetHeight()
        assertTrue(
            height >= composeRule.rootHeight() * 0.90f,
            "Expected plain content to expand to full detent, got $height",
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
    fun clearsInheritedBackdropFromChromeAndContent() {
        var startBackdrop: Backdrop? = null
        var endBackdrop: Backdrop? = null
        var contentBackdrop: Backdrop? = null

        composeRule.setContent {
            LiquidSheetTestTheme {
                LiquidGlassBottomSheet(
                    show = true,
                    title = "Backdrop",
                    startAction = {
                        startBackdrop = LocalLiquidParentBackdrop.current
                        Box(modifier = Modifier.testTag("sheet-start-action"))
                    },
                    endAction = {
                        endBackdrop = LocalLiquidParentBackdrop.current
                        Box(modifier = Modifier.testTag("sheet-end-action"))
                    },
                ) {
                    contentBackdrop = LocalLiquidParentBackdrop.current
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(96.dp)
                                .testTag("sheet-backdrop-content"),
                    )
                }
            }
        }

        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("sheet-start-action").assertExists()
        composeRule.onNodeWithTag("sheet-end-action").assertExists()
        composeRule.onNodeWithTag("sheet-backdrop-content").assertExists()

        composeRule.runOnIdle {
            assertNull(startBackdrop)
            assertNull(endBackdrop)
            assertNull(contentBackdrop)
        }
    }

    /**
     * Glass on glass: a control inside the sheet must sample the *sheet*, not the page behind it.
     *
     * This is what the sheet's `exportedBackdrop` is for. Before the rewrite the sheet lived in a
     * Dialog window and could not sample anything, so it published no parent backdrop at all and every
     * control inside it fell back to a flat fill.
     */
    @Test
    fun childControlsSampleTheSheetSurfaceInsideTheOverlayHost() {
        var pageBackdrop: Backdrop? = null
        var sheetBackdrop: Backdrop? = null

        composeRule.setContent {
            LiquidSheetTestTheme {
                SceneBackdropHost {
                    pageBackdrop = LocalSceneBackdrop.current
                    LiquidGlassBottomSheet(
                        show = true,
                        title = "Inherited backdrop",
                        preferExportedBackdrop = true,
                    ) {
                        sheetBackdrop = LocalLiquidParentBackdrop.current
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(96.dp)
                                    .testTag("sheet-inherited-control"),
                        )
                    }
                }
            }
        }

        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("sheet-inherited-control").assertExists()

        composeRule.runOnIdle {
            assertNotNull(pageBackdrop)
            assertNotNull(sheetBackdrop, "The sheet must publish its own surface for inner controls")
            assertTrue(
                sheetBackdrop !== pageBackdrop,
                "Inner controls must sample the sheet, not the page it floats over",
            )
        }
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
                        GrayRows(count = 24, height = 48.dp)
                    }
                }
            }
        }

        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        val heightBefore = sheetHeight()
        val bottomBefore = sheetBottom()
        assertTrue(
            heightBefore >= composeRule.rootHeight() * 0.90f,
            "Expected tall sheet before drag, got $heightBefore",
        )
        val dragDistance = composeRule.rootHeight() * 0.25f

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
    fun contentDownwardDragShorterThanScrollRollbackDoesNotMoveSheet() {
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
                        GrayRows(count = 48, height = 56.dp)
                    }
                }
            }
        }

        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        val contentScrollDistance = composeRule.rootHeight() * 0.42f

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

        val downwardDrag = composeRule.rootHeight() * 0.20f
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
                        GrayRows(count = 24, height = 48.dp)
                    }
                }
            }
        }

        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        val heightBefore = sheetHeight()
        val bottomBefore = sheetBottom()
        val downwardDrag = composeRule.rootHeight() * 0.30f
        val upwardDrag = composeRule.rootHeight() * 0.18f

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
                        GrayRows(count = 6, height = 56.dp)
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
            moveBy(Offset(x = 0f, y = -(composeRule.rootHeight() * 0.80f).toPx()))
            up()
        }

        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()

        val topAfter = sheetTop()
        assertTrue(
            topBefore > composeRule.rootHeight() * 0.30f,
            "Expected sheet to start away from safe top, got $topBefore",
        )
        assertTrue(
            topAfter < topBefore - composeRule.rootHeight() * 0.20f,
            "Expected upward drag to expand sheet length toward safe top, before=$topBefore after=$topAfter",
        )
        assertTrue(
            sheetHeight() > heightBefore + composeRule.rootHeight() * 0.20f,
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
                        GrayRows(count = 24, height = 48.dp)
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
            "Expected minimum sheet height near 1/3, got ${sheetHeight()}",
        )
    }

    @Test
    fun topChromeSemanticsCanCollapseExpandAndDismissSheet() {
        var dismissRequests = 0
        composeRule.setContent {
            LiquidSheetTestTheme {
                LiquidGlassBottomSheet(
                    show = true,
                    modifier = Modifier.testTag(SHEET_TAG),
                    title = "Sheet",
                    initialDetent = LiquidSheetInitialDetent.Half,
                    onDismissRequest = { dismissRequests++ },
                ) {
                    SheetContentColumn(verticalSpacing = 0.dp) {
                        GrayRows(count = 24, height = 48.dp)
                    }
                }
            }
        }

        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        val initialHeight = sheetHeight()
        val withinSheet: (SemanticsMatcher) -> SemanticsMatcher = { matcher ->
            matcher and hasAnyAncestor(hasTestTag(SHEET_TAG))
        }

        composeRule
            .onNode(withinSheet(SemanticsMatcher.keyIsDefined(SemanticsActions.Collapse)))
            .performSemanticsAction(SemanticsActions.Collapse)
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        val collapsedHeight = sheetHeight()
        assertTrue(collapsedHeight < initialHeight)

        composeRule
            .onNode(withinSheet(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand)))
            .performSemanticsAction(SemanticsActions.Expand)
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        assertTrue(sheetHeight() > collapsedHeight)

        composeRule
            .onNode(withinSheet(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss)))
            .performSemanticsAction(SemanticsActions.Dismiss)
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        assertEquals(1, dismissRequests)
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
                        GrayRows(count = 24, height = 48.dp)
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
                composeRule.rootHeight() * 0.40f
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
                        GrayRows(count = 24, height = 48.dp)
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
                composeRule.rootHeight() * 0.40f
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
            "Expected blocked dismiss to keep minimum sheet height, got ${sheetHeight()}",
        )
    }

    private fun sheetHeight(): Dp = composeRule.nodeBounds(SHEET_TAG).let { it.bottom - it.top }

    private fun sheetWidth(): Dp = composeRule.nodeBounds(SHEET_TAG).let { it.right - it.left }

    private fun sheetTop(): Dp = composeRule.nodeBounds(SHEET_TAG).let { it.top }

    private fun sheetBottom(): Dp = composeRule.nodeBounds(SHEET_TAG).let { it.bottom }

    private fun oneThirdRootHeight(): Dp = composeRule.rootHeight() / 3f

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

class LiquidGlassBottomSheetTestApp : Application()
