package os.kei.ui.page.main.widget

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.chrome.AppTopBarSearchField
import os.kei.ui.page.main.widget.chrome.AppTopBarSection
import os.kei.ui.page.main.widget.support.LocalTextCopyExpandedOverride
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * The search-shell baseline is the only design-system screenshot containing a [
 * androidx.compose.foundation.text.BasicTextField], and it lives apart from
 * [AppDesignSystemScreenshotTest] because of that.
 *
 * A single-line `BasicTextField` settles its height over two layout passes under Robolectric: the
 * first pass reports the ascent-to-descent height (49px at this density), the second the real line
 * height (62px). The field centres its text vertically, so the unsettled pass draws the glyphs
 * 6.5px lower and the search bar host then clips their bottom rows -- a 1315-pixel diff against
 * this baseline.
 *
 * Which pass the standalone `captureRoboImage { }` helper snapshots depends on JVM-global state
 * left by tests that ran earlier in the same process; typing into any text field elsewhere in the
 * suite is enough to flip it. That made this baseline pass when the class ran alone and fail in a
 * whole-suite `verifyRoborazziDebug`. Capturing through the compose rule with an explicit
 * [androidx.compose.ui.test.junit4.ComposeContentTestRule.waitForIdle] forces the settled pass in
 * both cases, and reproduces this baseline byte for byte.
 *
 * The rule cannot be hoisted into [AppDesignSystemScreenshotTest]: its host changes what the
 * standalone helper renders, which shifts all twelve of the other baselines. Keep the two capture
 * styles in separate classes, and do not convert this test back to `captureRoboImage { }` without
 * re-checking a whole-suite verify run.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = Application::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class AppTopBarSearchShellScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun topBarSearchShellLight() {
        composeRule.setContent {
            CompositionLocalProvider(LocalTextCopyExpandedOverride provides false) {
                MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                    Box(
                        modifier =
                            Modifier
                                .background(Color(0xFFF3F4F6))
                                .padding(16.dp),
                    ) {
                        AppTopBarSection(
                            title = "",
                            largeTitle = "图鉴",
                            scrollBehavior = MiuixScrollBehavior(),
                            color = Color.Transparent,
                            searchBarVisible = true,
                            searchBarAnimationLabelPrefix = "screenshotTopBar",
                        ) {
                            AppTopBarSearchField(
                                value = "星野",
                                onValueChange = {},
                                label = "搜索学生 / NPC / 卫星",
                                modifier = Modifier.padding(horizontal = AppChromeTokens.searchFieldHorizontalPadding),
                            )
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule
            .onRoot()
            .captureRoboImage(filePath = "src/test/screenshots/design-system/topbar_search_shell_light.png")
    }
}
