package os.kei.ui.page.main.mcp.dialog

import android.app.Application
import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
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

/**
 * Resetting the MCP service config is destructive, so the two buttons must not trade callbacks.
 * How the dialog itself shows, titles and dismisses is `LiquidGlassDialogTest` in ui-liquid-glass.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = Application::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class McpConfirmDialogsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun resetConfigCancelDismissesAndResetConfirms() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var confirmCount = 0
        var dismissCount = 0
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                CompositionLocalProvider(LocalTransitionAnimationsEnabled provides false) {
                    LiquidGlassDialog(show = true, title = "Reset") {
                        McpResetConfirmDialogActions(
                            onConfirm = { confirmCount++ },
                            onDismissRequest = { dismissCount++ },
                        )
                    }
                }
            }
        }

        composeRule
            .onNode(hasText(context.getString(R.string.common_cancel)) and hasClickAction())
            .performClick()
        composeRule.runOnIdle { assertEquals(1 to 0, dismissCount to confirmCount) }
        composeRule
            .onNode(hasText(context.getString(R.string.common_reset)) and hasClickAction())
            .performClick()
        composeRule.runOnIdle { assertEquals(1 to 1, dismissCount to confirmCount) }
    }
}
