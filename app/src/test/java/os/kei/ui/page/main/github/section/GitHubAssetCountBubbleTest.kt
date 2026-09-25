package os.kei.ui.page.main.github.section

import android.app.Application
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import os.kei.ui.page.main.widget.glass.LocalLiquidControlsEnabled
import os.kei.ui.page.main.widget.glass.LocalLiquidParentBackdrop
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = Application::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class GitHubAssetCountBubbleTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun labelAndLoadingStatesKeepLegacyGeometryAndProgressSemantics() {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                CompositionLocalProvider(
                    LocalLiquidControlsEnabled provides true,
                    LocalLiquidParentBackdrop provides null,
                ) {
                    Row {
                        GitHubAssetCountBubble(
                            label = "7",
                            color = Color(0xFF3B82F6),
                            modifier = Modifier.testTag("asset-count"),
                        )
                        GitHubAssetCountBubble(
                            label = "pending",
                            color = Color(0xFF3B82F6),
                            modifier = Modifier.testTag("asset-loading"),
                            loading = true,
                        )
                    }
                }
            }
        }

        composeRule
            .onNodeWithTag("asset-count")
            .assertWidthIsEqualTo(28.dp)
            .assertHeightIsEqualTo(28.dp)
        composeRule.onNodeWithText("7").assertExists()
        composeRule
            .onNodeWithTag("asset-loading")
            .assertWidthIsEqualTo(28.dp)
            .assertHeightIsEqualTo(28.dp)
        composeRule.onNodeWithText("pending").assertDoesNotExist()
        composeRule
            .onNode(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo.Indeterminate,
                ),
            ).assertWidthIsEqualTo(14.dp)
            .assertHeightIsEqualTo(14.dp)
            .assertRangeInfoEquals(ProgressBarRangeInfo.Indeterminate)
    }
}
