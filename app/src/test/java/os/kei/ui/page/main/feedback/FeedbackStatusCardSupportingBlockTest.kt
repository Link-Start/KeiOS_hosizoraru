package os.kei.ui.page.main.feedback

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import os.kei.R
import os.kei.ui.page.main.widget.glass.LocalLiquidParentBackdrop
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = FeedbackStatusCardSupportingBlockTestApp::class,
    sdk = [35],
    qualifiers = "w360dp-h800dp-xxhdpi",
)
class FeedbackStatusCardSupportingBlockTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun lightStatusCardKeepsLargeFontSupportingCopyReadableAt360Dp() {
        assertLargeFontLayout(ColorSchemeMode.Light)
    }

    private fun assertLargeFontLayout(mode: ColorSchemeMode) {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val title = application.getString(R.string.feedback_issue_status_title)
        val summary = application.getString(R.string.feedback_issue_status_summary)
        val refresh = application.getString(R.string.common_refresh)

        setStatusCard(mode)

        composeRule.onNodeWithTag(CARD_HOST_TAG).assertWidthIsEqualTo(360.dp)
        val hostBounds = composeRule.onNodeWithTag(CARD_HOST_TAG).fetchSemanticsNode().boundsInRoot
        val titleBounds =
            composeRule
                .onNodeWithText(title, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        val statusBounds =
            composeRule
                .onNodeWithText(STATUS_MESSAGE, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        val refreshBounds =
            composeRule
                .onNodeWithText(refresh, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        val summaryBounds =
            composeRule
                .onNodeWithText(summary, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        val tolerance = with(composeRule.density) { 1.dp.toPx() }
        val oneLargeFontLine = with(composeRule.density) { 27.dp.toPx() }
        val headerBottom = maxOf(titleBounds.bottom, statusBounds.bottom, refreshBounds.bottom)

        assertTrue(titleBounds.right <= refreshBounds.left + tolerance)
        assertTrue(statusBounds.right <= refreshBounds.left + tolerance)
        assertTrue(
            summaryBounds.top >= headerBottom - tolerance,
            "Supporting copy must begin below the complete status header: " +
                "status=$statusBounds, refresh=$refreshBounds, supporting=$summaryBounds",
        )
        assertTrue(
            summaryBounds.height > oneLargeFontLine,
            "The complete supporting copy must wrap naturally at 360dp and 1.5x: $summaryBounds",
        )
        assertTrue(summaryBounds.left >= hostBounds.left - tolerance)
        assertTrue(summaryBounds.right <= hostBounds.right + tolerance)
        assertTrue(summaryBounds.bottom <= hostBounds.bottom + tolerance)
    }

    private fun setStatusCard(mode: ColorSchemeMode) {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(mode)) {
                val baseDensity = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(baseDensity.density, fontScale = 1.5f),
                ) {
                    val parentBackdrop = rememberLayerBackdrop()
                    Box(
                        modifier =
                            Modifier
                                .width(360.dp)
                                .testTag(CARD_HOST_TAG),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .matchParentSize()
                                    .background(MiuixTheme.colorScheme.background)
                                    .layerBackdrop(parentBackdrop),
                        )
                        CompositionLocalProvider(LocalLiquidParentBackdrop provides parentBackdrop) {
                            FeedbackStatusCard(
                                state =
                                    FeedbackIssueUiState(
                                        loading = false,
                                        statusMessage = STATUS_MESSAGE,
                                    ),
                                onRefresh = {},
                            )
                        }
                    }
                }
            }
        }
    }
}

class FeedbackStatusCardSupportingBlockTestApp : Application()

private const val CARD_HOST_TAG = "feedback-status-card-host"
private const val STATUS_MESSAGE = "Ready for local review"