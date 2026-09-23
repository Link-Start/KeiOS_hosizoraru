package os.kei.ui.page.main.sync

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlin.math.abs
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import os.kei.R
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
import os.kei.ui.page.main.widget.support.LocalTextCopyExpandedOverride
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = WebDavSyncHistoryCardsTestApp::class,
    sdk = [35],
    qualifiers = "en-rUS-w360dp-h800dp-xxhdpi",
)
class WebDavSyncHistoryCardsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun largeFontDiagnosticsRemainMultilineSeparatedAndCopyableAtCompactWidth() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val diagnosticLabels =
            listOf(
                context.getString(R.string.webdav_sync_history_label_power),
                context.getString(R.string.webdav_sync_history_label_network),
                context.getString(R.string.webdav_sync_history_label_scheduler),
            )

        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(baseDensity.density, fontScale = 1.5f),
                LocalTextCopyExpandedOverride provides false,
                LocalTransitionAnimationsEnabled provides false,
            ) {
                MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                    Box(
                        modifier =
                            Modifier
                                .width(360.dp)
                                .testTag(ROOT_TAG),
                    ) {
                        WebDavSyncHistoryEntryCard(
                            entry = largeFontHistoryEntry,
                            expanded = true,
                            cardColor = MiuixTheme.colorScheme.surfaceContainer,
                            onExpandedChange = {},
                        )
                    }
                }
            }
        }

        val rootBounds = composeRule.onNodeWithTag(ROOT_TAG).fetchSemanticsNode().boundsInRoot
        val diagnosticRows =
            diagnosticLabels.map { label ->
                composeRule.onNodeWithText(label).fetchSemanticsNode().also { row ->
                    assertTrue(
                        row.config.contains(SemanticsActions.OnLongClick),
                        "$label must retain AppInfoRow's long-press copy action",
                    )
                }.boundsInRoot
            }
        val expectedGap = with(composeRule.density) { 6.dp.toPx() }
        val gapTolerance = with(composeRule.density) { 1.dp.toPx() }

        diagnosticRows.zipWithNext().forEach { (previous, next) ->
            val actualGap = next.top - previous.bottom
            assertTrue(previous.bottom <= next.top, "Diagnostic rows must remain vertically separated: $previous, $next")
            assertTrue(
                abs(actualGap - expectedGap) <= gapTolerance,
                "AppInfoListBody must keep the compact 6dp gap; actual=${with(composeRule.density) { actualGap.toDp() }}",
            )
        }
        diagnosticRows.forEach { bounds ->
            assertTrue(bounds.left >= rootBounds.left)
            assertTrue(bounds.right <= rootBounds.right)
            assertTrue(bounds.top >= rootBounds.top)
            assertTrue(bounds.bottom <= rootBounds.bottom)
        }

        val schedulerLabel = diagnosticLabels.last()
        val schedulerLabelBounds =
            composeRule
                .onNodeWithText(schedulerLabel, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        val schedulerValueBounds =
            composeRule
                .onNode(hasText(LONG_PENDING_REASON, substring = true), useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        assertTrue(
            schedulerValueBounds.height > schedulerLabelBounds.height,
            "The long scheduler diagnostic must remain measurable as multiline text at 1.5x font scale",
        )
        assertTrue(
            schedulerValueBounds.bottom <= diagnosticRows.last().bottom,
            "The multiline scheduler value must remain inside its AppInfoRow",
        )
    }
}

class WebDavSyncHistoryCardsTestApp : Application()

private val largeFontHistoryEntry =
    WebDavSyncHistoryEntry(
        id = "large-font-layout",
        source = WebDavSyncHistorySource.Auto,
        kind = WebDavSyncHistoryKind.Sync,
        reason = "scheduled synchronization after a long background interval",
        status = WebDavAutoSyncStatus.Success,
        startedAtMs = 1_000L,
        finishedAtMs = 126_000L,
        targetCount = 2,
        succeededCount = 2,
        failedCount = 0,
        skippedCount = 0,
        items =
            listOf(
                WebDavSyncHistoryItem(
                    item = WebDavSyncItem.GitHubTracked,
                    status = WebDavItemStatus.UpToDate,
                    detail = "The remote record already matches the local tracked repository snapshot",
                ),
            ),
        runtimeDiagnostics =
            WebDavSyncRuntimeDiagnostics(
                interactive = false,
                deviceIdle = true,
                lightDeviceIdle = true,
                powerSave = true,
                lowPowerStandbyEnabled = true,
                lowPowerStandbyExempt = false,
                batteryOptimizationExempt = true,
                backgroundDataRestricted = true,
                networkPresent = true,
                networkValidated = false,
                networkNotSuspended = false,
                appStandbyBucket = "restricted-background-processing",
                queuedDurationMs = 125_000L,
                pendingReasons = listOf(LONG_PENDING_REASON, "metered_restricted"),
                previousStopReason = "background execution window expired before synchronization completed",
            ),
    )

private fun sourceFile(relativePath: String): String {
    val workingDirectory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
    val sourceFile =
        generateSequence(workingDirectory) { directory -> directory.parentFile }
            .map { directory -> File(directory, relativePath) }
            .firstOrNull(File::isFile)
    return requireNotNull(sourceFile) {
        "Unable to locate $relativePath from $workingDirectory"
    }.readText()
}

private const val ROOT_TAG = "webdav-history-large-font-root"
private const val LONG_PENDING_REASON =
    "connectivity_and_background_execution_constraints_remain_pending_for_this_synchronization"