package os.kei.ui.page.main.os.components

import android.app.Application
import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
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
import os.kei.ui.page.main.os.state.OsCardImportTarget
import os.kei.ui.page.main.os.transfer.OS_CARD_EXPORT_SCHEMA_VERSION
import os.kei.ui.page.main.os.transfer.OsCardImportFileKind
import os.kei.ui.page.main.os.transfer.OsCardImportPreview
import os.kei.ui.page.main.os.transfer.OsShellCardImportPayload
import os.kei.ui.page.main.widget.dialog.LiquidGlassDialog
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
import os.kei.ui.page.main.widget.support.LocalTextCopyExpandedOverride
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
    application = OsImportPreviewDialogTestApp::class,
    sdk = [35],
    qualifiers = "w360dp-h800dp-xxhdpi",
)
class OsImportPreviewDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun importingPreviewKeepsRichStatisticsAndProgressLabelThroughExit() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val show = mutableStateOf(true)
        val importInProgress = mutableStateOf(false)
        var observedSnapshot: OsImportDialogExitSnapshot? = null
        var dismissCount = 0
        var cancelCount = 0
        var confirmCount = 0
        val cancelText = context.getString(R.string.common_cancel)
        val confirmText = context.getString(R.string.os_import_dialog_action_confirm)
        val importingText = context.getString(R.string.os_import_dialog_action_importing)
        val mergedItemsLabel = context.getString(R.string.os_import_dialog_label_merged_items)
        val statisticsLabels =
            listOf(
                R.string.os_import_dialog_label_detected_type,
                R.string.os_import_dialog_label_backup_format,
                R.string.os_import_dialog_label_file_items,
                R.string.os_import_dialog_label_valid_items,
                R.string.os_import_dialog_label_duplicate_items,
                R.string.os_import_dialog_label_invalid_items,
                R.string.os_import_dialog_label_new_items,
                R.string.os_import_dialog_label_updated_items,
                R.string.os_import_dialog_label_unchanged_items,
                R.string.os_import_dialog_label_merged_items,
            ).map(context::getString)

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val baseDensity = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(baseDensity.density, fontScale = 1.5f),
                    LocalTransitionAnimationsEnabled provides true,
                    LocalTextCopyExpandedOverride provides false,
                ) {
                    val currentSnapshot =
                        if (show.value) {
                            OsImportDialogSnapshot(
                                preview = VALID_PREVIEW,
                                importInProgress = importInProgress.value,
                            )
                        } else {
                            null
                        }
                    val exitSnapshot = rememberOsImportDialogExitSnapshot(currentSnapshot)
                    val renderedSnapshot = exitSnapshot.resolve(currentSnapshot)
                    SideEffect { observedSnapshot = exitSnapshot }

                    LiquidGlassDialog(
                        show = show.value,
                        title = DIALOG_TITLE,
                        summary = renderedSnapshot?.let { READY_SUMMARY },
                        onDismissFinished = exitSnapshot::clear,
                    ) {
                        renderedSnapshot?.let { snapshot ->
                            OsImportPreviewDialogBody(
                                snapshot = snapshot,
                                actionsEnabled = show.value,
                                onDismissRequest = { dismissCount++ },
                                onCancel = { cancelCount++ },
                                onConfirmImport = { confirmCount++ },
                            )
                        }
                    }
                }
            }
        }

        composeRule.onNode(hasText(DIALOG_TITLE) and isHeading()).assertIsDisplayed()
        composeRule.onNode(hasText(READY_SUMMARY)).assertIsDisplayed()
        val statisticsHeight =
            with(composeRule.density) {
                composeRule
                    .onNodeWithTag(OS_IMPORT_PREVIEW_STATISTICS_TEST_TAG)
                    .fetchSemanticsNode()
                    .boundsInRoot
                    .height
                    .toDp()
            }
        assertTrue(
            statisticsHeight > 0.dp && statisticsHeight <= 360.dp,
            "Expected a visible statistics viewport no taller than 360dp, actual=$statisticsHeight",
        )
        statisticsLabels.forEach { label ->
            composeRule.onNode(hasText(label)).performScrollTo().assertIsDisplayed()
        }
        composeRule.onNode(hasText(VALID_PREVIEW.mergedCount.toString())).assertIsDisplayed()
        composeRule
            .onNode(hasText(cancelText) and hasClickAction())
            .assertIsDisplayed()
            .performClick()
        composeRule
            .onNode(hasText(confirmText) and hasClickAction())
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle { importInProgress.value = true }
        composeRule.onNode(hasText(importingText)).assertIsNotEnabled()

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle {
            show.value = false
            importInProgress.value = false
        }
        composeRule.mainClock.advanceTimeBy(EXIT_OBSERVATION_MILLIS)

        composeRule.onNode(hasText(READY_SUMMARY)).assertIsDisplayed()
        composeRule.onNode(hasText(mergedItemsLabel)).assertIsDisplayed()
        composeRule.onNode(hasText(cancelText)).assertIsNotEnabled()
        composeRule.onNode(hasText(importingText)).assertIsNotEnabled()
        assertEquals(VALID_PREVIEW, assertNotNull(observedSnapshot).retainedValue?.preview)
        assertEquals(true, assertNotNull(observedSnapshot).retainedValue?.importInProgress)

        finishExitAnimation()

        composeRule.onAllNodes(hasText(DIALOG_TITLE)).assertCountEquals(0)
        assertNull(assertNotNull(observedSnapshot).retainedValue)
        assertEquals(0, dismissCount)
        assertEquals(1, cancelCount)
        assertEquals(1, confirmCount)
    }

    @Test
    fun nonImportablePreviewRoutesCloseActionToDismiss() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var dismissCount = 0
        var cancelCount = 0
        var confirmCount = 0
        val cancelText = context.getString(R.string.common_cancel)
        val closeText = context.getString(R.string.common_close)

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Dark)) {
                val baseDensity = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(baseDensity.density, fontScale = 1.5f),
                    LocalTransitionAnimationsEnabled provides false,
                ) {
                    LiquidGlassDialog(show = true, title = "Wrong target") {
                        OsImportPreviewDialogActions(
                            preview = WRONG_TARGET_PREVIEW,
                            importInProgress = false,
                            onDismissRequest = { dismissCount++ },
                            onCancel = { cancelCount++ },
                            onConfirmImport = { confirmCount++ },
                        )
                    }
                }
            }
        }

        composeRule.onNode(hasText(cancelText) and hasClickAction()).performClick()
        composeRule.onNode(hasText(closeText) and hasClickAction()).performClick()

        assertEquals(1, dismissCount)
        assertEquals(1, cancelCount)
        assertEquals(0, confirmCount)
    }

    private fun finishExitAnimation() {
        composeRule.mainClock.advanceTimeBy(EXIT_COMPLETION_MILLIS)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
    }
}

class OsImportPreviewDialogTestApp : Application()

private val VALID_PREVIEW =
    OsCardImportPreview(
        target = OsCardImportTarget.Shell,
        payload =
            OsShellCardImportPayload(
                cards = emptyList(),
                sourceCount = 17,
                invalidCount = 2,
                duplicateCount = 2,
                fileKind = OsCardImportFileKind.Shell,
                schemaVersion = OS_CARD_EXPORT_SCHEMA_VERSION,
                isLegacyFormat = false,
            ),
        fileItemCount = 17,
        validCount = 13,
        duplicateCount = 2,
        invalidCount = 2,
        newCount = 7,
        updatedCount = 3,
        unchangedCount = 1,
        mergedCount = 11,
    )

private val WRONG_TARGET_PREVIEW = VALID_PREVIEW.copy(target = OsCardImportTarget.Activity)

private const val DIALOG_TITLE = "Import OS cards?"
private const val READY_SUMMARY = "The backup is ready to import."
private const val EXIT_OBSERVATION_MILLIS = 16L
private const val EXIT_COMPLETION_MILLIS = 300L