package os.kei.ui.page.main.github.history

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import os.kei.R
import os.kei.feature.github.model.GitHubTrackChangeField
import os.kei.feature.github.model.GitHubTrackChangeHistoryAction
import os.kei.feature.github.model.GitHubTrackChangeHistoryRecord
import os.kei.feature.github.model.GitHubTrackChangeHistorySource
import os.kei.feature.github.model.GitHubTrackedSourceMode
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
import os.kei.ui.page.main.widget.support.LocalTextCopyExpandedOverride
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = Application::class,
    sdk = [35],
    qualifiers = "en-rUS-w360dp-h800dp-xxhdpi",
)
class GitHubHistoryInfoListBodyTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun trackChangeDetailsAtLargeFontKeepCompactGapsCopyAndExpansionBoundaries() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rowLabels =
            listOf(
                context.getString(R.string.github_history_tracking_label_action),
                context.getString(R.string.github_history_refresh_label_source),
                context.getString(R.string.github_history_tracking_label_source_mode),
            )
        var requestedExpansion: Boolean? = null

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
                        GitHubTrackChangeHistoryRecordCard(
                            item = largeFontTrackChangeRecord,
                            appIconBitmap = null,
                            expanded = true,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .testTag(CARD_TAG),
                            onExpandedChange = { requestedExpansion = it },
                        )
                    }
                }
            }
        }

        val rootBounds =
            composeRule
                .onNodeWithTag(ROOT_TAG)
                .assertWidthIsEqualTo(360.dp)
                .fetchSemanticsNode()
                .boundsInRoot
        val rowBounds =
            rowLabels.map { label ->
                composeRule
                    .onNodeWithText(label)
                    .fetchSemanticsNode()
                    .also { row ->
                        assertTrue(
                            row.config.contains(SemanticsActions.OnLongClick),
                            "$label must retain AppInfoRow's long-press copy action",
                        )
                    }.boundsInRoot
            }
        val expectedGap = with(composeRule.density) { 6.dp.toPx() }
        val gapTolerance = with(composeRule.density) { 1.dp.toPx() }

        rowBounds.zipWithNext().forEach { (previous, next) ->
            val actualGap = next.top - previous.bottom
            assertTrue(previous.bottom <= next.top)
            assertTrue(
                abs(actualGap - expectedGap) <= gapTolerance,
                "AppInfoListBody must keep the compact 6dp gap",
            )
        }
        rowBounds.forEach { bounds ->
            assertTrue(bounds.left >= rootBounds.left)
            assertTrue(bounds.right <= rootBounds.right)
        }

        val repositoryValueBounds =
            composeRule
                .onNodeWithText(LONG_REPOSITORY, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        val packageValueBounds =
            composeRule
                .onNodeWithText(SHORT_PACKAGE, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        assertTrue(
            repositoryValueBounds.height > packageValueBounds.height,
            "The two-line repository value must remain multiline at 360dp and 1.5x font scale",
        )
        assertTrue(repositoryValueBounds.right <= rootBounds.right)

        composeRule.onNodeWithText(rowLabels.first()).performClick()
        composeRule.runOnIdle { assertNull(requestedExpansion) }
        composeRule.onNodeWithText(LONG_APP_LABEL).performClick()
        composeRule.runOnIdle { assertEquals(false, requestedExpansion) }
    }
}

private val largeFontTrackChangeRecord =
    GitHubTrackChangeHistoryUiRecord(
        record =
            GitHubTrackChangeHistoryRecord(
                id = "large-font-history",
                trackId = LONG_TRACK_ID,
                previousTrackId = LONG_PREVIOUS_TRACK_ID,
                action = GitHubTrackChangeHistoryAction.Updated,
                source = GitHubTrackChangeHistorySource.Import,
                changedAtMillis = 1_000L,
                owner = "owner",
                repo = LONG_REPOSITORY.removePrefix("owner/"),
                repoUrl = "https://github.com/$LONG_REPOSITORY",
                packageName = SHORT_PACKAGE,
                appLabel = LONG_APP_LABEL,
                sourceMode = GitHubTrackedSourceMode.GitHubRepository,
                changedFields =
                    listOf(
                        GitHubTrackChangeField.Repository,
                        GitHubTrackChangeField.PackageName,
                        GitHubTrackChangeField.LatestReleaseDownloadButton,
                    ),
            ),
    )

private const val ROOT_TAG = "github-history-info-list-large-font-root"
private const val CARD_TAG = "github-history-info-list-large-font-card"
private const val LONG_APP_LABEL =
    "A deliberately long tracked application name for compact history layout"
private const val LONG_REPOSITORY =
    "owner/a-deliberately-long-repository-name-for-compact-history-layout-verification"
private const val SHORT_PACKAGE = "os.kei.history.sample"
private const val LONG_TRACK_ID =
    "owner/a-deliberately-long-repository-name|os.kei.history.current.application"
private const val LONG_PREVIOUS_TRACK_ID =
    "owner/a-deliberately-long-previous-repository-name|os.kei.history.previous.application"
