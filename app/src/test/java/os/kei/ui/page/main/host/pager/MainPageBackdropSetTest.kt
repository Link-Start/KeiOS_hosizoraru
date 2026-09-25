package os.kei.ui.page.main.host.pager

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import os.kei.ui.testing.repoSource
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = MainPageBackdropSetTestApp::class,
    sdk = [35],
)
class MainPageBackdropSetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun contentSceneKeepsLayerProducersBeforeConsumerSlot() {
        val source = repoSource(MAIN_PAGE_BACKDROP_SET_SOURCE)
        val contentProducerIndex = source.indexOf(".layerBackdrop(contentProducer)")
        val sheetProducerIndex = source.indexOf(".layerBackdrop(sheetProducer)")
        val consumerIndex = source.indexOf("content()", startIndex = sheetProducerIndex + 1)

        assertTrue(contentProducerIndex >= 0, "Expected a content Backdrop producer")
        assertTrue(sheetProducerIndex > contentProducerIndex, "Sheet producer must follow the content producer")
        assertTrue(consumerIndex > sheetProducerIndex, "Content consumers must follow both producer siblings")
        assertTrue(".matchParentSize()" in source, "The producer must cover the complete page scene")
        assertTrue(
            "as? LayerBackdrop" !in source,
            "Recording must be decided by the parameter's type, not by casting a consumer value: a " +
                "consumer that happened to be a layer used to be silently re-recorded and blanked",
        )
        assertTrue(
            "Box(modifier = modifier)" in source,
            "The scene modifier must remain on the neutral parent rather than the Backdrop producer",
        )
    }

    @Test
    fun solidContentMaterialSharesOnlyTheTopBarLayerIdentity() {
        var backdrops: MainPageBackdropSet? = null

        composeRule.setContent {
            TestTheme {
                backdrops =
                    rememberMainPageBackdropSet(
                        keyPrefix = "solid",
                        distinctLayers = true,
                        useSolidSurfaceBackdrops = true,
                    )
            }
        }

        composeRule.runOnIdle {
            val result = requireNotNull(backdrops)
            assertSame(result.topBarProducer, result.contentProducer)
            assertNotSame(result.topBarProducer, result.sheetProducer)
            assertNotSame(result.topBarProducer, result.contentMaterial)
        }
    }

    @Test
    fun distinctLayersUseIndependentBackdropIdentities() {
        var backdrops: MainPageBackdropSet? = null

        composeRule.setContent {
            TestTheme {
                backdrops =
                    rememberMainPageBackdropSet(
                        keyPrefix = "distinct",
                        distinctLayers = true,
                    )
            }
        }

        composeRule.runOnIdle {
            val result = requireNotNull(backdrops)
            assertNotSame(result.topBarProducer, result.contentProducer)
            assertNotSame(result.topBarProducer, result.sheetProducer)
            assertNotSame(result.contentProducer, result.sheetProducer)
        }
    }

    @Test
    fun collapsedLayersKeepTopBarIndependentByDefault() {
        var backdrops: MainPageBackdropSet? = null

        composeRule.setContent {
            TestTheme {
                backdrops =
                    rememberMainPageBackdropSet(
                        keyPrefix = "shared",
                        distinctLayers = false,
                    )
            }
        }

        composeRule.runOnIdle {
            val result = requireNotNull(backdrops)
            assertNotSame(result.topBarProducer, result.contentProducer)
            assertSame(result.contentProducer, result.sheetProducer)
        }
    }

    @Test
    fun topBarAndContentIdentitiesStayStableWhenSheetCollapses() {
        val distinctLayers = mutableStateOf(true)
        var backdrops: MainPageBackdropSet? = null

        composeRule.setContent {
            TestTheme {
                backdrops =
                    rememberMainPageBackdropSet(
                        keyPrefix = "stable",
                        distinctLayers = distinctLayers.value,
                    )
            }
        }

        lateinit var expanded: MainPageBackdropSet
        composeRule.runOnIdle {
            expanded = requireNotNull(backdrops)
            distinctLayers.value = false
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            val collapsed = requireNotNull(backdrops)
            assertSame(expanded.topBarProducer, collapsed.topBarProducer)
            assertSame(expanded.contentProducer, collapsed.contentProducer)
            assertSame(collapsed.contentProducer, collapsed.sheetProducer)
            distinctLayers.value = true
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            val expandedAgain = requireNotNull(backdrops)
            assertSame(expanded.topBarProducer, expandedAgain.topBarProducer)
            assertSame(expanded.contentProducer, expandedAgain.contentProducer)
            assertNotSame(expandedAgain.contentProducer, expandedAgain.sheetProducer)
        }
    }

    @Composable
    private fun TestTheme(content: @Composable () -> Unit) {
        MiuixTheme(controller = ThemeController(ColorSchemeMode.Light), content = content)
    }
}

private const val MAIN_PAGE_BACKDROP_SET_SOURCE =
    "app/src/main/java/os/kei/ui/page/main/host/pager/MainPageBackdropSet.kt"

class MainPageBackdropSetTestApp : Application()
