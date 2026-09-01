package os.kei.ui.page.main.student.catalog.page

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
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = BaGuideCatalogPageBackdropTestApp::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class BaGuideCatalogPageBackdropTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sceneBackdropIsIndependentAndStableAcrossRecomposition() {
        lateinit var recompositionSignal: MutableIntState
        val observedSceneBackdrops = mutableListOf<Pair<Int, Backdrop>>()
        var pageChromeBackdrop: Backdrop? = null
        var bottomChromeBackdrop: Backdrop? = null

        composeRule.setContent {
            val signal = remember { mutableIntStateOf(0) }
            recompositionSignal = signal
            val currentPageChromeBackdrop = rememberLayerBackdrop()
            val currentBottomChromeBackdrop = rememberLayerBackdrop()
            val currentSceneBackdrop = rememberBaGuideCatalogSceneBackdrop()
            val stateRevision = signal.intValue

            SideEffect {
                pageChromeBackdrop = currentPageChromeBackdrop
                bottomChromeBackdrop = currentBottomChromeBackdrop
                observedSceneBackdrops += stateRevision to currentSceneBackdrop
            }
            Box(modifier = Modifier.size(1.dp))
        }

        composeRule.runOnIdle {
            val firstSceneBackdrop = observedSceneBackdrops.first().second
            assertNotNull(pageChromeBackdrop)
            assertNotNull(bottomChromeBackdrop)
            assertNotSame(pageChromeBackdrop, firstSceneBackdrop)
            assertNotSame(bottomChromeBackdrop, firstSceneBackdrop)
            recompositionSignal.intValue += 1
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(1, observedSceneBackdrops.last().first)
            observedSceneBackdrops.forEach { (_, backdrop) ->
                assertSame(observedSceneBackdrops.first().second, backdrop)
            }
        }
    }

    @Test
    fun producerDrawsTheRealPageBackgroundBeforePagerConsumers() {
        val source = sourceFile(PAGE_CONTENT_SOURCE)
        val producerIndex = source.indexOf(".layerBackdrop(catalogSceneBackdrop)")
        val panelBackgroundIndex = source.indexOf(".background(panelBackground)", startIndex = producerIndex.coerceAtLeast(0))
        val gradientIndex = source.indexOf("Brush.verticalGradient(", startIndex = panelBackgroundIndex.coerceAtLeast(0))
        val pagerIndex = source.indexOf("BaGuideCatalogPagePager(", startIndex = gradientIndex.coerceAtLeast(0))

        assertTrue(producerIndex >= 0, "Catalog page must own one scene Backdrop producer")
        assertTrue(panelBackgroundIndex > producerIndex, "Panel background must be drawn inside the producer layer")
        assertTrue(gradientIndex > panelBackgroundIndex, "Page gradient must be drawn inside the producer layer")
        assertTrue(pagerIndex > gradientIndex, "Producer sibling must be composed before Pager consumers")
        assertTrue(
            "CompositionLocalProvider(LocalLiquidParentBackdrop provides catalogSceneBackdrop)" in source,
            "Catalog cards must inherit the real page scene Backdrop",
        )
        assertEquals(1, source.occurrencesOf(".layerBackdrop(catalogSceneBackdrop)"))
        assertEquals(1, source.occurrencesOf("val catalogSceneBackdrop = rememberBaGuideCatalogSceneBackdrop()"))
        assertEquals(1, source.occurrencesOf("catalogSceneBackdrop = catalogSceneBackdrop"))
    }

    @Test
    fun pagerAndFiveStatusCardsShareThePageSceneBackdrop() {
        val pagerSource = sourceFile(PAGE_PAGER_SOURCE)
        val statusLeafSources = STATUS_LEAF_SOURCES.map(::sourceFile)

        assertEquals(4, pagerSource.occurrencesOf("catalogSceneBackdrop = catalogSceneBackdrop"))
        assertEquals(2, pagerSource.occurrencesOf("catalogSceneBackdrop: Backdrop"))
        assertEquals(5, statusLeafSources.sumOf { it.occurrencesOf("backdrop = catalogSceneBackdrop") })
        assertEquals(listOf(2, 2, 1), statusLeafSources.map { it.occurrencesOf("backdrop = catalogSceneBackdrop") })
        statusLeafSources.forEach { source ->
            assertEquals(1, source.occurrencesOf("catalogSceneBackdrop: Backdrop"))
            assertEquals(0, source.occurrencesOf("rememberLayerBackdrop"))
            assertEquals(0, source.occurrencesOf("statusBackdrop"))
            assertEquals(0, source.occurrencesOf(".layerBackdrop("))
        }
    }

    @Test
    fun statusBackdropMigrationPreservesLazyListContracts() {
        val catalogSource = sourceFile(STATUS_LEAF_SOURCES[0])
        val memorySource = sourceFile(STATUS_LEAF_SOURCES[1])
        val studentBgmSource = sourceFile(STATUS_LEAF_SOURCES[2])

        assertSourceContains(
            source = catalogSource,
            "laneStates = laneStates",
            ".nestedScroll(nestedScrollConnection)",
            "key = \"ba-guide-catalog-error-\${tab.name}\"",
            "key = \"ba-guide-catalog-empty-\${tab.name}\"",
        )
        assertSourceContains(
            source = memorySource,
            "laneStates = laneStates",
            ".nestedScroll(nestedScrollConnection)",
            "key = \"memory-lobby-error\"",
            "key = \"memory-lobby-empty\"",
            "key = { entry -> \"memory-lobby-\${entry.contentId}\" }",
        )
        assertSourceContains(
            source = studentBgmSource,
            "laneStates = laneStates",
            "userScrollEnabled = !sliderInteractionActive",
            ".nestedScroll(nestedScrollConnection)",
            "key = \"student-bgm-empty\"",
            "key = { it.entry.contentId }",
        )
        STATUS_LEAF_SOURCES.map(::sourceFile).forEach { source ->
            assertSourceContains(
                source = source,
                "innerPadding.calculateTopPadding()",
                // The page edge goes through the helpers, not the raw token, and the two sides are
                // deliberately *different* helpers: only the leading edge can have the sidebar rail on it.
                // The token is still correct inside a card, where there is no page edge to centre against.
                // Both are identical to the token on every phone, so this pins a contract rather than a look.
                "startPadding = appPageEdgePaddingStart()",
                "endPadding = appPageEdgePaddingEnd()",
                // The gap is the shared container's now, and it is the same token in both axes: the
                // lanes' inside edge should read as the same rhythm as the space between rows.
                "verticalGap = entryListGap",
                "horizontalGap = entryListGap",
            )
            // All three leaves stack, so all three shift their list up inside `AppEdgeStackKeepAlive` and
            // must add that headroom back to their own content inset. Passing the page inset straight
            // through — which is what this used to assert — would now start the first card off screen.
            assertTrue(
                "topPadding = appEdgeStackKeepAliveTopPadding(" in source,
                "A stacking leaf must absorb the keep-alive headroom into its content inset",
            )
        }
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

private fun String.occurrencesOf(value: String): Int = Regex(Regex.escape(value)).findAll(this).count()

private fun assertSourceContains(
    source: String,
    vararg expectedFragments: String,
) {
    expectedFragments.forEach { fragment ->
        assertTrue(fragment in source, "Expected source fragment: $fragment")
    }
}

private const val PAGE_CONTENT_SOURCE =
    "app/src/main/java/os/kei/ui/page/main/student/catalog/page/BaGuideCatalogPageContent.kt"

private const val PAGE_PAGER_SOURCE =
    "app/src/main/java/os/kei/ui/page/main/student/catalog/page/BaGuideCatalogPagePager.kt"

private val STATUS_LEAF_SOURCES =
    listOf(
        "app/src/main/java/os/kei/ui/page/main/student/catalog/component/BaGuideCatalogV2ListContent.kt",
        "app/src/main/java/os/kei/ui/page/main/student/catalog/component/BaGuideMemoryLobbyTabContent.kt",
        "app/src/main/java/os/kei/ui/page/main/student/catalog/component/BaGuideStudentBgmTabContent.kt",
    )

class BaGuideCatalogPageBackdropTestApp : Application()
