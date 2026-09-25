package os.kei.ui.page.main.sync

import android.app.Application
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = Application::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class WebDavLiquidTextFieldBackHandlerTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun focusedFieldConsumesBackBeforeTheRouteHandler() {
        var routeBackCount = 0
        lateinit var backDispatcher: OnBackPressedDispatcher

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val dispatcherOwner = checkNotNull(LocalOnBackPressedDispatcherOwner.current)
                SideEffect { backDispatcher = dispatcherOwner.onBackPressedDispatcher }
                BackHandler { routeBackCount++ }
                WebDavLiquidTextField(
                    value = "",
                    onValueChange = {},
                    label = "WebDAV test field",
                )
            }
        }

        composeRule.onAllNodes(hasSetTextAction(), useUnmergedTree = true)[0].performClick()
        composeRule.waitForIdle()

        composeRule.runOnIdle { backDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertEquals(0, routeBackCount)

        composeRule.runOnIdle { backDispatcher.onBackPressed() }
        assertEquals(1, routeBackCount)
    }
}
