package os.kei.ui.page.main.ba

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import os.kei.ui.page.main.widget.chrome.AppPageScaffold
import os.kei.ui.page.main.widget.chrome.appPageContentMaxWidthFor
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * In two columns the server panel is the lists' shared header, inside the edge-stack keep-alive box, so
 * the top bar's inset has to be part of the stack line. When it was not, the line sat at the window's
 * top edge and the panel was drawn behind the top bar.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = Application::class,
    sdk = [35],
    // The tablet AVD in landscape: wide enough that the page takes its two-column shape.
    qualifiers = "w1280dp-h800dp-xhdpi",
)
class BaCalendarPoolTwoColumnLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theServerPanelSitsBelowTheTopBar() {
        var topBarBottom = Dp.Unspecified

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                AppPageScaffold(
                    title = "Calendar and banners",
                    contentMaxWidth = appPageContentMaxWidthFor(columnCount = 2),
                ) { innerPadding ->
                    // The scaffold measures its top bar and hands the content that height as top padding,
                    // which is exactly where the bar ends.
                    val barBottom = innerPadding.calculateTopPadding()
                    SideEffect { topBarBottom = barBottom }
                    BaCalendarPoolTwoColumnLayout(
                        innerPadding = innerPadding,
                        primaryState = rememberLazyListState(),
                        secondaryState = rememberLazyListState(),
                        nestedScrollConnection = remember { object : NestedScrollConnection {} },
                        backdrop = rememberLayerBackdrop(),
                        serverOptions = listOf("CN", "Global", "JP"),
                        serverIndex = 0,
                        syncText = "synced",
                        syncTextColor = Color.Blue,
                        showServerPopup = false,
                        serverPopupAnchorBounds = null,
                        onServerPopupChange = {},
                        onServerPopupAnchorBoundsChange = {},
                        onServerSelected = {},
                        primary = { placeholderCards() },
                        secondary = { placeholderCards() },
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val panelTop =
            with(composeRule.density) {
                composeRule
                    .onNodeWithTag(BA_CALENDAR_POOL_SERVER_PANEL_TEST_TAG)
                    .fetchSemanticsNode()
                    .boundsInRoot
                    .top
                    .toDp()
            }

        assertTrue(topBarBottom > 0.dp, "the scaffold must have measured a top bar, got $topBarBottom")
        assertTrue(
            panelTop >= topBarBottom,
            "the server panel starts at $panelTop, above the top bar's bottom at $topBarBottom",
        )
    }

    private fun LazyListScope.placeholderCards() {
        items(count = 4) {
            Box(modifier = Modifier.fillMaxWidth().height(120.dp))
        }
    }
}
