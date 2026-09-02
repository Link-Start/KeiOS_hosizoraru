package os.kei.ui.page.main.widget.chrome

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.composables.icons.lucide.R as LucideR
import androidx.compose.ui.res.vectorResource
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
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
 * A two-tab category bar is the width of its labels, not the width of the page.
 *
 * Filling is right for four or five tabs, where the width divides into recognisable buttons; with two it
 * divides into two halves of the screen, and one tab across ~200dp reads as a section header rather than a
 * switch. That is what the merged Calendar/Banners bar looked like on a phone.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = TabbedPageCategoryBarWidthTestApp::class,
    sdk = [35],
    qualifiers = "w420dp-h900dp-xhdpi",
)
class TabbedPageCategoryBarWidthTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `two tabs sized to themselves leave most of the page alone`() {
        setContent(barSizedToTabs = true)

        val (start, end) = barBounds()
        val width = end - start
        // Half the page would be ~196dp per tab. The pill is comfortably under that for both together.
        assertTrue(width < 260.dp, "expected a content-sized pill, got $width")
        assertTrue(width > 100.dp, "two tabs still need room for their labels, got $width")
        // Centred, because nothing else is on the row to align against.
        val leading = start
        val trailing = 420.dp - end
        assertTrue(
            abs(leading.value - trailing.value) <= 1.5f,
            "expected a centred bar, got $leading before and $trailing after",
        )
    }

    @Test
    fun `the default still fills the row, which is what four or five tabs want`() {
        setContent(barSizedToTabs = false)

        val (start, end) = barBounds()
        // 420dp less the page's own 14dp margins.
        assertTrue(end - start > 380.dp, "expected a filled bar, got ${end - start}")
        assertTrue(start < 20.dp, "a filled bar starts at the page margin, got $start")
    }

    private fun setContent(barSizedToTabs: Boolean) {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                Box(modifier = Modifier.fillMaxSize()) {
                    TabbedPageBottomChrome(
                        visible = true,
                        navigationBarBottom = 0.dp,
                        categories = TestCategory.entries.toList(),
                        selectedPage = 0,
                        selectedPagePosition = null,
                        selectedPageProvider = { 0 },
                        searchExpanded = false,
                        searchQuery = "",
                        onSearchQueryChange = {},
                        onSearchExpandedChange = {},
                        searchIcon = androidx.compose.ui.graphics.vector.ImageVector.vectorResource(
                            LucideR.drawable.lucide_ic_search,
                        ),
                        searchContentDescription = "",
                        searchPlaceholder = "",
                        searchEnabled = false,
                        backdrop = rememberLayerBackdrop(),
                        isLiquidEffectEnabled = false,
                        onSelectCategory = {},
                        onExpandDock = {},
                        labelPrefix = LABEL_PREFIX,
                        barSizedToTabs = barSizedToTabs,
                    )
                }
            }
        }
    }

    /** The bar's own bounds, read off the two tabs it contains. */
    private fun barBounds(): Pair<Dp, Dp> {
        val first = tabBounds(0)
        val last = tabBounds(TestCategory.entries.lastIndex)
        return first.first to last.second
    }

    private fun tabBounds(index: Int): Pair<Dp, Dp> {
        val bounds =
            composeRule
                .onNodeWithTag(tabbedPageCategoryTabTestTag(LABEL_PREFIX, index))
                .fetchSemanticsNode()
                .boundsInRoot
        return with(composeRule.density) { bounds.left.toDp() to bounds.right.toDp() }
    }
}

private const val LABEL_PREFIX = "test_tabbed_page"

private enum class TestCategory(
    override val iconRes: Int,
    override val labelRes: Int,
) : TabbedPageCategory {
    Calendar(LucideR.drawable.lucide_ic_calendar, os.kei.R.string.ba_calendar_tab),
    Banners(LucideR.drawable.lucide_ic_mail, os.kei.R.string.ba_pool_tab),
}

class TabbedPageCategoryBarWidthTestApp : Application()
