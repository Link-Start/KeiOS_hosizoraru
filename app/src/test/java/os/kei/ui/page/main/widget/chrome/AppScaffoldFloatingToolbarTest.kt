package os.kei.ui.page.main.widget.chrome

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
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
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = AppScaffoldFloatingToolbarTestApp::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class AppScaffoldFloatingToolbarTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun floatingToolbarRemainsAnOverlayWithoutReservingContentPadding() {
        val toolbarVisible = mutableStateOf(false)
        var contentBottomPadding = 0.dp
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                AppScaffold(
                    floatingToolbar = {
                        if (toolbarVisible.value) {
                            Box(
                                Modifier
                                    .size(width = 240.dp, height = 62.dp)
                                    .testTag("floating-toolbar"),
                            )
                        }
                    },
                ) { padding ->
                    SideEffect { contentBottomPadding = padding.calculateBottomPadding() }
                    Box(Modifier.fillMaxSize())
                }
            }
        }

        composeRule.waitForIdle()
        val hiddenBottomPadding = contentBottomPadding
        composeRule.runOnIdle { toolbarVisible.value = true }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("floating-toolbar").assertExists()
        composeRule.runOnIdle { assertEquals(hiddenBottomPadding, contentBottomPadding) }
    }

    @Test
    fun bottomFloatingToolbarKeepsCurrentMiuixHostSpacingAboveSystemInset() {
        var density = Density(1f)
        var systemBottomInsetPx = 0
        composeRule.setContent {
            density = LocalDensity.current
            systemBottomInsetPx = WindowInsets.systemBars.getBottom(density)
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                AppScaffold(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .testTag("scaffold"),
                    floatingToolbar = {
                        Box(
                            Modifier
                                .size(width = 240.dp, height = 62.dp)
                                .testTag("floating-toolbar"),
                        )
                    },
                ) {
                    Box(Modifier.fillMaxSize())
                }
            }
        }

        composeRule.waitForIdle()
        val scaffoldBounds = composeRule.onNodeWithTag("scaffold").fetchSemanticsNode().boundsInRoot
        val toolbarBounds = composeRule.onNodeWithTag("floating-toolbar").fetchSemanticsNode().boundsInRoot
        val actualBottomGapPx = scaffoldBounds.bottom - toolbarBounds.bottom
        val expectedBottomGapPx = systemBottomInsetPx + with(density) { 4.dp.toPx() }

        assertTrue(
            abs(actualBottomGapPx - expectedBottomGapPx) <= 1f,
            "Expected MIUIX 4dp host spacing above system inset, actual=$actualBottomGapPx expected=$expectedBottomGapPx",
        )
    }

    @Test
    fun snackbarIsPlacedAboveBottomFloatingToolbar() {
        var density = Density(1f)
        composeRule.setContent {
            density = LocalDensity.current
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                AppScaffold(
                    floatingToolbar = {
                        Box(
                            Modifier
                                .size(width = 240.dp, height = 62.dp)
                                .testTag("floating-toolbar"),
                        )
                    },
                    snackbarHost = {
                        Box(
                            Modifier
                                .size(width = 220.dp, height = 48.dp)
                                .testTag("snackbar"),
                        )
                    },
                ) {
                    Box(Modifier.fillMaxSize())
                }
            }
        }

        composeRule.waitForIdle()
        val toolbarBounds = composeRule.onNodeWithTag("floating-toolbar").fetchSemanticsNode().boundsInRoot
        val snackbarBounds = composeRule.onNodeWithTag("snackbar").fetchSemanticsNode().boundsInRoot
        val gapPx = toolbarBounds.top - snackbarBounds.bottom

        assertTrue(
            gapPx >= with(density) { 3.dp.toPx() },
            "Expected snackbar above floating toolbar, gapPx=$gapPx toolbar=$toolbarBounds snackbar=$snackbarBounds",
        )
    }
}

class AppScaffoldFloatingToolbarTestApp : Application()
