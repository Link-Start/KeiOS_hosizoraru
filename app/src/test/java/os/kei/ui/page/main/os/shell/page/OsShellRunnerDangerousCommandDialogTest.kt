package os.kei.ui.page.main.os.shell.page

import android.app.Application
import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import os.kei.R
import os.kei.ui.page.main.widget.dialog.LiquidGlassDialog
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = OsShellRunnerDangerousCommandDialogTestApp::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class OsShellRunnerDangerousCommandDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun latestSummaryAndDangerActionsRemainUntilDismissalFinishes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val show = mutableStateOf(true)
        val summary = mutableStateOf(INITIAL_SUMMARY)
        var observedSnapshot: OsShellDialogExitSnapshot<OsShellDangerousCommandDialogContent>? = null
        var dismissCount = 0
        var confirmCount = 0
        val cancelText = context.getString(R.string.common_cancel)

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                CompositionLocalProvider(LocalTransitionAnimationsEnabled provides true) {
                    val currentContent =
                        if (show.value) {
                            OsShellDangerousCommandDialogContent(
                                title = DIALOG_TITLE,
                                summary = summary.value,
                                confirmText = CONFIRM_TEXT,
                            )
                        } else {
                            null
                        }
                    val exitSnapshot = rememberOsShellDialogExitSnapshot(currentContent)
                    val renderedContent = exitSnapshot.resolve(currentContent)
                    SideEffect { observedSnapshot = exitSnapshot }

                    LiquidGlassDialog(
                        show = show.value,
                        title = renderedContent?.title,
                        summary = renderedContent?.summary,
                        onDismissFinished = exitSnapshot::clear,
                    ) {
                        renderedContent?.let { content ->
                            OsShellDangerousCommandConfirmActions(
                                confirmText = content.confirmText,
                                actionsEnabled = show.value,
                                onDismissRequest = { dismissCount++ },
                                onConfirm = { confirmCount++ },
                            )
                        }
                    }
                }
            }
        }

        composeRule.onNode(hasText(DIALOG_TITLE) and isHeading()).assertIsDisplayed()
        composeRule.onNode(hasText(INITIAL_SUMMARY)).assertIsDisplayed()

        composeRule.runOnIdle { summary.value = LATEST_SUMMARY }
        composeRule.onAllNodes(hasText(INITIAL_SUMMARY)).assertCountEquals(0)
        composeRule.onNode(hasText(LATEST_SUMMARY)).assertIsDisplayed()
        composeRule.onNode(hasText(cancelText) and hasClickAction()).performClick()
        composeRule.onNode(hasText(CONFIRM_TEXT) and hasClickAction()).performClick()

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { show.value = false }
        composeRule.mainClock.advanceTimeBy(EXIT_OBSERVATION_MILLIS)

        composeRule.onNode(hasText(LATEST_SUMMARY)).assertIsDisplayed()
        composeRule.onNode(hasText(cancelText)).assertIsNotEnabled()
        composeRule.onNode(hasText(CONFIRM_TEXT)).assertIsNotEnabled()
        assertEquals(
            LATEST_SUMMARY,
            assertNotNull(observedSnapshot).retainedValue?.summary,
        )

        finishExitAnimation()

        composeRule.onAllNodes(hasText(DIALOG_TITLE)).assertCountEquals(0)
        assertNull(assertNotNull(observedSnapshot).retainedValue)
        assertEquals(1, dismissCount)
        assertEquals(1, confirmCount)
    }

    private fun finishExitAnimation() {
        composeRule.mainClock.advanceTimeBy(EXIT_COMPLETION_MILLIS)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
    }
}

class OsShellRunnerDangerousCommandDialogTestApp : Application()

private const val DIALOG_TITLE = "Run dangerous command?"
private const val INITIAL_SUMMARY = "Command: rm -rf /tmp/old"
private const val LATEST_SUMMARY = "Command: rm -rf /tmp/latest"
private const val CONFIRM_TEXT = "Run anyway"
private const val EXIT_OBSERVATION_MILLIS = 16L
private const val EXIT_COMPLETION_MILLIS = 300L