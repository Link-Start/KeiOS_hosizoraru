@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.importer

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
    application = GitHubStarImportConfirmDialogTestApp::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class GitHubStarImportConfirmDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun importConfirmExitKeepsSummaryAndActionsUntilDismissalFinishes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val show = mutableStateOf(true)
        val retainedSummary =
            context.getString(
                R.string.github_star_import_confirm_summary_format,
                3,
                2,
                1,
            )
        var observedSnapshot: GitHubStarDialogExitSnapshot<String>? = null
        var dismissCount = 0
        var confirmCount = 0
        val title = context.getString(R.string.github_star_import_confirm_title)
        val cancelLabel = context.getString(R.string.common_cancel)
        val confirmLabel = context.getString(R.string.github_star_import_confirm_action)

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                CompositionLocalProvider(LocalTransitionAnimationsEnabled provides true) {
                    val currentSummary = retainedSummary.takeIf { show.value }
                    val exitSnapshot = rememberGitHubStarDialogExitSnapshot(currentSummary)
                    val renderedSummary = exitSnapshot.resolve(currentSummary)
                    SideEffect { observedSnapshot = exitSnapshot }
                    LiquidGlassDialog(
                        show = show.value,
                        title = title,
                        summary = renderedSummary,
                        onDismissFinished = exitSnapshot::clear,
                    ) {
                        renderedSummary?.let {
                            GitHubStarImportConfirmActions(
                                importing = false,
                                actionsEnabled = show.value,
                                onDismissRequest = { dismissCount++ },
                                onConfirmImport = { confirmCount++ },
                            )
                        }
                    }
                }
            }
        }

        composeRule.onNode(hasText(title) and isHeading()).assertIsDisplayed()
        composeRule.onNode(hasText(retainedSummary)).assertIsDisplayed()
        composeRule.onNode(hasText(cancelLabel) and hasClickAction()).performClick()
        composeRule.onNode(hasText(confirmLabel) and hasClickAction()).performClick()

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { show.value = false }
        composeRule.mainClock.advanceTimeBy(EXIT_OBSERVATION_MILLIS)

        composeRule.onNode(hasText(retainedSummary)).assertIsDisplayed()
        composeRule.onNode(hasText(cancelLabel)).assertIsNotEnabled()
        composeRule.onNode(hasText(confirmLabel)).assertIsNotEnabled()
        assertEquals(retainedSummary, assertNotNull(observedSnapshot).retainedValue)

        finishExitAnimation()

        composeRule.onAllNodes(hasText(title)).assertCountEquals(0)
        assertNull(assertNotNull(observedSnapshot).retainedValue)
        assertEquals(1, dismissCount)
        assertEquals(1, confirmCount)
    }

    @Test
    fun exitConfirmKeepsSelectedCountAndDangerActionsUntilDismissalFinishes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val show = mutableStateOf(true)
        val selectedCount = 5
        var observedSnapshot: GitHubStarDialogExitSnapshot<Int>? = null
        var dismissCount = 0
        var confirmCount = 0
        val title = context.getString(R.string.github_star_import_exit_confirm_title)
        val summary =
            context.getString(
                R.string.github_star_import_exit_confirm_summary_format,
                selectedCount,
            )
        val keepLabel = context.getString(R.string.github_star_import_exit_confirm_keep)
        val exitLabel = context.getString(R.string.github_star_import_exit_confirm_action)

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                CompositionLocalProvider(LocalTransitionAnimationsEnabled provides true) {
                    val currentCount = selectedCount.takeIf { show.value }
                    val exitSnapshot = rememberGitHubStarDialogExitSnapshot(currentCount)
                    val renderedCount = exitSnapshot.resolve(currentCount)
                    SideEffect { observedSnapshot = exitSnapshot }
                    LiquidGlassDialog(
                        show = show.value,
                        title = title,
                        summary =
                            renderedCount?.let {
                                context.getString(
                                    R.string.github_star_import_exit_confirm_summary_format,
                                    it,
                                )
                            },
                        onDismissFinished = exitSnapshot::clear,
                    ) {
                        renderedCount?.let {
                            GitHubStarImportExitActions(
                                actionsEnabled = show.value,
                                onDismissRequest = { dismissCount++ },
                                onConfirmExit = { confirmCount++ },
                            )
                        }
                    }
                }
            }
        }

        composeRule.onNode(hasText(title) and isHeading()).assertIsDisplayed()
        composeRule.onNode(hasText(summary)).assertIsDisplayed()
        composeRule.onNode(hasText(keepLabel) and hasClickAction()).performClick()
        composeRule.onNode(hasText(exitLabel) and hasClickAction()).performClick()

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { show.value = false }
        composeRule.mainClock.advanceTimeBy(EXIT_OBSERVATION_MILLIS)

        composeRule.onNode(hasText(summary)).assertIsDisplayed()
        composeRule.onNode(hasText(keepLabel)).assertIsNotEnabled()
        composeRule.onNode(hasText(exitLabel)).assertIsNotEnabled()
        assertEquals(selectedCount, assertNotNull(observedSnapshot).retainedValue)

        finishExitAnimation()

        composeRule.onAllNodes(hasText(title)).assertCountEquals(0)
        assertNull(assertNotNull(observedSnapshot).retainedValue)
        assertEquals(1, dismissCount)
        assertEquals(1, confirmCount)
    }

    @Test
    fun importingStateDisablesBothConfirmActions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var dismissCount = 0
        var confirmCount = 0
        val cancelLabel = context.getString(R.string.common_cancel)
        val importingLabel = context.getString(R.string.github_star_import_status_importing)

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                CompositionLocalProvider(LocalTransitionAnimationsEnabled provides false) {
                    LiquidGlassDialog(show = true, title = "Star import") {
                        GitHubStarImportConfirmActions(
                            importing = true,
                            onDismissRequest = { dismissCount++ },
                            onConfirmImport = { confirmCount++ },
                        )
                    }
                }
            }
        }

        composeRule.onNode(hasText(cancelLabel)).assertIsNotEnabled()
        composeRule.onNode(hasText(importingLabel)).assertIsNotEnabled()
        assertEquals(0, dismissCount)
        assertEquals(0, confirmCount)
    }

    private fun finishExitAnimation() {
        composeRule.mainClock.advanceTimeBy(EXIT_COMPLETION_MILLIS)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
    }
}

class GitHubStarImportConfirmDialogTestApp : Application()

private const val EXIT_OBSERVATION_MILLIS = 16L
private const val EXIT_COMPLETION_MILLIS = 300L