package os.kei.ui.page.main.student.catalog.component.bgm

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class BaGuideBgmAlbumSplitSourceTest {
    @Test
    fun theAlbumPaneIsFixedAndOnlyTheQueueScrolls() {
        val source = sourceFile(ALBUM_CONTENT_SOURCE)
        val split = source.substringAfter("if (columnCount >= 2) {").substringBefore("        return\n")

        // A Box, not a list. Everything in the album pane -- artwork, title, transport, volume -- has to
        // be reachable without scrolling, which is what makes the queue the only thing that moves.
        assertTrue("Box(" in split, "The album pane must not be a scroll container")
        assertTrue("LazyColumn(" in split, "The queue beside it still is one")
        assertEquals(
            1,
            Regex("LazyColumn\\(").findAll(split).count(),
            "Exactly one of the two panes scrolls",
        )
        assertTrue("state = trackListState," in split, "The queue scrolls on its own state")
        assertTrue("hero(true)" in split, "The album pane fits itself to the height it is given")
    }

    @Test
    fun theStackedShapeIsStillOneListWithTheAlbumAsItsFirstItem() {
        val source = sourceFile(ALBUM_CONTENT_SOURCE)
        val stacked = source.substringAfter("        return\n    }\n")

        assertTrue("""key = "ba-guide-bgm-album-hero"""" in stacked)
        assertTrue("hero(false)" in stacked, "Stacked, the album is sized from the width as it always was")
        assertTrue("queueItems()" in stacked)
        assertTrue("state = trackListState" !in stacked, "One list means one state")
    }

    @Test
    fun theArtworkIsMeasuredLastOnlyWhenThePaneOwnsTheHeight() {
        val source = sourceFile(ALBUM_HERO_SOURCE)

        // `fill = false` is load-bearing: with it the square keeps its ratio and takes the smaller of the
        // pane's width and the slack; without it the artwork would stretch to the slack and stop being
        // square. The stacked default has no weight at all, because a lazy item has no height to divide.
        assertTrue("Modifier.weight(1f, fill = false)" in source)
        assertTrue("Modifier.fillMaxWidth(0.72f)" in source)
        assertTrue("fillAvailableHeight: Boolean = false," in source)
        assertTrue(
            "Arrangement.spacedBy(12.dp, Alignment.CenterVertically)" in source,
            "A short album sits in the middle of its pane rather than against the top",
        )
    }

    @Test
    fun theChromeWatchesWhicheverPaneCanActuallyScroll() {
        val source = sourceFile(FAVORITE_BGM_CONTENT_SOURCE)

        assertTrue("val trackListState = rememberLazyListState()" in source)
        assertTrue("columnCount = columnCount," in source)
        assertTrue(
            "if (columnCount >= 2) listOf(trackListState) else listOf(listState)" in source,
            "Split, the album pane does not scroll, so watching it would pin the chrome open",
        )
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

private const val ALBUM_CONTENT_SOURCE =
    "app/src/main/java/os/kei/ui/page/main/student/catalog/component/bgm/BaGuideBgmAlbumContent.kt"
private const val ALBUM_HERO_SOURCE =
    "app/src/main/java/os/kei/ui/page/main/student/catalog/component/bgm/BaGuideBgmAlbumHero.kt"
private const val FAVORITE_BGM_CONTENT_SOURCE =
    "app/src/main/java/os/kei/ui/page/main/student/catalog/page/BaGuideFavoriteBgmMusicContent.kt"
