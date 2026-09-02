package os.kei.ui.page.main.widget.chrome

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.math.abs
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * A page-edge floating dock is chrome, so it stays on the chrome column — reachable — on whichever edge it is.
 *
 * Following the *content* column was tried on the Pad and rejected there: a page laid out in two columns fills
 * the panel, so the dock went with it and ended up against the bezel, which is the least reachable point of a
 * 1280dp screen for a hand that is holding it. On the single-column cap it lands where the About and Settings
 * pages already put their search button, because the dock's edge spacing and `pageHorizontalPadding` are the
 * same 14dp — so one rule places every floating control on the device.
 *
 * The leading edge is separate because a sidebar rail only floats over that one. Taking the trailing gutter for
 * both put the dock *underneath* the rail whenever the grip-aware side flipped to leading, which on a tablet is
 * just holding it in the other hand.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = AppFloatingDockGutterTestApp::class,
    sdk = [35],
    qualifiers = "w1280dp-h800dp-xhdpi",
)
class AppFloatingDockGutterTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the dock sits on the chrome column, in reach, not against the bezel`() {
        setContent(AppNavigationPlacement.Top, columnCount = 1) { Dock(dockAtStart = false) }

        // 14dp of dock spacing outside the 280dp gutter a 720dp column leaves on a 1280dp panel. The same
        // 294dp the tabbed pages' bottom chrome takes, which is what makes the two line up.
        assertDp(1280.dp - dockBounds().second, AppFloatingDockEdgeSpacing + 280.dp, "end")
    }

    @Test
    fun `a page widening to two columns does not drag the dock out with it`() {
        setContent(AppNavigationPlacement.Top, columnCount = 2) { Dock(dockAtStart = false) }

        // The same 294dp as the single-column case above: the cards reach the bezel and the dock does not
        // follow. This is the one that was wrong on the Pad — GitHub, OS, MCP and BA all lay out in two lanes,
        // so all four docks had left the reachable column.
        assertDp(
            1280.dp - dockBounds().second,
            AppFloatingDockEdgeSpacing + 280.dp,
            "the dock must not move when the page behind it widens",
        )
    }

    @Test
    fun `a leading dock clears the sidebar rail rather than hiding under it`() {
        setContent(AppNavigationPlacement.Sidebar, columnCount = 1) { Dock(dockAtStart = true) }

        // `appContentWidth` already excludes the rail, so the column centres in the 1000dp beside it -- 140dp
        // of gutter -- and only the leading edge then adds the rail itself.
        val start = dockBounds().first
        assertDp(start, AppFloatingDockEdgeSpacing + 140.dp + AppSidebarWidth, "start")
        assertTrue(start >= AppSidebarWidth, "the dock must begin past the rail, not inside it")
    }

    @Test
    fun `a trailing dock beside a rail follows the narrowed column inward`() {
        setContent(AppNavigationPlacement.Sidebar, columnCount = 2) { Dock(dockAtStart = false) }

        // The rail leaves 1000dp, so the chrome column's own gutter is 140dp rather than 280dp: the dock moves
        // *in* with the column it belongs to, instead of staying put while the page narrows around it.
        assertDp(1280.dp - dockBounds().second, AppFloatingDockEdgeSpacing + 140.dp, "end")
    }

    /** The same placement the four pages use: aligned to one edge, padded on that edge only. */
    @Composable
    private fun BoxScope.Dock(dockAtStart: Boolean) {
        Box(
            modifier =
                Modifier
                    .align(if (dockAtStart) Alignment.BottomStart else Alignment.BottomEnd)
                    .padding(
                        start = appFloatingDockStartPadding(dockAtStart),
                        end = appFloatingDockEndPadding(!dockAtStart),
                    ),
        ) {
            Box(modifier = Modifier.size(DOCK_SIZE).testTag(DOCK_TAG))
        }
    }

    private fun setContent(
        placement: AppNavigationPlacement,
        columnCount: Int,
        dock: @Composable BoxScope.() -> Unit,
    ) {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                CompositionLocalProvider(
                    LocalAppNavigationPlacement provides placement,
                    LocalAppPageContentMaxWidth provides appPageContentMaxWidthFor(columnCount),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) { dock() }
                }
            }
        }
    }

    private fun dockBounds(): Pair<Dp, Dp> {
        val bounds = composeRule.onNodeWithTag(DOCK_TAG).fetchSemanticsNode().boundsInRoot
        return with(composeRule.density) { bounds.left.toDp() to bounds.right.toDp() }
    }

    private fun assertDp(
        actual: Dp,
        expected: Dp,
        label: String,
    ) {
        assertTrue(
            abs(actual.value - expected.value) <= 0.75f,
            "$label: expected $expected, got $actual",
        )
    }
}

private const val DOCK_TAG = "floating-dock"
private val DOCK_SIZE = 56.dp

class AppFloatingDockGutterTestApp : Application()
