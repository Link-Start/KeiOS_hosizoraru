package os.kei.ui.page.main.widget.chrome

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = AppPageTwoColumnPanesTestApp::class,
    sdk = [35],
    // The tablet AVD in landscape, which is the geometry this container exists for.
    qualifiers = "w1280dp-h800dp-xhdpi",
)
class AppPageTwoColumnPanesTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun bothPanesTakeHalfTheContentWidthAndTheWholeHeightBetweenTheInsets() {
        setPanes(innerPadding = PaddingValues(top = 96.dp, bottom = 24.dp))

        val primary = paneBounds(PRIMARY_TAG)
        val secondary = paneBounds(SECONDARY_TAG)

        // Halves, not "roughly a split": a caller cannot weight one pane over the other, because the weight
        // belongs to the container.
        assertDpEquals(primary.width, secondary.width, "pane widths")
        assertDpEquals(primary.height, secondary.height, "pane heights")

        // Leading pane on the left, with exactly the page's column gap between them.
        assertTrue(primary.left < secondary.left, "primary must be the leading pane")
        assertDpEquals(secondary.left - primary.right, AppPageColumnGap, "column gap")

        // A two-column cap is wider than this window, so the gutter bottoms out at the wide edge inset:
        // 14dp of page padding plus 14dp of it, on each outer edge. The inside edge is the gap above.
        assertDpEquals(primary.left, 28.dp, "start inset")
        assertDpEquals(1280.dp - secondary.right, 28.dp, "end inset")
        assertDpEquals(primary.width, (1280.dp - 56.dp - AppPageColumnGap) / 2f, "pane width")

        // The point of this container: the panes are as tall as the page, not as tall as their content.
        // 800dp of window, less the chrome insets it was handed and the rhythm it adds to them.
        assertDpEquals(primary.height, 800.dp - (96.dp + 12.dp) - (24.dp + 16.dp), "pane height")
    }

    @Test
    fun aSingleColumnCapStillLeavesTheSurplusAsGutter() {
        // Not a shape any page asks for, but it pins that the container centres against whatever cap it is
        // given rather than assuming it has been widened -- which is what keeps a page's chrome and its
        // panes agreeing on where the content column is.
        setPanes(
            innerPadding = PaddingValues(),
            maxContentWidth = AppPageContentMaxWidth,
        )

        val primary = paneBounds(PRIMARY_TAG)
        val secondary = paneBounds(SECONDARY_TAG)

        // (1280 - 720) / 2 of gutter, plus the page's own 14dp.
        assertDpEquals(primary.left, 294.dp, "gutter start")
        assertDpEquals(1280.dp - secondary.right, 294.dp, "gutter end")
        // The cap is the outer bound of the content *including* the page padding, so the halves are
        // what is left of it after both edges and the gap between them.
        assertDpEquals(primary.width, (720.dp - 28.dp - AppPageColumnGap) / 2f, "pane width")
    }

    private fun setPanes(
        innerPadding: PaddingValues,
        maxContentWidth: Dp = appPageContentMaxWidthFor(columnCount = 2),
    ) {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                AppPageTwoColumnPanes(
                    innerPadding = innerPadding,
                    modifier = Modifier.fillMaxSize(),
                    maxContentWidth = maxContentWidth,
                    primary = { TaggedPane(PRIMARY_TAG) },
                    secondary = { TaggedPane(SECONDARY_TAG) },
                )
            }
        }
    }

    @Composable
    private fun TaggedPane(tag: String) {
        Box(modifier = Modifier.fillMaxSize().testTag(tag))
    }

    private fun paneBounds(tag: String): PaneBounds {
        val bounds = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        return with(composeRule.density) {
            PaneBounds(
                left = bounds.left.toDp(),
                right = bounds.right.toDp(),
                width = bounds.width.toDp(),
                height = bounds.height.toDp(),
            )
        }
    }

    private fun assertDpEquals(
        actual: Dp,
        expected: Dp,
        label: String,
    ) {
        assertTrue(
            abs(actual.value - expected.value) <= 0.75f,
            "$label: expected $expected, got $actual",
        )
    }

    private data class PaneBounds(
        val left: Dp,
        val right: Dp,
        val width: Dp,
        val height: Dp,
    )
}

private const val PRIMARY_TAG = "two-column-pane-primary"
private const val SECONDARY_TAG = "two-column-pane-secondary"

class AppPageTwoColumnPanesTestApp : Application()
