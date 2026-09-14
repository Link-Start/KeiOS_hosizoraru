package os.kei.ui.page.main.github.section

import java.io.File
import kotlin.test.assertTrue
import org.junit.Test

/**
 * The explanation rows earn their place by almost never appearing.
 *
 * Both are one `if` away from being noise on every card in the list: the selection basis behind the
 * version card's own disclosure, the retired-pre-release line behind the reader having asked for
 * pre-releases at all. Neither gate is expressible in the pure functions that produce the text, and
 * losing one fails quietly — the page still renders, just with a sentence nobody asked for on every
 * row. So the gates are pinned here.
 */
class GitHubTrackedItemDecisionCardWiringSourceTest {
    @Test
    fun theSelectionBasisStaysBehindTheVersionCardDisclosure() {
        val source = sourceFile(VERSION_SECTIONS)

        // Same AnimatedVisibility as the release meta it qualifies, so opening the stable version
        // card is what reveals it -- a reader who has not opened it is not asking how it was picked.
        val gate = source.substringAfter("val selectionBasis = githubSelectionBasisText(")
            .substringBefore("private fun GitHubTrackedItemPreReleaseVersionSection")
        assertTrue(
            "visible = stableExpanded && (stableReleaseMeta.isNotBlank() || selectionBasis != null)"
                in gate,
            "the basis must share the stable card's disclosure, and must be able to open it alone",
        )
        assertTrue(
            "selectionBasis?.let { basis ->" in gate,
            "and must not draw at all when the ranking explains itself",
        )
    }

    @Test
    fun theRetiredPreReleaseLineOnlySpeaksToReadersWhoWantedOne() {
        val cards = sourceFile(DECISION_CARDS)

        // AbandonedLine only. A pre-release superseded by the stable that followed it is the
        // ordinary shape of a release history; explaining that every time is noise.
        assertTrue(
            "if (note.preReleaseRejection != GitHubReleaseRejection.AbandonedLine) return null"
                in cards,
        )
        assertTrue(
            "item.preferPreRelease || itemLookupConfig.checkAllTrackedPreReleases" in cards,
            "to a reader not tracking pre-releases the row was never there to lose",
        )

        val sections = sourceFile(VERSION_SECTIONS)
        assertTrue(
            "if (!showPreRelease) {" in sections && "GitHubRetiredPreReleaseCard(" in sections,
            "it stands where the pre-release row would be, not in addition to it",
        )
    }
}

private const val VERSION_SECTIONS =
    "app/src/main/java/os/kei/ui/page/main/github/section/GitHubTrackedItemVersionSections.kt"
private const val DECISION_CARDS =
    "app/src/main/java/os/kei/ui/page/main/github/section/GitHubTrackedItemDecisionCards.kt"

private fun sourceFile(path: String): String {
    var directory = File("").absoluteFile
    while (!File(directory, path).exists()) {
        directory = directory.parentFile ?: error("cannot locate $path")
    }
    return File(directory, path).readText()
}
