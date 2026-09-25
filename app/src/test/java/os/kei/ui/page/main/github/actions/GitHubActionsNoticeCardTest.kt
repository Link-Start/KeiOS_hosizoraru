package os.kei.ui.page.main.github.actions

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import os.kei.ui.page.main.widget.glass.LocalLiquidParentBackdrop
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = Application::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class GitHubActionsNoticeCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun bodyTypographyPaddingAndLongTextStayComplete() {
        val longText =
            "This complete GitHub Actions status message wraps across several lines without truncation or ellipsis."

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                Column {
                    GitHubActionsNoticeCard(
                        text = "Single line notice",
                        accent = Color.Gray,
                        isDark = false,
                        modifier = Modifier.width(180.dp).testTag("single-line-notice"),
                    )
                    GitHubActionsNoticeCard(
                        text = longText,
                        accent = Color.Gray,
                        isDark = false,
                        modifier = Modifier.width(180.dp).testTag("long-notice"),
                    )
                }
            }
        }

        composeRule
            .onNodeWithTag("single-line-notice")
            .assertWidthIsEqualTo(180.dp)
            .assertHeightIsEqualTo(40.33.dp)
        composeRule.onNodeWithText(longText).assertExists()
        val longHeight =
            with(composeRule.density) {
                composeRule.onNodeWithTag("long-notice").fetchSemanticsNode().boundsInRoot.height.toDp()
            }
        assertTrue(longHeight > 40.33.dp)
    }

    @Test
    fun parentBackdropAndStandaloneFallbackKeepTheSameNoticeGeometry() {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                Column {
                    CompositionLocalProvider(LocalLiquidParentBackdrop provides null) {
                        GitHubActionsNoticeCard(
                            text = "Fallback notice",
                            accent = Color.Gray,
                            isDark = false,
                            modifier = Modifier.width(180.dp).testTag("fallback-notice"),
                        )
                    }
                    val backdrop = rememberLayerBackdrop()
                    Box(modifier = Modifier.width(180.dp)) {
                        Box(
                            modifier =
                                Modifier
                                    .matchParentSize()
                                    .background(Color.White)
                                    .layerBackdrop(backdrop),
                        )
                        CompositionLocalProvider(LocalLiquidParentBackdrop provides backdrop) {
                            GitHubActionsNoticeCard(
                                text = "Parent material notice",
                                accent = Color.Gray,
                                isDark = false,
                                modifier = Modifier.testTag("parent-material-notice"),
                            )
                        }
                    }
                }
            }
        }

        composeRule
            .onNodeWithTag("fallback-notice")
            .assertWidthIsEqualTo(180.dp)
            .assertHeightIsEqualTo(40.33.dp)
        composeRule
            .onNodeWithTag("parent-material-notice")
            .assertWidthIsEqualTo(180.dp)
            .assertHeightIsEqualTo(40.33.dp)
        composeRule.onNodeWithText("Fallback notice").assertExists()
        composeRule.onNodeWithText("Parent material notice").assertExists()
    }
}
