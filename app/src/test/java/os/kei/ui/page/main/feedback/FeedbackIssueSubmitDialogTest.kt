package os.kei.ui.page.main.feedback

import android.app.Application
import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = FeedbackIssueSubmitDialogTestApp::class,
    sdk = [35],
    qualifiers = "w360dp-h800dp-xxhdpi",
)
class FeedbackIssueSubmitDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun browserConfirmationSupportsBothTokenStatesAndCompactLargeFontActions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mode = mutableStateOf<FeedbackSubmitMode?>(FeedbackSubmitMode.Browser)
        val tokenAvailable = mutableStateOf(true)
        val submitting = mutableStateOf(false)
        var dismissCount = 0
        var browserCount = 0
        var apiCount = 0
        val browserSummary = context.getString(R.string.feedback_issue_confirm_browser_summary)
        val apiSummary = context.getString(R.string.feedback_issue_confirm_api_summary)
        val missingTokenSummary = context.getString(R.string.feedback_issue_confirm_api_missing_token)
        val cancelText = context.getString(R.string.common_cancel)
        val browserText = context.getString(R.string.feedback_issue_confirm_browser)

        setFeedbackDialogContent(
            mode = mode::value,
            apiTokenAvailable = tokenAvailable::value,
            submitting = submitting::value,
            colorSchemeMode = ColorSchemeMode.Light,
            animationsEnabled = false,
            onDismiss = { dismissCount++ },
            onConfirmBrowser = { browserCount++ },
            onConfirmApi = { apiCount++ },
        )

        composeRule.onNode(hasText(browserSummary)).assertIsDisplayed()
        composeRule.onAllNodes(hasText(apiSummary)).assertCountEquals(0)
        composeRule.onAllNodes(hasText(missingTokenSummary)).assertCountEquals(0)

        composeRule.runOnIdle { tokenAvailable.value = false }

        composeRule.onNode(hasText(browserSummary)).assertIsDisplayed()
        composeRule.onAllNodes(hasText(apiSummary)).assertCountEquals(0)
        composeRule.onAllNodes(hasText(missingTokenSummary)).assertCountEquals(0)
        assertChecklistAndActionsReachable(
            context = context,
            cancelText = cancelText,
            confirmText = browserText,
        )
        composeRule.onNode(hasText(cancelText) and hasClickAction()).performClick()
        composeRule.onNode(hasText(browserText) and hasClickAction()).performClick()

        assertEquals(1, dismissCount)
        assertEquals(1, browserCount)
        assertEquals(0, apiCount)
    }

    @Test
    fun apiConfirmationWithTokenUsesApiCopyAndRoutesActions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mode = mutableStateOf<FeedbackSubmitMode?>(FeedbackSubmitMode.GitHubApi)
        val tokenAvailable = mutableStateOf(true)
        val submitting = mutableStateOf(false)
        var dismissCount = 0
        var browserCount = 0
        var apiCount = 0
        val apiSummary = context.getString(R.string.feedback_issue_confirm_api_summary)
        val missingTokenSummary = context.getString(R.string.feedback_issue_confirm_api_missing_token)
        val cancelText = context.getString(R.string.common_cancel)
        val apiText = context.getString(R.string.feedback_issue_confirm_api)

        setFeedbackDialogContent(
            mode = mode::value,
            apiTokenAvailable = tokenAvailable::value,
            submitting = submitting::value,
            colorSchemeMode = ColorSchemeMode.Dark,
            animationsEnabled = false,
            onDismiss = { dismissCount++ },
            onConfirmBrowser = { browserCount++ },
            onConfirmApi = { apiCount++ },
        )

        composeRule.onNode(hasText(apiSummary)).assertIsDisplayed()
        composeRule.onAllNodes(hasText(missingTokenSummary)).assertCountEquals(0)
        composeRule.onNode(hasText(cancelText) and hasClickAction()).performClick()
        composeRule.onNode(hasText(apiText) and hasClickAction()).performClick()

        assertEquals(1, dismissCount)
        assertEquals(0, browserCount)
        assertEquals(1, apiCount)
    }

    @Test
    fun missingTokenApiCopyAndSnapshotRemainStableThroughExit() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mode = mutableStateOf<FeedbackSubmitMode?>(FeedbackSubmitMode.GitHubApi)
        val tokenAvailable = mutableStateOf(false)
        val submitting = mutableStateOf(false)
        var observedSnapshot: FeedbackSubmitDialogExitSnapshot? = null
        var dismissCount = 0
        var browserCount = 0
        var apiCount = 0
        val title = context.getString(R.string.feedback_issue_confirm_title)
        val apiSummary = context.getString(R.string.feedback_issue_confirm_api_summary)
        val missingTokenSummary = context.getString(R.string.feedback_issue_confirm_api_missing_token)
        val lastChecklistItem = context.getString(R.string.feedback_issue_confirm_check_zip)
        val cancelText = context.getString(R.string.common_cancel)
        val apiText = context.getString(R.string.feedback_issue_confirm_api)

        setFeedbackDialogContent(
            mode = mode::value,
            apiTokenAvailable = tokenAvailable::value,
            submitting = submitting::value,
            colorSchemeMode = ColorSchemeMode.Light,
            animationsEnabled = true,
            onSnapshotObserved = { observedSnapshot = it },
            onDismiss = { dismissCount++ },
            onConfirmBrowser = { browserCount++ },
            onConfirmApi = { apiCount++ },
        )

        composeRule.onNode(hasText(title) and isHeading()).assertIsDisplayed()
        composeRule.onNode(hasText(missingTokenSummary)).assertIsDisplayed()
        composeRule.onAllNodes(hasText(apiSummary)).assertCountEquals(0)
        composeRule.onNode(hasText(lastChecklistItem)).performScrollTo().assertIsDisplayed()
        composeRule.onNode(hasText(apiText) and hasClickAction()).performClick()

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle {
            mode.value = null
            tokenAvailable.value = true
            submitting.value = true
        }

        composeRule.mainClock.advanceTimeBy(EXIT_OBSERVATION_MILLIS)

        composeRule.onNode(hasText(missingTokenSummary)).assertIsDisplayed()
        composeRule.onAllNodes(hasText(apiSummary)).assertCountEquals(0)
        composeRule.onNode(hasText(lastChecklistItem)).assertIsDisplayed()
        composeRule.onNode(hasText(cancelText)).assertIsNotEnabled()
        composeRule.onNode(hasText(apiText)).assertIsNotEnabled()
        assertEquals(
            FeedbackSubmitDialogSnapshot(
                mode = FeedbackSubmitMode.GitHubApi,
                apiTokenAvailable = false,
                submitting = false,
            ),
            assertNotNull(observedSnapshot).retainedValue,
        )

        finishExitAnimation()

        composeRule.onAllNodes(hasText(title)).assertCountEquals(0)
        assertNull(assertNotNull(observedSnapshot).retainedValue)
        assertEquals(0, dismissCount)
        assertEquals(0, browserCount)
        assertEquals(1, apiCount)
    }

    private fun setFeedbackDialogContent(
        mode: () -> FeedbackSubmitMode?,
        apiTokenAvailable: () -> Boolean,
        submitting: () -> Boolean,
        colorSchemeMode: ColorSchemeMode,
        animationsEnabled: Boolean,
        onSnapshotObserved: (FeedbackSubmitDialogExitSnapshot) -> Unit = {},
        onDismiss: () -> Unit,
        onConfirmBrowser: () -> Unit,
        onConfirmApi: () -> Unit,
    ) {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(colorSchemeMode)) {
                val baseDensity = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(baseDensity.density, fontScale = 1.5f),
                    LocalTransitionAnimationsEnabled provides animationsEnabled,
                ) {
                    val currentMode = mode()
                    val currentTokenAvailable = apiTokenAvailable()
                    val currentSubmitting = submitting()
                    val currentSnapshot =
                        remember(currentMode, currentTokenAvailable, currentSubmitting) {
                            currentMode?.let {
                                FeedbackSubmitDialogSnapshot(
                                    mode = it,
                                    apiTokenAvailable = currentTokenAvailable,
                                    submitting = currentSubmitting,
                                )
                            }
                        }
                    val exitSnapshot = rememberFeedbackSubmitDialogExitSnapshot(currentSnapshot)
                    val renderedSnapshot = exitSnapshot.resolve(currentSnapshot)
                    SideEffect { onSnapshotObserved(exitSnapshot) }

                    LiquidGlassDialog(
                        show = currentMode != null,
                        title = stringResource(R.string.feedback_issue_confirm_title),
                        summary = renderedSnapshot?.let { feedbackSubmitSummary(it) },
                        onDismissFinished = exitSnapshot::clear,
                    ) {
                        renderedSnapshot?.let { snapshot ->
                            FeedbackSubmitConfirmDialogBody(
                                snapshot = snapshot,
                                actionsEnabled = currentMode != null,
                                onDismiss = onDismiss,
                                onConfirmBrowser = onConfirmBrowser,
                                onConfirmApi = onConfirmApi,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun assertChecklistAndActionsReachable(
        context: Context,
        cancelText: String,
        confirmText: String,
    ) {
        val checklistHeight =
            with(composeRule.density) {
                composeRule
                    .onNodeWithTag(FEEDBACK_CONFIRM_CHECKLIST_TEST_TAG)
                    .fetchSemanticsNode()
                    .boundsInRoot
                    .height
                    .toDp()
            }
        assertTrue(
            checklistHeight > 0.dp && checklistHeight <= 320.dp,
            "Expected a visible checklist viewport no taller than 320dp, actual=$checklistHeight",
        )
        listOf(
            R.string.feedback_issue_confirm_check_public,
            R.string.feedback_issue_confirm_check_sensitive,
            R.string.feedback_issue_confirm_check_steps,
            R.string.feedback_issue_confirm_check_zip,
        ).map(context::getString).forEach { item ->
            composeRule.onNode(hasText(item)).performScrollTo().assertIsDisplayed()
        }
        composeRule
            .onNode(hasText(cancelText) and hasClickAction())
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
        composeRule
            .onNode(hasText(confirmText) and hasClickAction())
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }

    private fun finishExitAnimation() {
        composeRule.mainClock.advanceTimeBy(EXIT_COMPLETION_MILLIS)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
    }
}

class FeedbackIssueSubmitDialogTestApp : Application()

private const val EXIT_OBSERVATION_MILLIS = 16L
private const val EXIT_COMPLETION_MILLIS = 300L