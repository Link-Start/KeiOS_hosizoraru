package os.kei.ui.page.main.ba

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import os.kei.ui.page.main.ba.support.BaCraftFunction
import os.kei.ui.page.main.host.pager.MainPageBackdropSet
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
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
    application = BaPageBackdropTestApp::class,
    sdk = [35],
)
class BaPageBackdropTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun baPageUsesStableCanvasContentAndIndependentVisibleSheetAfterEntry() {
        lateinit var recompositionSignal: MutableIntState
        var observedBackdrops: MainPageBackdropSet? = null

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val signal = remember { mutableIntStateOf(0) }
                recompositionSignal = signal
                val backdrops =
                    rememberBaPageBackdropSet(
                        pageBackdropEffectsEnabled = true,
                        sheetBackdropVisible = true,
                    )
                val revision = signal.intValue

                SideEffect {
                    observedBackdrops = backdrops
                    check(revision >= 0)
                }
                Box(modifier = Modifier.size(1.dp))
            }
        }

        composeRule.waitForIdle()
        lateinit var settledBackdrops: MainPageBackdropSet
        composeRule.runOnIdle {
            settledBackdrops = requireNotNull(observedBackdrops)
            assertSame(settledBackdrops.topBarProducer, settledBackdrops.contentProducer)
            assertNotSame(settledBackdrops.contentProducer, settledBackdrops.contentMaterial)
            assertNotSame(settledBackdrops.topBarProducer, settledBackdrops.sheetProducer)
            recompositionSignal.intValue += 1
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            val recomposedBackdrops = requireNotNull(observedBackdrops)
            assertSame(settledBackdrops.topBarProducer, recomposedBackdrops.topBarProducer)
            assertSame(settledBackdrops.contentProducer, recomposedBackdrops.contentProducer)
            assertSame(settledBackdrops.contentMaterial, recomposedBackdrops.contentMaterial)
            assertSame(settledBackdrops.sheetProducer, recomposedBackdrops.sheetProducer)
        }
    }

    @Test
    fun baPageReusesTopBarBackdropWhileEverySheetIsHidden() {
        var observedBackdrops: MainPageBackdropSet? = null

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val backdrops =
                    rememberBaPageBackdropSet(
                        pageBackdropEffectsEnabled = true,
                        sheetBackdropVisible = false,
                    )
                SideEffect { observedBackdrops = backdrops }
                Box(modifier = Modifier.size(1.dp))
            }
        }

        composeRule.waitForIdle()
        composeRule.runOnIdle {
            val backdrops = requireNotNull(observedBackdrops)
            assertSame(backdrops.topBarProducer, backdrops.contentProducer)
            assertSame(backdrops.contentProducer, backdrops.sheetProducer)
            assertNotSame(backdrops.contentProducer, backdrops.contentMaterial)
        }
    }

    @Test
    fun sheetBackdropVisibilityIncludesEveryBaPageSheet() {
        assertFalse(BaOfficeChromeUiState().hasVisiblePageSheet)
        listOf(
            BaOfficeChromeUiState(showSettingsSheet = true),
            BaOfficeChromeUiState(showAccountManagementSheet = true),
            BaOfficeChromeUiState(showNotificationSettingsSheet = true),
            BaOfficeChromeUiState(showApLimitToolsSheet = true),
            BaOfficeChromeUiState(showCafeApToolsSheet = true),
            BaOfficeChromeUiState(dailyDoneSheet = BaDailyDoneSheetUiState(show = true)),
            BaOfficeChromeUiState(cafeCooldownEditTarget = BaCafeCooldownEditTarget.Headpat),
            BaOfficeChromeUiState(
                craftSlotEditTarget = BaCraftSlotEditTarget(function = BaCraftFunction.Generate, index = 0),
            ),
        ).forEach { state ->
            assertTrue(state.hasVisiblePageSheet)
        }
    }

    @Test
    fun canvasContentPrecedesBaConsumersAndTopBarKeepsItsOwnProducer() {
        val pageSource = sourceFile(BA_PAGE_SOURCE)
        val contentSource = sourceFile(BA_PAGE_CONTENT_SOURCE)
        val sceneIndex = pageSource.indexOf("MainPageContentBackdropScene(")
        val sceneBackdropIndex =
            pageSource.indexOf("contentProducer = null", startIndex = sceneIndex.coerceAtLeast(0))
        val scaffoldIndex = pageSource.indexOf("AppScaffold(", startIndex = sceneBackdropIndex.coerceAtLeast(0))
        val contentConsumerIndex =
            pageSource.indexOf("backdrop = backdrops.contentMaterial", startIndex = scaffoldIndex.coerceAtLeast(0))
        val dockConsumerIndex =
            pageSource.indexOf("BaPageFloatingDock(", startIndex = contentConsumerIndex.coerceAtLeast(0))
        val topBarProducerIndex = contentSource.indexOf(".layerBackdrop(topBarBackdrop)")

        assertTrue(sceneIndex >= 0, "BA page must host one content Backdrop scene")
        assertTrue(sceneBackdropIndex > sceneIndex, "BA scene must be handed a producer, and it has none of its own")
        assertTrue(scaffoldIndex > sceneBackdropIndex, "The producer wiring must precede the Scaffold consumer tree")
        assertTrue(contentConsumerIndex > scaffoldIndex, "BA cards must consume the direct content material")
        assertTrue(dockConsumerIndex > contentConsumerIndex, "Floating dock must be composed after page consumers")
        assertTrue(
            pageSource.indexOf("backdrop = backdrops.topBar,", startIndex = dockConsumerIndex) > dockConsumerIndex,
            "Floating dock must sample the scrolling-content identity",
        )
        assertTrue(topBarProducerIndex >= 0, "BA scrolling content must keep the dedicated top-bar producer")
        assertEquals(1, pageSource.occurrencesOf("MainPageContentBackdropScene("))
        assertEquals(1, contentSource.occurrencesOf(".layerBackdrop(topBarBackdrop)"))
        assertEquals(1, pageSource.occurrencesOf("contentProducer = null"))
        assertEquals(1, pageSource.occurrencesOf("backdrop = backdrops.contentMaterial"))
        assertEquals(
            1,
            pageSource.occurrencesOf("producerActive = pageBackdropEffectsEnabled && sheetBackdropVisible"),
        )
        assertEquals(
            1,
            pageSource.occurrencesOf("distinctLayers = pageBackdropEffectsEnabled && sheetBackdropVisible"),
        )
        assertEquals(0, pageSource.occurrencesOf("producerActive = backdrops.sheetProducer !== backdrops.contentProducer"))
        assertEquals(1, pageSource.occurrencesOf("useSolidSurfaceBackdrops = true"))
        assertEquals(0, pageSource.occurrencesOf(".layerBackdrop(backdrops.contentProducer)"))
        assertEquals(0, contentSource.occurrencesOf(".layerBackdrop(backdrop)"))
    }
}

private fun sourceFile(relativePath: String): String {
    val workingDirectory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
    val sourceFile =
        generateSequence(workingDirectory) { directory -> directory.parentFile }
            .map { directory -> File(directory, relativePath) }
            .firstOrNull(File::isFile)
    return requireNotNull(sourceFile) {
        "Unable to locate $relativePath from $workingDirectory"
    }.readText()
}

private fun String.occurrencesOf(needle: String): Int = windowed(needle.length).count { it == needle }

private const val BA_PAGE_SOURCE =
    "app/src/main/java/os/kei/ui/page/main/ba/BAPage.kt"
private const val BA_PAGE_CONTENT_SOURCE =
    "app/src/main/java/os/kei/ui/page/main/ba/BaPageContent.kt"

class BaPageBackdropTestApp : Application()
