package os.kei.ui.page.main.widget.chrome

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.math.abs
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import os.kei.ui.page.main.widget.support.LocalTextCopyExpandedOverride
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * The two ends of the top row must take the same gutter.
 *
 * They did not (ab2103dc9): the first Pad round gave the trailing actions [appTopBarChromeGutter] and left the
 * navigation icon on the bare window margin, so a pushed route in landscape put its back button 242dp outside
 * the column its own page occupied. That is invisible on a phone, where the gutter is 0dp, and small in
 * portrait, where it is 40dp. Only the 280dp landscape gutter makes it obvious, so this renders the real top
 * bar on a 1280dp landscape panel and measures both ends.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = Application::class,
    sdk = [35],
    qualifiers = "w1280dp-h800dp-land-xhdpi",
)
class AppTopBarChromeGutterTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `a pushed route's back button and its actions sit the same gutter in from each edge`() {
        setTopBar(AppNavigationPlacement.Bottom)

        val (leading, trailing) = insets()
        assertTrue(
            abs(leading - trailing) <= 1f,
            "navigation icon starts ${leading}px in, actions end ${trailing}px in; the two ends must agree",
        )
        // Not vacuously equal: on this panel both ends are the 14dp margin plus the 280dp content gutter.
        val expected = with(composeRule.density) { (AppChromeTokens.topBarHorizontalPadding + 280.dp).toPx() }
        assertTrue(abs(leading - expected) <= 1f, "expected both ends ${expected}px in, got ${leading}px")
    }

    @Test
    fun `at the top tab bar both ends span the window and still agree`() {
        setTopBar(AppNavigationPlacement.Top)

        val (leading, trailing) = insets()
        assertTrue(abs(leading - trailing) <= 1f, "leading ${leading}px, trailing ${trailing}px")
        val expected = with(composeRule.density) { AppTopBarRegularEdgePadding.toPx() }
        assertTrue(abs(leading - expected) <= 1f, "expected both ends ${expected}px in, got ${leading}px")
    }

    private fun setTopBar(placement: AppNavigationPlacement) {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalTextCopyExpandedOverride provides false,
                LocalAppNavigationPlacement provides placement,
            ) {
                MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                    AppTopBarSection(
                        title = "Release",
                        modifier = Modifier.fillMaxWidth(),
                        navigationIcon = {
                            Box(modifier = Modifier.size(CONTROL_SIZE).testTag(NAVIGATION_TAG))
                        },
                        actions = {
                            Box(modifier = Modifier.size(CONTROL_SIZE).testTag(ACTION_TAG))
                        },
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** Pixels from the root's leading edge to the icon, and from the action's trailing edge to the root's. */
    private fun insets(): Pair<Float, Float> {
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val icon = composeRule.onNodeWithTag(NAVIGATION_TAG).fetchSemanticsNode().boundsInRoot
        val action = composeRule.onNodeWithTag(ACTION_TAG).fetchSemanticsNode().boundsInRoot
        return (icon.left - root.left) to (root.right - action.right)
    }
}

private const val NAVIGATION_TAG = "top-bar-navigation-icon"
private const val ACTION_TAG = "top-bar-action"
private val CONTROL_SIZE = 44.dp
