package os.kei.ui.page.main.settings.support

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
import os.kei.ui.page.main.widget.glass.LocalLiquidParentBackdrop
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = Application::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class SettingsGroupCardBackdropTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun exportsIndependentBackdropToActionContent() {
        var sceneBackdrop: Backdrop? = null
        var contentBackdrop: Backdrop? = null

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val backdrop = rememberLayerBackdrop()
                sceneBackdrop = backdrop
                Box(modifier = Modifier.size(260.dp)) {
                    Box(
                        modifier =
                            Modifier
                                .matchParentSize()
                                .background(Color.White)
                                .layerBackdrop(backdrop),
                    )
                    CompositionLocalProvider(LocalLiquidParentBackdrop provides backdrop) {
                        SettingsGroupCard(
                            header = "Settings",
                            title = "Actions",
                            containerColor = Color.White,
                            exportBackdropToContent = true,
                        ) {
                            contentBackdrop = LocalLiquidParentBackdrop.current
                            Box(
                                modifier =
                                    Modifier
                                        .size(24.dp)
                                        .testTag("settings-group-card-content"),
                            )
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithTag("settings-group-card-content").assertExists()
        composeRule.runOnIdle {
            assertNotNull(sceneBackdrop)
            assertNotNull(contentBackdrop)
            assertNotSame(sceneBackdrop, contentBackdrop)
        }
    }

    @Test
    fun keepsInheritedBackdropForDefaultContent() {
        var sceneBackdrop: Backdrop? = null
        var contentBackdrop: Backdrop? = null

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val backdrop = rememberLayerBackdrop()
                sceneBackdrop = backdrop
                CompositionLocalProvider(LocalLiquidParentBackdrop provides backdrop) {
                    SettingsGroupCard(
                        header = "Settings",
                        title = "Information",
                        containerColor = Color.White,
                    ) {
                        contentBackdrop = LocalLiquidParentBackdrop.current
                        Box(
                            modifier =
                                Modifier
                                    .size(24.dp)
                                    .testTag("settings-group-default-content"),
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("settings-group-default-content").assertExists()
        composeRule.runOnIdle {
            assertNotNull(sceneBackdrop)
            assertSame(sceneBackdrop, contentBackdrop)
        }
    }

    @Test
    fun defaultGroupCardShowsContentWithoutAnInertCollapseAction() {
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                SettingsGroupCard(
                    header = "Settings",
                    title = "Static information",
                    containerColor = Color.White,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(24.dp)
                                .testTag("settings-group-static-content"),
                    )
                }
            }
        }

        composeRule.onNodeWithTag("settings-group-static-content").assertExists()
        composeRule
            .onAllNodes(hasClickAction(), useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun expansionHandlerEnablesTheCollapseAction() {
        var requestedExpanded: Boolean? = null

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                SettingsGroupCard(
                    header = "Settings",
                    title = "Controlled information",
                    containerColor = Color.White,
                    expanded = true,
                    onExpandedChange = { requestedExpanded = it },
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(24.dp)
                                .testTag("settings-group-controlled-content"),
                    )
                }
            }
        }

        composeRule.onNodeWithTag("settings-group-controlled-content").assertExists()
        composeRule
            .onAllNodes(hasClickAction(), useUnmergedTree = true)
            .assertCountEquals(1)
        val collapseAction =
            composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true)[0]
        collapseAction.performClick()
        composeRule.runOnIdle {
            assertEquals(false, requestedExpanded)
        }
    }
}
