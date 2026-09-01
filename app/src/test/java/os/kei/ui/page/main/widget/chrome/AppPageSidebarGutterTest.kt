package os.kei.ui.page.main.widget.chrome

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
 * Page content must be clear of the sidebar rail.
 *
 * The rail floats *over* the pager rather than insetting it — that is what lets Home's artwork run the full
 * width of the window — so every list is responsible for pushing its own content past it. The containers
 * took one symmetric gutter for both edges, which is right for centring and blind to the rail, and every
 * card on OS, MCP, GitHub and BA slid underneath it the moment the navigation moved to the side.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = AppPageSidebarGutterTestApp::class,
    sdk = [35],
    qualifiers = "w1280dp-h800dp-xhdpi",
)
class AppPageSidebarGutterTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `a single column starts past the rail and ends at the page edge`() {
        setContent(AppNavigationPlacement.Sidebar) {
            AppPageLazyColumn(
                innerPadding = PaddingValues(),
                state = rememberLazyListState(),
                modifier = Modifier.fillMaxSize(),
            ) {
                item { Row(CONTENT_TAG) }
            }
        }

        val bounds = contentBounds(CONTENT_TAG)
        // The rail is counted exactly once. `appContentWidth` already excludes it, so the column is centred
        // in the 1000dp beside the rail -- (1000 - 720) / 2 = 140 of gutter each side -- and only the
        // leading edge then adds the rail itself. 14 + 140 + 280 against 14 + 140.
        assertDp(bounds.first, AppChromeTokens.pageHorizontalPadding + 140.dp + AppSidebarWidth, "start")
        assertDp(1280.dp - bounds.second, AppChromeTokens.pageHorizontalPadding + 140.dp, "end")
        // Which leaves the column exactly the width it has anywhere else -- the rail costs the page its
        // gutter, never its content.
        assertDp(bounds.second - bounds.first, AppPageContentMaxWidth - 28.dp, "column width")
    }

    @Test
    fun `two columns start past the rail too`() {
        setContent(AppNavigationPlacement.Sidebar) {
            CompositionLocalProvider(
                LocalAppPageContentMaxWidth provides appPageContentMaxWidthFor(columnCount = 2),
            ) {
                AppPageTwoColumnLists(
                    innerPadding = PaddingValues(),
                    primaryState = rememberLazyListState(),
                    secondaryState = rememberLazyListState(),
                    modifier = Modifier.fillMaxSize(),
                    primary = { item { Row(CONTENT_TAG) } },
                    secondary = { item { Row(SECONDARY_TAG) } },
                )
            }
        }

        // A widened cap bottoms the gutter out at the wide edge inset, so 14 + 14 on the trailing edge and
        // the rail on top of that on the leading one.
        val primary = contentBounds(CONTENT_TAG)
        val secondary = contentBounds(SECONDARY_TAG)
        assertDp(primary.first, 28.dp + AppSidebarWidth, "start")
        assertDp(1280.dp - secondary.second, 28.dp, "end")
        // Still halves of what is left, so the rail does not come out of one column only.
        assertDp(primary.second - primary.first, secondary.second - secondary.first, "column widths")
    }

    @Test
    fun `without a rail both edges are the same, which is every phone`() {
        setContent(AppNavigationPlacement.Bottom) {
            AppPageLazyColumn(
                innerPadding = PaddingValues(),
                state = rememberLazyListState(),
                modifier = Modifier.fillMaxSize(),
            ) {
                item { Row(CONTENT_TAG) }
            }
        }

        val bounds = contentBounds(CONTENT_TAG)
        assertDp(bounds.first, 1280.dp - bounds.second, "symmetric without a rail")
    }

    @Composable
    private fun Row(tag: String) {
        Box(modifier = Modifier.fillMaxWidth().height(40.dp).testTag(tag))
    }

    private fun setContent(
        placement: AppNavigationPlacement,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                CompositionLocalProvider(LocalAppNavigationPlacement provides placement) {
                    content()
                }
            }
        }
    }

    private fun contentBounds(tag: String): Pair<Dp, Dp> {
        val bounds = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
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

private const val CONTENT_TAG = "page-content-row"
private const val SECONDARY_TAG = "page-content-row-secondary"

class AppPageSidebarGutterTestApp : Application()
