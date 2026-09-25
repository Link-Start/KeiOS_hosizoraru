package os.kei.ui.page.main.os.components

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
 * Deleting an OS card cannot be undone, so the two buttons must not trade callbacks.
 * How the dialog itself shows, titles and dismisses is `LiquidGlassDialogTest` in ui-liquid-glass.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = Application::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class OsDeleteConfirmDialogsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun cancelDismissesAndDeleteConfirms() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var deleteCount = 0
        var dismissCount = 0
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                CompositionLocalProvider(LocalTransitionAnimationsEnabled provides false) {
                    LiquidGlassDialog(show = true, title = "Delete saved item?") {
                        OsDeleteConfirmDialogActions(
                            onDismissRequest = { dismissCount++ },
                            onConfirmDelete = { deleteCount++ },
                        )
                    }
                }
            }
        }

        composeRule
            .onNode(hasText(context.getString(R.string.common_cancel)) and hasClickAction())
            .performClick()
        composeRule.runOnIdle { assertEquals(1 to 0, dismissCount to deleteCount) }
        composeRule
            .onNode(hasText(context.getString(R.string.common_delete)) and hasClickAction())
            .performClick()
        composeRule.runOnIdle { assertEquals(1 to 1, dismissCount to deleteCount) }
    }
}
