package os.kei.ui.page.main.student.catalog.component.bgm

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
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
    application = BaGuideBgmAlbumPaneTestApp::class,
    sdk = [35],
    qualifiers = "w1280dp-h800dp-xhdpi",
)
class BaGuideBgmAlbumPaneTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `stacked, the artwork is still seventy-two percent of the width`() {
        setPane(paneWidth = 400.dp, paneHeight = 900.dp, reserved = 0.dp) {
            BaGuideBgmAlbumArtwork(
                accent = Accent,
                backdrop = null,
                modifier = Modifier.fillMaxWidth(0.72f).testTag(ARTWORK_TAG),
            )
        }

        // The default the phone has always had: width-driven, and the square follows.
        composeRule.onNodeWithTag(ARTWORK_TAG).assertWidthIsEqualTo(288.dp).assertHeightIsEqualTo(288.dp)
    }

    @Test
    fun `a pane with height to spare gives the artwork its full width`() {
        setPane(paneWidth = 300.dp, paneHeight = 800.dp, reserved = 200.dp) {
            BaGuideBgmAlbumArtwork(
                accent = Accent,
                backdrop = null,
                modifier = Modifier.weight(1f, fill = false).testTag(ARTWORK_TAG),
            )
        }

        // 600dp of slack against a 300dp pane: the width is the smaller of the two, so the square is 300.
        composeRule.onNodeWithTag(ARTWORK_TAG).assertWidthIsEqualTo(300.dp).assertHeightIsEqualTo(300.dp)
    }

    @Test
    fun `a short pane shrinks the artwork to whatever the controls left over`() {
        setPane(paneWidth = 600.dp, paneHeight = 480.dp, reserved = 240.dp) {
            BaGuideBgmAlbumArtwork(
                accent = Accent,
                backdrop = null,
                modifier = Modifier.weight(1f, fill = false).testTag(ARTWORK_TAG),
            )
        }

        // The controls take 240 of the 480, so the album gets the other 240 -- not the 600 the width would
        // have allowed. This is what keeps the transport row on screen without the pane scrolling, and why
        // the pane needs no reserve height written down anywhere: the artwork is measured last.
        composeRule.onNodeWithTag(ARTWORK_TAG).assertWidthIsEqualTo(240.dp).assertHeightIsEqualTo(240.dp)
    }

    private fun setPane(
        paneWidth: Dp,
        paneHeight: Dp,
        reserved: Dp,
        artwork: @Composable ColumnScope.() -> Unit,
    ) {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                Column(modifier = Modifier.size(width = paneWidth, height = paneHeight)) {
                    artwork()
                    // Stands in for the title, transport row and volume slider below the album.
                    Spacer(modifier = Modifier.height(reserved))
                }
            }
        }
    }
}

private val Accent = Color(0xFF2563EB)
private const val ARTWORK_TAG = "bgm-album-artwork"

class BaGuideBgmAlbumPaneTestApp : Application()
