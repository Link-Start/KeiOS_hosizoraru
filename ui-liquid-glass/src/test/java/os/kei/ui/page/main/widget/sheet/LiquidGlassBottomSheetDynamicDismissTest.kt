@file:Suppress("FunctionName")

package os.kei.ui.page.main.widget.sheet

import android.app.Application
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import os.kei.ui.page.main.widget.motion.LocalPredictiveBackAnimationsEnabled
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
import kotlin.test.assertEquals

private const val DYNAMIC_DISMISS_SHEET_TAG = "dynamic-dismiss-liquid-sheet"

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = Application::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class LiquidGlassBottomSheetDynamicDismissTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun disablingDismissDuringExitAnimationBlocksAndRestoresSheet() {
        val allowDismiss = mutableStateOf(true)
        var dismissRequests = 0
        var blockedDismissRequests = 0
        composeRule.setContent {
            LiquidSheetTestTheme {
                LiquidGlassBottomSheet(
                    show = true,
                    modifier = Modifier.testTag(DYNAMIC_DISMISS_SHEET_TAG),
                    title = "Sheet",
                    initialDetent = LiquidSheetInitialDetent.Full,
                    allowDismiss = allowDismiss.value,
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
        val dragDistance = composeRule.rootHeight() * 0.82f

        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag(DYNAMIC_DISMISS_SHEET_TAG).performTouchInput {
            val start = Offset(x = width / 2f, y = 12.dp.toPx())
            down(start)
            moveBy(Offset(x = 0f, y = dragDistance.toPx()))
            up()
        }
        composeRule.runOnIdle { allowDismiss.value = false }
        composeRule.mainClock.advanceTimeBy(3_000)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        assertEquals(0, dismissRequests)
        assertEquals(1, blockedDismissRequests)
        composeRule.onNodeWithTag(DYNAMIC_DISMISS_SHEET_TAG).assertExists()
    }

    @Test
    fun repeatedBackDuringExitAnimationDispatchesOneDismissRequest() {
        var dismissRequests = 0
        lateinit var dialogBackDispatcher: OnBackPressedDispatcher
        composeRule.setContent {
            LiquidSheetTestTheme {
                CompositionLocalProvider(LocalPredictiveBackAnimationsEnabled provides false) {
                    LiquidGlassBottomSheet(
                        show = true,
                        modifier = Modifier.testTag(DYNAMIC_DISMISS_SHEET_TAG),
                        title = "Sheet",
                        initialDetent = LiquidSheetInitialDetent.Full,
                        onDismissRequest = { dismissRequests++ },
                    ) {
                        val owner = checkNotNull(LocalOnBackPressedDispatcherOwner.current)
                        SideEffect { dialogBackDispatcher = owner.onBackPressedDispatcher }
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(240.dp)
                                    .background(Color.Gray),
                        )
                    }
                }
            }
        }

        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false

        composeRule.runOnIdle { dialogBackDispatcher.onBackPressed() }
        composeRule.mainClock.advanceTimeBy(16)
        composeRule.runOnIdle { dialogBackDispatcher.onBackPressed() }
        composeRule.mainClock.advanceTimeBy(3_000)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        assertEquals(1, dismissRequests)
    }

    @Test
    fun disabledTransitionAnimationsDismissAndFinishWithoutAnimationDuration() {
        val show = mutableStateOf(true)
        var dismissRequests = 0
        var dismissFinished = 0
        lateinit var dialogBackDispatcher: OnBackPressedDispatcher
        composeRule.setContent {
            LiquidSheetTestTheme {
                CompositionLocalProvider(
                    LocalPredictiveBackAnimationsEnabled provides false,
                    LocalTransitionAnimationsEnabled provides false,
                ) {
                    LiquidGlassBottomSheet(
                        show = show.value,
                        modifier = Modifier.testTag(DYNAMIC_DISMISS_SHEET_TAG),
                        title = "Sheet",
                        onDismissRequest = {
                            dismissRequests++
                            show.value = false
                        },
                        onDismissFinished = { dismissFinished++ },
                    ) {
                        val owner = checkNotNull(LocalOnBackPressedDispatcherOwner.current)
                        SideEffect { dialogBackDispatcher = owner.onBackPressedDispatcher }
                        Box(Modifier.fillMaxWidth().height(240.dp))
                    }
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { dialogBackDispatcher.onBackPressed() }
        composeRule.mainClock.advanceTimeBy(32)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(DYNAMIC_DISMISS_SHEET_TAG).assertDoesNotExist()
        assertEquals(1, dismissRequests)
        assertEquals(1, dismissFinished)
        composeRule.mainClock.autoAdvance = true
    }
}

