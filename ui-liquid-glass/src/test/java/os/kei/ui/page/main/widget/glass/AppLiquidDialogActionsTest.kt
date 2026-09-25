package os.kei.ui.page.main.widget.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = AppLiquidDialogActionsTestApp::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class AppLiquidDialogActionsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun standaloneBackdropHostUsesFallbackSurfaceWithoutParentBackdrop() {
        var observedBackdrop: Backdrop? = null

        composeRule.setContent {
            AppStandaloneBackdropHost(modifier = Modifier) { backdrop ->
                observedBackdrop = backdrop
                Box(modifier = Modifier.testTag("standalone-content"))
            }
        }

        composeRule.onNodeWithTag("standalone-content").assertExists()
        composeRule.runOnIdle { assertNull(observedBackdrop) }
    }

    @Test
    fun standaloneBackdropGroupProvidesExplicitBackdropToChildren() {
        var expectedBackdrop: Backdrop? = null
        var observedBackdrop: Backdrop? = null

        composeRule.setContent {
            val backdrop = rememberLayerBackdrop()
            expectedBackdrop = backdrop
            BackdropScene(backdrop, 48.dp) {
                AppStandaloneLiquidBackdropGroup(backdrop = backdrop) {
                    observedBackdrop = LocalLiquidParentBackdrop.current
                    Box(modifier = Modifier.testTag("grouped-standalone-content"))
                }
            }
        }

        composeRule.onNodeWithTag("grouped-standalone-content").assertExists()
        composeRule.runOnIdle {
            assertNotNull(expectedBackdrop)
            assertSame(expectedBackdrop, observedBackdrop)
        }
    }

    @Test
    fun fallbackAndBackdropActionsApplyTagToClickableButton() {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val backdrop = rememberLayerBackdrop()
                Column(
                    modifier =
                        Modifier
                            .background(Color.White)
                            .layerBackdrop(backdrop),
                ) {
                    AppLiquidDialogActionButton(
                        text = "Fallback action",
                        onClick = {},
                        buttonModifier = Modifier.testTag("fallback-action"),
                    )
                    CompositionLocalProvider(LocalLiquidDialogBackdrop provides backdrop) {
                        AppLiquidDialogActionButton(
                            text = "Backdrop action",
                            onClick = {},
                            buttonModifier = Modifier.testTag("backdrop-action"),
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("fallback-action").assertHasClickAction()
        composeRule.onNodeWithTag("backdrop-action").assertHasClickAction()
    }

    @Test
    fun fallbackActionsHonorRowWeightsAndKeepBothTargetsReachable() {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                Row(modifier = Modifier.width(320.dp)) {
                    AppLiquidDialogActionButton(
                        text = "Cancel",
                        onClick = {},
                        modifier = Modifier.weight(1f),
                        buttonModifier = Modifier.fillMaxWidth().testTag("fallback-cancel"),
                    )
                    AppLiquidDialogActionButton(
                        text = "Confirm",
                        onClick = {},
                        modifier = Modifier.weight(1f),
                        buttonModifier = Modifier.fillMaxWidth().testTag("fallback-confirm"),
                    )
                }
            }
        }

        composeRule
            .onNodeWithTag("fallback-cancel")
            .assertHasClickAction()
            .assertWidthIsAtLeast(140.dp)
        composeRule
            .onNodeWithTag("fallback-confirm")
            .assertHasClickAction()
            .assertWidthIsAtLeast(140.dp)
    }
}

class AppLiquidDialogActionsTestApp : Application()
