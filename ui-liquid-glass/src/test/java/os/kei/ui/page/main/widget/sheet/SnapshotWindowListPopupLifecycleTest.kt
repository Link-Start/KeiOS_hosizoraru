package os.kei.ui.page.main.widget.sheet

import android.app.Application
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = Application::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class SnapshotWindowListPopupLifecycleTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun customProviderRunsAndExitAnimationFinishesBeforePopupLeavesComposition() {
        val show = mutableStateOf(false)
        var calculateCount = 0
        var dismissFinishedCount = 0
        val provider =
            object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowBounds: IntRect,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize,
                    popupMargin: IntRect,
                    alignment: PopupPositionProvider.Align,
                ): IntOffset {
                    calculateCount++
                    return IntOffset(anchorBounds.left, anchorBounds.bottom + popupMargin.bottom)
                }

                override fun getMargins() = ListPopupDefaults.DropdownPositionProvider.getMargins()
            }

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                SnapshotWindowListPopup(
                    show = show.value,
                    popupPositionProvider = provider,
                    anchorBounds = IntRect(left = 120, top = 180, right = 260, bottom = 240),
                    onDismissFinished = { dismissFinishedCount++ },
                ) {
                    Box(Modifier.size(width = 160.dp, height = 120.dp).testTag("snapshot-popup"))
                }
            }
        }

        composeRule.runOnIdle { show.value = true }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("snapshot-popup").assertExists()
        assertTrue(calculateCount > 0)

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { show.value = false }
        composeRule.mainClock.advanceTimeBy(60)
        composeRule.onNodeWithTag("snapshot-popup").assertExists()

        composeRule.mainClock.advanceTimeBy(500)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("snapshot-popup").assertDoesNotExist()
        assertEquals(1, dismissFinishedCount)
    }

    @Test
    fun exitAnimationConsumesAdditionalBackWithoutDismissingUnderlyingContent() {
        val show = mutableStateOf(true)
        var dismissRequestCount = 0
        var underlyingBackCount = 0
        lateinit var backDispatcher: OnBackPressedDispatcher
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val localBackDispatcher =
                    checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
                SideEffect { backDispatcher = localBackDispatcher }
                BackHandler { underlyingBackCount++ }
                SnapshotWindowListPopup(
                    show = show.value,
                    anchorBounds = IntRect(left = 120, top = 180, right = 260, bottom = 240),
                    onDismissRequest = {
                        dismissRequestCount++
                        show.value = false
                    },
                ) {
                    Box(Modifier.size(width = 160.dp, height = 120.dp).testTag("back-popup"))
                }
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false

        composeRule.runOnIdle { backDispatcher.onBackPressed() }
        composeRule.mainClock.advanceTimeBy(16)
        composeRule.onNodeWithTag("back-popup").assertExists()
        composeRule.runOnIdle { backDispatcher.onBackPressed() }

        assertEquals(1, dismissRequestCount)
        assertEquals(0, underlyingBackCount)

        composeRule.mainClock.advanceTimeBy(500)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("back-popup").assertDoesNotExist()
    }
}
