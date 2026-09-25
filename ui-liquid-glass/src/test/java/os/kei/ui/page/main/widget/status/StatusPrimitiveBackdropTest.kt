package os.kei.ui.page.main.widget.status

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import os.kei.ui.page.main.widget.core.AppSupportingBlock
import os.kei.ui.page.main.widget.glass.LocalLiquidControlsEnabled
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
class StatusPrimitiveBackdropTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun defaultSupportingBlockKeepsOriginalSupportingDensity() {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                AppSupportingBlock(
                    text = "Default supporting density",
                    modifier = Modifier.testTag("default-supporting-density"),
                )
            }
        }

        composeRule
            .onNodeWithTag("default-supporting-density")
            .assertHeightIsEqualTo(33.33.dp)
        composeRule.onNodeWithText("Default supporting density").assertExists()
    }

    @Test
    fun standaloneNullParentKeepsRenderingAndClickSemantics() {
        var supportingBlockClicks = 0

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                CompositionLocalProvider(
                    LocalLiquidControlsEnabled provides true,
                    LocalLiquidParentBackdrop provides null,
                ) {
                    Column {
                        StatusPill(
                            label = "Ready",
                            color = Color(0xFF3B82F6),
                            modifier = Modifier.testTag("standalone-status-pill"),
                        )
                        AppSupportingBlock(
                            text = "Open supporting details",
                            modifier = Modifier.testTag("standalone-supporting-block"),
                            onClick = { supportingBlockClicks++ },
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("standalone-status-pill").assertExists().assertHeightIsAtLeast(20.dp)
        composeRule.onNodeWithText("Ready").assertExists()
        composeRule
            .onNodeWithTag("standalone-supporting-block")
            .assertExists()
            .assertHasClickAction()
            .performClick()
        composeRule.onNodeWithText("Open supporting details").assertExists()
        composeRule.runOnIdle {
            assertTrue(supportingBlockClicks == 1)
        }
    }

}
