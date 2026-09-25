@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.importer

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Composable
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
    application = Application::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class GitHubStarImportConfirmDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun importConfirmExitKeepsSummaryAndActionsUntilDismissalFinishes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertExitRetainsUntilDismissed(
            title = context.getString(R.string.github_star_import_confirm_title),
            dismissLabel = context.getString(R.string.common_cancel),
            confirmLabel = context.getString(R.string.github_star_import_confirm_action),
            value = context.getString(R.string.github_star_import_confirm_summary_format, 3, 2, 1),
            summaryFor = { it },
        ) { enabled, onDismiss, onConfirm ->
            GitHubStarImportConfirmActions(
                importing = false,
                actionsEnabled = enabled,
                onDismissRequest = onDismiss,
                onConfirmImport = onConfirm,
            )
        }
    }

    @Test
    fun exitConfirmKeepsSelectedCountAndDangerActionsUntilDismissalFinishes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertExitRetainsUntilDismissed(
            title = context.getString(R.string.github_star_import_exit_confirm_title),
            dismissLabel = context.getString(R.string.github_star_import_exit_confirm_keep),
            confirmLabel = context.getString(R.string.github_star_import_exit_confirm_action),
            value = 5,
            summaryFor = { count ->
                context.getString(R.string.github_star_import_exit_confirm_summary_format, count)
            },
        ) { enabled, onDismiss, onConfirm ->
            GitHubStarImportExitActions(
                actionsEnabled = enabled,
                onDismissRequest = onDismiss,
                onConfirmExit = onConfirm,
            )
        }
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

    /**
     * Renders a star-import dialog through [rememberGitHubStarDialogExitSnapshot], clicks both actions,
     * hides it, and checks that the summary and disabled actions stay until the exit animation ends.
     */
    private fun <T : Any> assertExitRetainsUntilDismissed(
        title: String,
        dismissLabel: String,
        confirmLabel: String,
        value: T,
        summaryFor: (T) -> String,
        actions: @Composable (enabled: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) -> Unit,
    ) {
        val show = mutableStateOf(true)
        val summary = summaryFor(value)
        var observedSnapshot: GitHubStarDialogExitSnapshot<T>? = null
        var dismissCount = 0
        var confirmCount = 0

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                CompositionLocalProvider(LocalTransitionAnimationsEnabled provides true) {
                    val currentValue = value.takeIf { show.value }
                    val exitSnapshot = rememberGitHubStarDialogExitSnapshot(currentValue)
                    val renderedValue = exitSnapshot.resolve(currentValue)
                    SideEffect { observedSnapshot = exitSnapshot }
                    LiquidGlassDialog(
                        show = show.value,
                        title = title,
                        summary = renderedValue?.let(summaryFor),
                        onDismissFinished = exitSnapshot::clear,
                    ) {
                        renderedValue?.let {
                            actions(show.value, { dismissCount++ }, { confirmCount++ })
                        }
                    }
                }
            }
        }

        composeRule.onNode(hasText(title) and isHeading()).assertIsDisplayed()
        composeRule.onNode(hasText(summary)).assertIsDisplayed()
        composeRule.onNode(hasText(dismissLabel) and hasClickAction()).performClick()
        composeRule.onNode(hasText(confirmLabel) and hasClickAction()).performClick()

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { show.value = false }
        composeRule.mainClock.advanceTimeBy(EXIT_OBSERVATION_MILLIS)

        composeRule.onNode(hasText(summary)).assertIsDisplayed()
        composeRule.onNode(hasText(dismissLabel)).assertIsNotEnabled()
        composeRule.onNode(hasText(confirmLabel)).assertIsNotEnabled()
        assertEquals(value, assertNotNull(observedSnapshot).retainedValue)

        finishExitAnimation()

        composeRule.onAllNodes(hasText(title)).assertCountEquals(0)
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

private const val EXIT_OBSERVATION_MILLIS = 16L
private const val EXIT_COMPLETION_MILLIS = 300L