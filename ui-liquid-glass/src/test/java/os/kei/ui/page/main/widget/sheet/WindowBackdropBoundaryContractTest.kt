package os.kei.ui.page.main.widget.sheet

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import os.kei.ui.page.main.widget.glass.AppLiquidWindowBoundary
import os.kei.ui.page.main.widget.glass.LocalLiquidDialogBackdrop
import os.kei.ui.page.main.widget.glass.LocalLiquidParentBackdrop
import os.kei.ui.page.main.widget.glass.preferredLiquidBackdrop
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WindowBackdropBoundaryContractTest {
    @Test
    fun boundaryClearsInheritedWindowBackdropsAndExplicitFallbacks() {
        val boundary = windowBoundarySource(WINDOW_BOUNDARY_SOURCE)
        val resolver = windowBoundarySource(GLASS_RUNTIME_SOURCE)

        assertTrue("LocalLiquidBackdropWindowBoundary provides true" in boundary)
        assertTrue("LocalSceneBackdrop provides emptyBackdrop()" in boundary)
        assertTrue("LocalLiquidParentBackdrop provides null" in boundary)
        assertTrue("LocalLiquidParentBackdropOverridesFallback provides false" in boundary)
        assertTrue("LocalLiquidDialogBackdrop provides null" in boundary)
        assertTrue("fun AppLiquidWindowBoundary(content: @Composable () -> Unit)" in boundary)
        assertTrue("LiquidBackdropWindowBoundary(content = content)" in boundary)
        assertTrue(
            "LocalLiquidParentBackdrop.current ?: LocalLiquidDialogBackdrop.current" in resolver,
            "A window can consume a parent or dialog backdrop created inside its own boundary",
        )
        assertFalse("rememberLayerBackdrop" in boundary)
        assertFalse(".layerBackdrop(" in boundary)
        assertFalse(".drawBackdrop(" in boundary)
    }

    /**
     * The sheet no longer hides behind the boundary — it renders in the activity window, so it both
     * may and must sample the real scene backdrop. What it still has to do is degrade to an opaque
     * fill whenever that backdrop cannot be trusted: outside an overlay host, `LocalSceneBackdrop` is
     * whatever the surrounding window provides, and in a preview or Robolectric harness that is
     * `emptyBackdrop()`. Asking it for blur there produces a *transparent* sheet, not a blurred one.
     */
    @Test
    fun sheetSamplesTheSceneBackdropButFallsBackWithoutAnOverlayHost() {
        val surface = windowBoundarySource(LIQUID_SHEET_SURFACE_SOURCE)

        assertTrue("LocalSceneBackdrop.current" in surface)
        assertTrue(".drawBackdrop(" in surface)
        assertTrue("exportedBackdrop = sheetBackdrop," in surface)
        assertTrue(
            "LocalLiquidOverlayHost.current != null" in surface,
            "Glass must be gated on actually being inside the overlay host",
        )
        assertTrue(
            ".background(" in surface,
            "The fallback must be an opaque fill, not a no-op backdrop",
        )
        assertFalse(
            ".layerBackdrop(" in surface,
            "A second layerBackdrop after drawBackdrop is the documented draw loop",
        )
    }

    /**
     * The whole presentation family — dialog, alert, action sheet — renders in the activity window
     * through the overlay portal, and every one of them republishes its own surface so controls inside
     * cannot sample the page they float over.
     *
     * Hosting any of these in a Dialog window is the regression to guard against: `LocalSceneBackdrop`
     * is blanked there, so the card's blur draws nothing and it silently degrades to a flat fill. That
     * is exactly how the old dialog ended up readable straight through.
     */
    @Test
    fun modalPresentationsRenderInWindowAndRepublishTheirOwnSurface() {
        val presentation = windowBoundarySource(LIQUID_MODAL_PRESENTATION_SOURCE)
        val modalSurface = windowBoundarySource(LIQUID_MODAL_SURFACE_SOURCE)

        assertTrue("LiquidOverlayPortal {" in presentation)
        assertFalse("Dialog(" in presentation)

        assertTrue("LocalSceneBackdrop.current" in modalSurface)
        assertTrue("exportedBackdrop = cardBackdrop," in modalSurface)
        assertTrue(
            "LocalLiquidOverlayHost.current != null" in modalSurface,
            "Glass must be gated on actually being inside the overlay host",
        )
        assertFalse(
            ".layerBackdrop(" in modalSurface,
            "A second layerBackdrop after drawBackdrop is the documented draw loop",
        )

        // Not going back to a Dialog window is the repo-wide ban in app's PlatformWindowSourceContractTest.
        for (relativePath in LIQUID_MODAL_CONSUMER_SOURCES) {
            val consumer = windowBoundarySource(relativePath)
            assertTrue(
                "LocalLiquidParentBackdrop provides surface.exportedBackdrop," in consumer,
                "$relativePath must republish its own surface to its content",
            )
        }
    }

    /**
     * Inverted from the contract it replaces, which checked that the anchored panel installed a window
     * boundary before composing caller content.
     *
     * That was the right invariant for a panel in a `Popup`: content there must not inherit a producer
     * from the activity window, because a `LayerBackdrop` resolves its offset through shared
     * `LayoutCoordinates` and two windows have none. But obeying it meant the panel had no backdrop at
     * all — `activeGlassBackdrop` resolved to null and every menu in the app drew a flat filled card
     * while carrying twenty configured optical values that never reached a shader.
     *
     * So the panel moved into the activity window, and the invariant flips: it must *not* install a
     * boundary, and it must go through the overlay portal.
     */
    @Test
    fun theAnchoredPanelIsHostedInWindowRatherThanBehindAWindowBoundary() {
        val panel = windowBoundarySource(SNAPSHOT_POPUP_SOURCE)

        // Not hosting it in a Popup window, which is what blanked its backdrop, is the repo-wide ban in
        // app's PlatformWindowSourceContractTest.
        assertTrue(
            "LiquidMenuPresentation(" in panel,
            "the panel is hosted by LiquidMenuPresentation, which portals it into the activity window",
        )

        val presentation = windowBoundarySource(LIQUID_MENU_PRESENTATION_SOURCE)
        assertTrue(
            "LiquidOverlayPortal {" in presentation,
            "in-window hosting is what makes the scene backdrop reachable",
        )
        // Matched on the call, not the bare name: the presentation's own KDoc names the boundary while
        // explaining why the panel no longer sits behind one.
        assertFalse(
            "LiquidBackdropWindowBoundary {" in presentation ||
                "LiquidBackdropWindowBoundary(" in presentation,
            "installing a boundary here would blank the very backdrop the panel now samples",
        )
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = WindowBackdropBoundaryContractTestApp::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class WindowBackdropBoundaryRuntimeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun resolverRejectsCapturedBackdropAndAcceptsWindowLocalProviders() {
        var explicitOnly: Backdrop? = null
        var expectedParent: Backdrop? = null
        var expectedDialog: Backdrop? = null
        var resolvedParent: Backdrop? = null
        var resolvedDialog: Backdrop? = null

        composeRule.setContent {
            val capturedPageBackdrop = rememberLayerBackdrop()
            val windowParentBackdrop = rememberLayerBackdrop()
            val windowDialogBackdrop = rememberLayerBackdrop()

            AppLiquidWindowBoundary {
                val explicitResolution = preferredLiquidBackdrop(capturedPageBackdrop)
                SideEffect {
                    explicitOnly = explicitResolution
                    expectedParent = windowParentBackdrop
                    expectedDialog = windowDialogBackdrop
                }
                CompositionLocalProvider(LocalLiquidParentBackdrop provides windowParentBackdrop) {
                    val resolution = preferredLiquidBackdrop(capturedPageBackdrop)
                    SideEffect { resolvedParent = resolution }
                }
                CompositionLocalProvider(LocalLiquidDialogBackdrop provides windowDialogBackdrop) {
                    val resolution = preferredLiquidBackdrop(capturedPageBackdrop)
                    SideEffect { resolvedDialog = resolution }
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertNull(explicitOnly)
            assertSame(expectedParent, resolvedParent)
            assertSame(expectedDialog, resolvedDialog)
        }
    }
}

class WindowBackdropBoundaryContractTestApp : Application()

private fun windowBoundarySource(relativePath: String): String {
    val roots = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
    val source = roots.map { File(it, relativePath) }.firstOrNull(File::isFile)
    return requireNotNull(source) {
        "Unable to locate $relativePath from ${System.getProperty("user.dir")}"
    }.readText()
}

private const val WINDOW_BOUNDARY_SOURCE =
    "ui-liquid-glass/src/main/java/os/kei/ui/page/main/widget/glass/LiquidBackdropWindowBoundary.kt"
private const val GLASS_RUNTIME_SOURCE =
    "ui-liquid-glass/src/main/java/os/kei/ui/page/main/widget/glass/GlassEffectRuntime.kt"
private const val LIQUID_SHEET_SURFACE_SOURCE =
    "ui-liquid-glass/src/main/java/os/kei/ui/page/main/widget/sheet/LiquidSheetSurface.kt"
private const val LIQUID_MODAL_PRESENTATION_SOURCE =
    "ui-liquid-glass/src/main/java/os/kei/ui/page/main/widget/dialog/LiquidModalPresentation.kt"
private const val LIQUID_MODAL_SURFACE_SOURCE =
    "ui-liquid-glass/src/main/java/os/kei/ui/page/main/widget/dialog/LiquidModalSurface.kt"
private val LIQUID_MODAL_CONSUMER_SOURCES =
    listOf(
        // LiquidGlassDialog is a deprecated alias that delegates to LiquidAlert, so it has no
        // material of its own to check.
        "ui-liquid-glass/src/main/java/os/kei/ui/page/main/widget/dialog/LiquidAlert.kt",
        "ui-liquid-glass/src/main/java/os/kei/ui/page/main/widget/dialog/LiquidActionSheet.kt",
    )
private const val SNAPSHOT_POPUP_SOURCE =
    "ui-liquid-glass/src/main/java/os/kei/ui/page/main/widget/sheet/MiuixSnapshotAdapters.kt"
private const val LIQUID_MENU_PRESENTATION_SOURCE =
    "ui-liquid-glass/src/main/java/os/kei/ui/page/main/widget/glass/LiquidMenuPresentation.kt"
