package os.kei.ui.page.main.github.page

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class GitHubPageBackdropTest {
    @Test
    fun contentProducerPrecedesOverviewTrackedAndDockConsumers() {
        val source = sourceFile(GITHUB_MAIN_CONTENT_SOURCE)
        val sceneIndex = source.indexOf("MainPageContentBackdropScene(")
        val sceneBackdropIndex =
            source.indexOf(
                "contentProducer = null,",
                startIndex = sceneIndex.coerceAtLeast(0),
            )
        val scaffoldIndex = source.indexOf("AppScaffold(", startIndex = sceneBackdropIndex.coerceAtLeast(0))
        val overviewIndex = source.indexOf("GitHubOverviewCard(", startIndex = scaffoldIndex.coerceAtLeast(0))
        val overviewBackdropIndex =
            source.indexOf("backdrop = surfaces.contentBackdrop,", startIndex = overviewIndex.coerceAtLeast(0))
        val trackedIndex =
            source.indexOf("GitHubTrackedItemsSurfaces(", startIndex = overviewBackdropIndex.coerceAtLeast(0))
        val trackedBackdropIndex =
            source.indexOf("contentBackdrop = surfaces.contentBackdrop,", startIndex = trackedIndex.coerceAtLeast(0))
        val dockIndex =
            source.indexOf("AppFloatingVerticalSearchActionDock(", startIndex = trackedBackdropIndex.coerceAtLeast(0))
        val dockBackdropIndex =
            source.indexOf("backdrop = surfaces.topBarMaterial,", startIndex = dockIndex.coerceAtLeast(0))

        assertTrue(sceneIndex >= 0, "GitHub page must host the shared content Backdrop scene")
        assertTrue(sceneBackdropIndex > sceneIndex, "The scene must produce the page content identity")
        assertTrue(scaffoldIndex > sceneBackdropIndex, "The content producer must precede the Scaffold tree")
        assertTrue(overviewBackdropIndex > overviewIndex, "Overview must consume the page content identity")
        assertTrue(trackedBackdropIndex > trackedIndex, "Tracked cards must consume the page content identity")
        assertTrue(dockBackdropIndex > dockIndex, "The floating dock must sample the scrolling-content identity")
        assertTrue(overviewIndex < trackedIndex, "Tracked content must remain after the overview")
        assertTrue(trackedIndex < dockIndex, "The floating dock must remain after scrolling content")
        assertEquals(1, source.occurrencesOf("MainPageContentBackdropScene("))
        assertEquals(0, source.occurrencesOf(".layerBackdrop(surfaces.contentBackdrop)"))
    }

    @Test
    fun topBarProducerKeepsAnIdentitySeparateFromContentConsumers() {
        val source = sourceFile(GITHUB_MAIN_CONTENT_SOURCE)
        val topBarProducerIndex = source.indexOf(".layerBackdrop(surfaces.topBarProducer)")
        val contentSceneIndex = source.indexOf("contentProducer = null,")
        val titleConsumerIndex = source.indexOf("titleBackdrop = surfaces.topBarMaterial,")

        assertTrue(contentSceneIndex >= 0, "GitHub scene must use the content Backdrop identity")
        assertTrue(titleConsumerIndex > contentSceneIndex, "The title must consume the top-bar identity")
        assertTrue(topBarProducerIndex > titleConsumerIndex, "Scrolling content must produce the top-bar identity")
        assertEquals(1, source.occurrencesOf(".layerBackdrop(surfaces.topBarProducer)"))
        assertEquals(1, source.occurrencesOf("titleBackdrop = surfaces.topBarMaterial,"))
    }

    @Test
    fun sceneMigrationPreservesScrollingAndLazyItemContracts() {
        val source = sourceFile(GITHUB_MAIN_CONTENT_SOURCE)
        val sceneIndex = source.indexOf("MainPageContentBackdropScene(")
        val rootTagIndex =
            source.indexOf(
                ".testTag(KeiOsTestTags.GitHubPageRoot)",
                startIndex = sceneIndex.coerceAtLeast(0),
            )
        val overviewIndex =
            source.indexOf("GitHubOverviewCard(", startIndex = rootTagIndex.coerceAtLeast(0))
        val listIndex = source.indexOf("AppPageLazyColumn(", startIndex = rootTagIndex.coerceAtLeast(0))

        assertTrue(rootTagIndex > sceneIndex, "The page root tag must remain on the scene container")
        assertTrue(overviewIndex > rootTagIndex, "The overview hub must render inside the page scene")
        assertTrue(
            listIndex > overviewIndex,
            "The overview hub stays pinned above the scrolling list instead of being a lazy item",
        )
        assertTrue(".nestedScroll(layout.scrollBehavior.nestedScrollConnection)" in source)
        assertTrue("AppEdgeStackKeepAlive(" in source)
        assertTrue("state = layout.listState," in source)
        // Hoisted into locals now that two lane shapes share them, so both branches are laid out the
        // same way by construction rather than by two copies staying in step.
        assertTrue(
            "val listInnerPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding())" in source,
        )
        // The keep-alive box shifts the list up by its headroom, so the list's own top inset has to
        // absorb it. The compiler cannot catch a host that adopts the shift and forgets the inset.
        assertTrue("val listTopExtra = appEdgeStackKeepAliveTopPadding(AppEdgeStackListTopInset)" in source)
        assertTrue(
            "appPageBottomPaddingWithFloatingOverlay(layout.contentBottomPadding)" in source,
        )
        assertTrue("topExtra = listTopExtra," in source)
        assertTrue("bottomExtra = listBottomExtra," in source)
    }

    @Test
    fun pullToRefreshReplacesTheVisibleDockRefreshAction() {
        val source = sourceFile(GITHUB_MAIN_CONTENT_SOURCE)
        val pullToRefreshIndex = source.indexOf("PullToRefresh(")
        val pullRefreshCallbackIndex =
            source.indexOf("actions.onRefreshVisibleTracked()", startIndex = pullToRefreshIndex.coerceAtLeast(0))
        val dockIndex = source.indexOf("AppFloatingVerticalSearchActionDock(")
        val hiddenRefreshActionIndex =
            source.indexOf("showRefreshAction = false,", startIndex = dockIndex.coerceAtLeast(0))

        assertTrue(pullToRefreshIndex >= 0, "GitHub content must keep pull-to-refresh")
        assertTrue(
            pullRefreshCallbackIndex > pullToRefreshIndex,
            "Pull-to-refresh must refresh the visible tracked items",
        )
        assertTrue(dockIndex > pullRefreshCallbackIndex, "The floating dock must remain after pull-to-refresh content")
        assertTrue(hiddenRefreshActionIndex > dockIndex, "The GitHub dock must hide its redundant refresh action")
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

private const val GITHUB_MAIN_CONTENT_SOURCE =
    "app/src/main/java/os/kei/ui/page/main/github/section/GitHubMainContentSection.kt"
