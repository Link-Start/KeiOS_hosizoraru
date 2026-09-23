package os.kei.ui.page.main.mcp.dialog

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
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

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = McpConfirmDialogsTestApp::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class McpConfirmDialogsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun resetConfigDialogPreservesShowCopyAndDualActions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        verifyConfirmDialog(
            title = context.getString(R.string.mcp_action_reset_service_config),
            summary = context.getString(R.string.mcp_reset_service_config_confirm_summary),
            cancelLabel = context.getString(R.string.common_cancel),
            confirmLabel = context.getString(R.string.common_reset),
        ) { onConfirm, onDismissRequest ->
            McpResetConfirmDialogActions(
                onConfirm = onConfirm,
                onDismissRequest = onDismissRequest,
            )
        }
    }

    @Test
    fun resetTokenDialogPreservesShowCopyAndDualActions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        verifyConfirmDialog(
            title = context.getString(R.string.mcp_action_reset_token),
            summary = context.getString(R.string.mcp_reset_token_confirm_summary),
            cancelLabel = context.getString(R.string.common_cancel),
            confirmLabel = context.getString(R.string.common_reset),
        ) { onConfirm, onDismissRequest ->
            McpResetConfirmDialogActions(
                onConfirm = onConfirm,
                onDismissRequest = onDismissRequest,
            )
        }
    }

    private fun verifyConfirmDialog(
        title: String,
        summary: String,
        cancelLabel: String,
        confirmLabel: String,
        actions: @Composable (() -> Unit, () -> Unit) -> Unit,
    ) {
        val show = mutableStateOf(false)
        var confirmCount = 0
        var dismissCount = 0
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                CompositionLocalProvider(LocalTransitionAnimationsEnabled provides false) {
                    LiquidGlassDialog(
                        show = show.value,
                        title = title,
                        summary = summary,
                    ) {
                        actions(
                            { confirmCount++ },
                            { dismissCount++ },
                        )
                    }
                }
            }
        }

        composeRule.onAllNodes(hasText(title)).assertCountEquals(0)
        composeRule.runOnIdle { show.value = true }
        composeRule.onNode(hasText(title) and isHeading()).assertIsDisplayed()
        composeRule.onNode(hasText(summary)).assertIsDisplayed()
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(2)

        composeRule.onNode(hasText(cancelLabel) and hasClickAction()).performClick()
        composeRule.onNode(hasText(confirmLabel) and hasClickAction()).performClick()
        composeRule.runOnIdle {
            assertEquals(1, dismissCount)
            assertEquals(1, confirmCount)
            show.value = false
        }
        composeRule.onAllNodes(hasText(title)).assertCountEquals(0)
    }
}

class McpConfirmDialogsTestApp : Application()
