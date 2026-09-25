package os.kei.ui.page.main.host.pager

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import os.kei.ui.testing.repoRoot
import os.kei.ui.testing.withoutComments

class MainPagerFrameRateTest {
    @Test
    fun `high frame rate follows pager and predictive back motion lifecycle`() {
        assertFalse(
            shouldPreferHighFrameRateForPagerMotion(
                pagerScrollInProgress = false,
                additionalMotionInProgress = false,
            ),
        )
        assertTrue(
            shouldPreferHighFrameRateForPagerMotion(
                pagerScrollInProgress = true,
                additionalMotionInProgress = false,
            ),
        )
        assertTrue(
            shouldPreferHighFrameRateForPagerMotion(
                pagerScrollInProgress = false,
                additionalMotionInProgress = true,
            ),
        )
    }

    /**
     * A frame-rate vote governs the whole display, so only motion that renders at the panel's peak may cast one.
     *
     * The dynamic background used to vote: `High` pinned the panel to 90 and an explicit 60 pinned it lower,
     * and scrolling and pager motion could not reach the peak. And `FrameRateCategory.High` resolves through
     * the display's `frameRateCategoryRate`, which is {normal = 60, high = 90} on 5eea1f50, so asking for the
     * category capped a 120Hz panel at 90 during the very motion it was meant to smooth. The votes that remain
     * ask for the peak mode read from the display, and the app never selects a mode outright: ARR and thermal
     * policy keep ownership of the actual cadence.
     */
    @Test
    fun `only peak-rate motion votes, and no vote pins a category, a literal rate or a display mode`() {
        val sources = productionSources()
        val voters = sources.filterValues { source -> "preferredFrameRate(" in source }.keys

        assertEquals(
            ALLOWED_VOTERS,
            voters,
            "Only motion that renders at the panel's peak may vote on the display's frame rate",
        )
        sources.forEach { (path, source) ->
            BANNED_VOTES.forEach { banned ->
                assertFalse(banned in source, "$path contains `$banned`; ask for the display's peak mode instead")
            }
        }
    }

    private fun productionSources(): Map<String, String> {
        val root = repoRoot()
        return root
            .listFiles()
            .orEmpty()
            .map { module -> File(module, "src/main") }
            .filter(File::isDirectory)
            .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
            .associate { file -> file.relativeTo(root).invariantSeparatorsPath to file.readText().withoutComments() }
    }

    private companion object {
        val ALLOWED_VOTERS =
            setOf(
                "app/src/main/java/os/kei/ui/page/main/host/pager/MainPagerFrameRate.kt",
                "app/src/main/java/os/kei/ui/page/main/widget/chrome/TabbedPageContentMotion.kt",
            )

        val BANNED_VOTES =
            listOf(
                "preferredFrameRate(FrameRateCategory",
                "preferredFrameRate(120",
                "preferredDisplayModeId",
            )
    }
}
