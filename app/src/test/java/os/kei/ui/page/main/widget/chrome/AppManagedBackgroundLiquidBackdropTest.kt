package os.kei.ui.page.main.widget.chrome

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import os.kei.core.prefs.NonHomeBackgroundContentScale
import os.kei.ui.page.main.settings.support.SettingsGroupCard
import os.kei.ui.page.main.widget.glass.LocalLiquidParentBackdrop
import os.kei.ui.page.main.widget.glass.LocalLiquidParentBackdropOverridesFallback
import os.kei.ui.page.main.widget.glass.UniformColorBackdrop
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = AppManagedBackgroundLiquidBackdropTestApp::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class AppManagedBackgroundLiquidBackdropTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun inactiveBackgroundOptInProvidesPageMaterialWithoutForcingChildOverride() {
        var pageBackdrop: Backdrop? = null
        var overridesFallback = true

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                testManagedBackgroundHost(exportBackdropToContent = true) {
                    val observedBackdrop = LocalLiquidParentBackdrop.current
                    val observedOverride = LocalLiquidParentBackdropOverridesFallback.current
                    SideEffect {
                        pageBackdrop = observedBackdrop
                        overridesFallback = observedOverride
                    }
                }
            }
        }

        composeRule.runOnIdle {
            assertNotNull(pageBackdrop)
            assertFalse(overridesFallback)
        }
    }

    @Test
    fun withNoBackgroundPaintingThePageMaterialIsTheBaseColourAsAFlatField() {
        // The recording would hold `drawRect(baseColor)` over an empty box: one colour. As a flat field,
        // every card on the route draws without an offscreen layer (docs/planning/liquid-flat-field.md).
        var pageBackdrop: Backdrop? = null
        var baseColor = Color.Unspecified

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val base = MiuixTheme.colorScheme.background
                testManagedBackgroundHost(exportBackdropToContent = true) {
                    val observed = LocalLiquidParentBackdrop.current
                    SideEffect {
                        pageBackdrop = observed
                        baseColor = base
                    }
                }
            }
        }

        composeRule.runOnIdle {
            val flat = assertIs<UniformColorBackdrop>(pageBackdrop)
            assertEquals(baseColor, flat.color)
        }
    }

    @Test
    fun withABackgroundPaintingThePageMaterialIsTheRecordedComposite() {
        var pageBackdrop: Backdrop? = null

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                testManagedBackgroundHost(
                    exportBackdropToContent = true,
                    enabled = true,
                    imageUri = "content://test/background.png",
                ) {
                    val observed = LocalLiquidParentBackdrop.current
                    SideEffect { pageBackdrop = observed }
                }
            }
        }

        composeRule.runOnIdle {
            assertIs<LayerBackdrop>(pageBackdrop, "an image varies across the page, so it has to be sampled")
        }
    }

    @Test
    fun optOutPreservesInheritedMaterialContract() {
        var inheritedBackdrop: Backdrop? = null
        var observedBackdrop: Backdrop? = null
        var observedOverride = false

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val backdrop = rememberLayerBackdrop()
                inheritedBackdrop = backdrop
                CompositionLocalProvider(
                    LocalLiquidParentBackdrop provides backdrop,
                    LocalLiquidParentBackdropOverridesFallback provides true,
                ) {
                    testManagedBackgroundHost(exportBackdropToContent = false) {
                        val contentBackdrop = LocalLiquidParentBackdrop.current
                        val contentOverride = LocalLiquidParentBackdropOverridesFallback.current
                        SideEffect {
                            observedBackdrop = contentBackdrop
                            observedOverride = contentOverride
                        }
                    }
                }
            }
        }

        composeRule.runOnIdle {
            assertNotNull(inheritedBackdrop)
            assertSame(inheritedBackdrop, observedBackdrop)
            assertTrue(observedOverride)
        }
    }

    @Test
    fun settingsCardConsumesPageMaterialAndExportsIndependentChildMaterial() {
        var pageBackdrop: Backdrop? = null
        var cardBackdrop: Backdrop? = null
        var cardOverridesFallback = false

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                testManagedBackgroundHost(exportBackdropToContent = true) {
                    val observedPageBackdrop = LocalLiquidParentBackdrop.current
                    SideEffect { pageBackdrop = observedPageBackdrop }
                    SettingsGroupCard(
                        header = "Settings",
                        title = "Controls",
                        containerColor = Color.White.copy(alpha = 0.64f),
                        exportBackdropToContent = true,
                    ) {
                        val observedCardBackdrop = LocalLiquidParentBackdrop.current
                        val observedOverride = LocalLiquidParentBackdropOverridesFallback.current
                        SideEffect {
                            cardBackdrop = observedCardBackdrop
                            cardOverridesFallback = observedOverride
                        }
                        Box(Modifier.size(24.dp))
                    }
                }
            }
        }

        composeRule.runOnIdle {
            assertNotNull(pageBackdrop)
            assertNotNull(cardBackdrop)
            assertNotSame(pageBackdrop, cardBackdrop)
            assertTrue(cardOverridesFallback)
        }
    }

    /**
     * The page behind a route must never show through it.
     *
     * This is "二级菜单的透明度在白色背景下生效". `Modifier.layerBackdrop` draws only `drawContent()` to
     * the screen and records the `rememberLayerBackdrop { ... }` block into an offscreen layer
     * *separately*, so the `drawRect(baseColor)` written inside that block reached the sampled layer and
     * never the screen. While the base was `if (sceneBackdrop != null) layerBackdrop(...) else
     * background(baseColor)`, every route that exported a backdrop had no opaque fill at all and was
     * transparent down to the main pager: the page underneath and the custom image composited together
     * into two apparent backgrounds, the near-white one being the pager's `colorScheme.surface`.
     *
     * Stated as a count because that is what the bug was — a fill that existed in only one branch.
     */
    @Test
    fun theOpaqueBaseIsPaintedWhetherOrNotABackdropIsExported() {
        var exporting = -1
        var plain = -1

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val backdrop = rememberLayerBackdrop()
                SideEffect {
                    exporting = appManagedBackgroundBaseModifier(Color.White, backdrop).elementCount()
                    plain = appManagedBackgroundBaseModifier(Color.White, null).elementCount()
                }
            }
        }

        composeRule.runOnIdle {
            assertEquals(1, plain, "the plain base should be exactly the opaque fill")
            assertEquals(2, exporting, "the exporting base should be the opaque fill plus the recorder")
        }
    }

    @Test
    fun thePageBaseTokenIsOpaqueInBothThemes() {
        // `colorScheme.background` is the token both the pager and the routes composite the custom image
        // over. A translucent value would let the page behind bleed through however the chain is built.
        val alphas = mutableListOf<Float>()

        composeRule.setContent {
            listOf(ColorSchemeMode.Light, ColorSchemeMode.Dark).forEach { mode ->
                MiuixTheme(controller = ThemeController(mode)) {
                    val base = MiuixTheme.colorScheme.background
                    SideEffect { alphas += base.alpha }
                }
            }
        }

        composeRule.runOnIdle {
            assertEquals(2, alphas.size)
            alphas.forEach { alpha -> assertEquals(1f, alpha, "a page base must be opaque") }
        }
    }

    @Test
    fun theSceneBackdropIsPublishedOnlyWhileABackgroundIsActuallyPainting() {
        // Page producers decide whether to paint their own opaque base from this local, so it has to be
        // null when nothing is behind the page — otherwise the chrome samples a transparent layer and the
        // glass loses its base entirely. `appManagedPageBackgroundActive` cannot answer this: the main
        // pager makes every non-Home page's scaffold transparent whether or not a background exists.
        var inactive: Any? = Unit
        var active: Any? = Unit

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Dark)) {
                testManagedBackgroundHost(exportBackdropToContent = false) {
                    val observed = LocalAppManagedSceneBackdrop.current
                    SideEffect { inactive = observed }
                }
                testManagedBackgroundHost(
                    exportBackdropToContent = false,
                    enabled = true,
                    imageUri = "file:///android_asset/does-not-need-to-decode.png",
                ) {
                    val observed = LocalAppManagedSceneBackdrop.current
                    SideEffect { active = observed }
                }
            }
        }

        composeRule.runOnIdle {
            assertEquals(null, inactive, "no background means no scene for page glass to sample")
            assertNotNull(active, "an active background must publish its composite")
        }
    }
}

private fun Modifier.elementCount(): Int = foldIn(0) { count, _ -> count + 1 }

/**
 * [AppManagedBackgroundHost] with the settings the tests do not vary: full opacity, crop, no scrim.
 * A disabled host with no image is the "nothing behind the page" case.
 */
@Composable
private fun testManagedBackgroundHost(
    exportBackdropToContent: Boolean,
    enabled: Boolean = false,
    imageUri: String = "",
    content: @Composable () -> Unit,
) {
    AppManagedBackgroundHost(
        enabled = enabled,
        imageUri = imageUri,
        opacity = 1f,
        contentScale = NonHomeBackgroundContentScale.Crop,
        scrim = 0f,
        exportBackdropToContent = exportBackdropToContent,
        content = content,
    )
}

class AppManagedBackgroundLiquidBackdropTestApp : Application()
