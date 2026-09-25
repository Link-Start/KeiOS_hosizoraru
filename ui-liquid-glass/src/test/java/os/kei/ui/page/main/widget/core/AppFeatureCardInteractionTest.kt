package os.kei.ui.page.main.widget.core

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = Application::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class AppFeatureCardInteractionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun collapsibleCardDefaultsToHeaderOnlyToggle() {
        val expandedChanges = mutableListOf<Boolean>()

        composeRule.setContent {
            TestTheme {
                AppFeatureCard(
                    title = "Header-only card",
                    subtitle = "Default interaction",
                    collapsible = true,
                    expanded = true,
                    onExpandedChange = expandedChanges::add,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("default-card-body"),
                    )
                }
            }
        }

        composeRule.onNodeWithTag("default-card-body", useUnmergedTree = true).performTouchInput { click() }
        composeRule.runOnIdle { assertTrue(expandedChanges.isEmpty()) }

        composeRule.onNodeWithText("Header-only card", useUnmergedTree = true).performTouchInput { click() }
        composeRule.runOnIdle { assertEquals(listOf(false), expandedChanges) }
    }

    @Test
    fun surfaceClickModeTogglesFromBodyAndHeaderExactlyOnce() {
        val expandedChanges = mutableListOf<Boolean>()

        composeRule.setContent {
            TestTheme {
                AppFeatureCard(
                    title = "Surface card",
                    subtitle = "Whole card interaction",
                    collapsible = true,
                    expanded = true,
                    onExpandedChange = expandedChanges::add,
                    collapseOnSurfaceClick = true,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("surface-card-body"),
                    )
                }
            }
        }

        composeRule.onNodeWithTag("surface-card-body", useUnmergedTree = true).performTouchInput { click() }
        composeRule.runOnIdle { assertEquals(listOf(false), expandedChanges) }

        composeRule.onNodeWithText("Surface card", useUnmergedTree = true).performTouchInput { click() }
        composeRule.runOnIdle { assertEquals(listOf(false, false), expandedChanges) }
    }
}

@androidx.compose.runtime.Composable
private fun TestTheme(content: @androidx.compose.runtime.Composable () -> Unit) {
    MiuixTheme(controller = ThemeController(ColorSchemeMode.Light), content = content)
}
