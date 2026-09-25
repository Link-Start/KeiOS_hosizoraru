package os.kei.ui.page.main.widget.core

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import os.kei.ui.page.main.widget.glass.AppLiquidExpandableSection
import os.kei.ui.page.main.widget.glass.AppStandaloneBackdropHost
import os.kei.ui.page.main.widget.glass.BackdropScene
import os.kei.ui.page.main.widget.glass.LiquidBackdropWindowBoundary
import os.kei.ui.page.main.widget.glass.LocalLiquidControlsEnabled
import os.kei.ui.page.main.widget.glass.LocalLiquidParentBackdrop
import os.kei.ui.page.main.widget.glass.preferredLiquidBackdrop
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = Application::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class AppSurfaceCardBackdropTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun surfaceCardExportsIndependentBackdropToContent() {
        var sceneBackdrop: Backdrop? = null
        var contentBackdrop: Backdrop? = null
        var resolvedExplicitFallback: Backdrop? = null
        var observedContentColor = Color.Unspecified
        val expectedContentColor = Color(0xFF2468AC)

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val backdrop = rememberLayerBackdrop()
                val explicitFallback = rememberLayerBackdrop()
                sceneBackdrop = backdrop
                BackdropScene(backdrop, 220.dp) {
                    CompositionLocalProvider(LocalLiquidParentBackdrop provides backdrop) {
                        AppSurfaceCard(
                            exportBackdropToContent = true,
                            contentColor = expectedContentColor,
                        ) {
                            contentBackdrop = LocalLiquidParentBackdrop.current
                            resolvedExplicitFallback = preferredLiquidBackdrop(explicitFallback)
                            observedContentColor = LocalContentColor.current
                            Box(
                                modifier =
                                    Modifier
                                        .size(24.dp)
                                        .testTag("surface-card-content"),
                            )
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithTag("surface-card-content").assertExists()
        composeRule.runOnIdle {
            assertNotNull(sceneBackdrop)
            assertNotNull(contentBackdrop)
            assertNotSame(sceneBackdrop, contentBackdrop)
            assertSame(contentBackdrop, resolvedExplicitFallback)
            assertEquals(expectedContentColor, observedContentColor)
        }
    }

    @Test
    fun surfaceCardWithoutParentKeepsContentOnSolidFallback() {
        var contentBackdrop: Backdrop? = null

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                AppSurfaceCard(exportBackdropToContent = true) {
                    contentBackdrop = LocalLiquidParentBackdrop.current
                    Box(
                        modifier =
                            Modifier
                                .size(24.dp)
                                .testTag("fallback-export-content"),
                    )
                }
            }
        }

        composeRule.onNodeWithTag("fallback-export-content").assertExists()
        composeRule.runOnIdle { assertNull(contentBackdrop) }
    }

    @Test
    fun expandableSectionExportsItsSurfaceToExpandedContent() {
        var sceneBackdrop: Backdrop? = null
        var contentBackdrop: Backdrop? = null

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val backdrop = rememberLayerBackdrop()
                sceneBackdrop = backdrop
                BackdropScene(backdrop, 260.dp) {
                    AppLiquidExpandableSection(
                        backdrop = backdrop,
                        title = "Expandable",
                        subtitle = "Backdrop",
                        expanded = true,
                        onExpandedChange = {},
                    ) {
                        contentBackdrop = LocalLiquidParentBackdrop.current
                        Box(
                            modifier =
                                Modifier
                                    .size(24.dp)
                                    .testTag("expandable-section-content"),
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("expandable-section-content").assertExists()
        composeRule.runOnIdle {
            assertNotNull(sceneBackdrop)
            assertNotNull(contentBackdrop)
            assertNotSame(sceneBackdrop, contentBackdrop)
        }
    }

    @Test
    fun featureCardForwardsExportedBackdropToBody() {
        var sceneBackdrop: Backdrop? = null
        var contentBackdrop: Backdrop? = null

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val backdrop = rememberLayerBackdrop()
                sceneBackdrop = backdrop
                BackdropScene(backdrop, 260.dp) {
                    AppFeatureCard(
                        title = "Backdrop",
                        subtitle = "Export",
                        backdrop = backdrop,
                        exportBackdropToContent = true,
                    ) {
                        contentBackdrop = LocalLiquidParentBackdrop.current
                        Box(
                            modifier =
                                Modifier
                                    .size(24.dp)
                                    .testTag("feature-card-content"),
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("feature-card-content").assertExists()
        composeRule.runOnIdle {
            assertNotNull(sceneBackdrop)
            assertNotNull(contentBackdrop)
            assertNotSame(sceneBackdrop, contentBackdrop)
        }
    }

    @Test
    fun exportDisabledKeepsInheritedBackdropVisibleToContent() {
        var expectedBackdrop: Backdrop? = null
        var observedBackdrop: Backdrop? = null

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val backdrop = rememberLayerBackdrop()
                expectedBackdrop = backdrop
                BackdropScene(backdrop, 220.dp) {
                    CompositionLocalProvider(LocalLiquidParentBackdrop provides backdrop) {
                        AppSurfaceCard(exportBackdropToContent = false) {
                            observedBackdrop = LocalLiquidParentBackdrop.current
                            Box(
                                modifier =
                                    Modifier
                                        .size(24.dp)
                                        .testTag("inherited-card-content"),
                            )
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithTag("inherited-card-content").assertExists()
        composeRule.runOnIdle {
            assertNotNull(expectedBackdrop)
            assertSame(expectedBackdrop, observedBackdrop)
        }
    }

    @Test
    fun disabledLiquidEffectsKeepDescendantControlsOnFallback() {
        var parentBackdrop: Backdrop? = null
        var observedContentBackdrop: Backdrop? = null
        var observedActiveBackdrop: Backdrop? = null

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val backdrop = rememberLayerBackdrop()
                parentBackdrop = backdrop
                BackdropScene(backdrop, 220.dp) {
                    CompositionLocalProvider(
                        LocalLiquidParentBackdrop provides backdrop,
                        LocalLiquidControlsEnabled provides false,
                    ) {
                        AppSurfaceCard(exportBackdropToContent = true) {
                            observedContentBackdrop = LocalLiquidParentBackdrop.current
                            AppStandaloneBackdropHost(modifier = Modifier) { activeBackdrop ->
                                observedActiveBackdrop = activeBackdrop
                                Box(modifier = Modifier.testTag("disabled-effects-content"))
                            }
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithTag("disabled-effects-content").assertExists()
        composeRule.runOnIdle {
            assertNotNull(parentBackdrop)
            assertSame(parentBackdrop, observedContentBackdrop)
            assertNull(observedActiveBackdrop)
        }
    }

    @Test
    fun windowBoundaryRejectsCapturedBackdropWithoutPublishingEmptyExport() {
        var capturedPageBackdrop: Backdrop? = null
        var observedContentBackdrop: Backdrop? = null
        var observedActiveBackdrop: Backdrop? = null

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val backdrop = rememberLayerBackdrop()
                capturedPageBackdrop = backdrop
                BackdropScene(backdrop, 220.dp) {
                    LiquidBackdropWindowBoundary {
                        AppSurfaceCard(
                            backdrop = backdrop,
                            exportBackdropToContent = true,
                        ) {
                            observedContentBackdrop = LocalLiquidParentBackdrop.current
                            AppStandaloneBackdropHost(modifier = Modifier) { activeBackdrop ->
                                observedActiveBackdrop = activeBackdrop
                                Box(modifier = Modifier.testTag("window-boundary-card-content"))
                            }
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithTag("window-boundary-card-content").assertExists()
        composeRule.runOnIdle {
            assertNotNull(capturedPageBackdrop)
            assertNull(observedContentBackdrop)
            assertNull(observedActiveBackdrop)
        }
    }

    @Test
    fun nestedCardsExportIndependentBackdrops() {
        var sceneBackdrop: Backdrop? = null
        var outerBackdrop: Backdrop? = null
        var innerBackdrop: Backdrop? = null

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val backdrop = rememberLayerBackdrop()
                sceneBackdrop = backdrop
                BackdropScene(backdrop, 260.dp) {
                    AppSurfaceCard(
                        backdrop = backdrop,
                        exportBackdropToContent = true,
                    ) {
                        outerBackdrop = LocalLiquidParentBackdrop.current
                        AppSurfaceCard(exportBackdropToContent = true) {
                            innerBackdrop = LocalLiquidParentBackdrop.current
                            Box(
                                modifier =
                                    Modifier
                                        .size(24.dp)
                                        .testTag("nested-card-content"),
                            )
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithTag("nested-card-content").assertExists()
        composeRule.runOnIdle {
            assertNotNull(sceneBackdrop)
            assertNotNull(outerBackdrop)
            assertNotNull(innerBackdrop)
            assertNotSame(sceneBackdrop, outerBackdrop)
            assertNotSame(outerBackdrop, innerBackdrop)
        }
    }
}
