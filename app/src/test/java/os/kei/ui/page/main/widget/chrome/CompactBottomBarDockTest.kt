package os.kei.ui.page.main.widget.chrome

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
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
class CompactBottomBarDockTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun enabledDockExposesAndRunsClickAction() {
        var clicks = 0
        setDock(enabled = true, onClick = { clicks++ })

        composeRule.onNode(hasClickAction()).performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun disabledDockRemovesClickAndChildSemantics() {
        setDock(enabled = false)

        composeRule.onAllNodes(hasClickAction()).assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("Open dock").assertCountEquals(0)
    }

    private fun setDock(
        enabled: Boolean,
        onClick: () -> Unit = {},
    ) {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                CompactBottomBarDock(
                    backdrop = null,
                    onClick = onClick,
                    enabled = enabled,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Basic.Check,
                        contentDescription = "Open dock",
                    )
                }
            }
        }
    }
}
