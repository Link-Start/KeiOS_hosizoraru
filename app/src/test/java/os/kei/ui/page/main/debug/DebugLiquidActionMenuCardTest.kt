package os.kei.ui.page.main.debug

import android.app.Application
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import os.kei.R
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = Application::class,
    sdk = [35],
    qualifiers = "zh-rCN-w360dp-h800dp-xxhdpi",
)
class DebugLiquidActionMenuCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun nestedMultipleChoiceExposesTwoCheckboxesAndStaysOpenAfterToggle() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val openLabel = context.getString(R.string.debug_component_lab_liquid_action_menu_open)
        val playbackOptionsLabel =
            context.getString(R.string.debug_component_lab_liquid_action_menu_playback_options)
        val showLyricsLabel =
            context.getString(R.string.debug_component_lab_liquid_action_menu_show_lyrics)
        val normalizeVolumeLabel =
            context.getString(R.string.debug_component_lab_liquid_action_menu_normalize_volume)

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val systemDensity = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(systemDensity.density, fontScale = 1.5f),
                    LocalTransitionAnimationsEnabled provides false,
                ) {
                    val backdrop = rememberLayerBackdrop()
                    Box(
                        modifier =
                            Modifier
                                .size(width = 360.dp, height = 800.dp)
                                .background(Color(0xFFF3F4F6))
                                .layerBackdrop(backdrop),
                    ) {
                        DebugLiquidActionMenuCard(
                            accent = MiuixTheme.colorScheme.primary,
                            backdrop = backdrop,
                        )
                    }
                }
            }
        }

        composeRule.onNode(hasText(openLabel) and buttonRoleMatcher).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule
                .onAllNodes(hasText(playbackOptionsLabel) and buttonRoleMatcher)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule
            .onNode(hasText(playbackOptionsLabel) and buttonRoleMatcher)
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(checkboxRoleMatcher).fetchSemanticsNodes().size == 2
        }

        composeRule.onAllNodes(checkboxRoleMatcher).assertCountEquals(2)
        composeRule
            .onNode(hasText(showLyricsLabel) and checkboxRoleMatcher)
            .assertIsOn()
            .performClick()
            .assertIsOff()
            .assertIsDisplayed()
        composeRule
            .onNode(hasText(normalizeVolumeLabel) and checkboxRoleMatcher)
            .assertIsOff()
            .performClick()
            .assertIsOn()
            .assertIsDisplayed()

        composeRule.onAllNodes(checkboxRoleMatcher).assertCountEquals(2)
        composeRule
            .onNode(hasText(playbackOptionsLabel) and buttonRoleMatcher)
            .assertIsDisplayed()
    }

    private companion object {
        val buttonRoleMatcher = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        val checkboxRoleMatcher =
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox) and hasClickAction()
    }
}
