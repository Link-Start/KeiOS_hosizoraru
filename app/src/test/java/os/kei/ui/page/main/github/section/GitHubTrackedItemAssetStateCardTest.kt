package os.kei.ui.page.main.github.section

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.math.abs
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import os.kei.R
import os.kei.ui.page.main.github.GitHubStatusPalette
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = GitHubTrackedItemAssetStateCardTestApp::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class GitHubTrackedItemAssetStateCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loadingCardKeepsLegacySpacingAndOneCompactProgressNodeAtLargeFont() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val title = application.getString(R.string.github_asset_loading_title)
        val summary = application.getString(R.string.github_asset_loading_summary)

        setLargeFontContent {
            GitHubTrackedItemAssetLoadingCard(
                alwaysLatestReleaseDownload = true,
                targetAccent = GitHubStatusPalette.Update,
                isDark = false,
                modifier =
                    Modifier
                        .width(360.dp)
                        .testTag(LOADING_CARD_TAG),
            )
        }

        composeRule.onNodeWithTag(LOADING_CARD_TAG).assertWidthIsEqualTo(360.dp)
        composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(0)
        composeRule.onAllNodes(progressMatcher, useUnmergedTree = true).assertCountEquals(1)
        composeRule
            .onNode(progressMatcher, useUnmergedTree = true)
            .assertWidthIsEqualTo(18.dp)
            .assertHeightIsEqualTo(18.dp)

        val cardBounds = composeRule.onNodeWithTag(LOADING_CARD_TAG).fetchSemanticsNode().boundsInRoot
        val titleBounds =
            composeRule
                .onNodeWithText(title, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        val summaryBounds =
            composeRule
                .onNodeWithText(summary, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot

        assertDpDistance(
            actualPx = summaryBounds.top - titleBounds.bottom,
            expected = 2.dp,
            message = "Loading title and summary must retain the legacy compact gap",
        )
        assertDpDistance(
            actualPx = cardBounds.bottom - summaryBounds.bottom,
            expected = 12.dp,
            message = "An empty feature-card body must not add bottom padding",
        )
        assertTrue(titleBounds.left >= cardBounds.left)
        assertTrue(summaryBounds.right <= cardBounds.right)
    }

    @Test
    fun errorCardKeepsTheCompleteMultilineMessageAndLegacyBottomRhythm() {
        setLargeFontContent {
            GitHubTrackedItemAssetErrorCard(
                assetError = LONG_ERROR,
                isDark = false,
                modifier =
                    Modifier
                        .width(360.dp)
                        .testTag(ERROR_CARD_TAG),
            )
        }

        composeRule.onNodeWithTag(ERROR_CARD_TAG).assertWidthIsEqualTo(360.dp)
        composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(0)
        composeRule.onAllNodes(progressMatcher, useUnmergedTree = true).assertCountEquals(0)

        val application = ApplicationProvider.getApplicationContext<Application>()
        val title = application.getString(R.string.github_asset_error_title)
        val cardBounds = composeRule.onNodeWithTag(ERROR_CARD_TAG).fetchSemanticsNode().boundsInRoot
        val titleBounds =
            composeRule
                .onNodeWithText(title, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        val errorBounds =
            composeRule
                .onNodeWithText(LONG_ERROR, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot

        assertDpDistance(
            actualPx = errorBounds.top - titleBounds.bottom,
            expected = 6.dp,
            message = "Error title and details must retain the legacy compact gap",
        )
        assertDpDistance(
            actualPx = cardBounds.bottom - errorBounds.bottom,
            expected = 12.dp,
            message = "An empty feature-card body must not add bottom padding",
        )
        with(composeRule.density) {
            assertTrue(
                errorBounds.height.toDp() > 80.dp,
                "Four explicit error lines must remain visible instead of being clamped to two: $errorBounds",
            )
        }
    }

    private fun setLargeFontContent(content: @Composable () -> Unit) {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density = density.density, fontScale = 1.5f),
                ) {
                    Box { content() }
                }
            }
        }
    }

    private fun assertDpDistance(
        actualPx: Float,
        expected: androidx.compose.ui.unit.Dp,
        message: String,
    ) {
        val expectedPx = with(composeRule.density) { expected.toPx() }
        val tolerance = with(composeRule.density) { 1.dp.toPx() }
        assertTrue(
            abs(actualPx - expectedPx) <= tolerance,
            "$message: expected $expected, actual ${with(composeRule.density) { actualPx.toDp() }}",
        )
    }
}

class GitHubTrackedItemAssetStateCardTestApp : Application()

private val progressMatcher = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

private const val LOADING_CARD_TAG = "github-tracked-asset-loading-card"
private const val ERROR_CARD_TAG = "github-tracked-asset-error-card"
private const val LONG_ERROR =
    "The first detailed network failure line\n" +
        "The second repository response line\n" +
        "The third release parsing line\n" +
        "The fourth recovery hint line"