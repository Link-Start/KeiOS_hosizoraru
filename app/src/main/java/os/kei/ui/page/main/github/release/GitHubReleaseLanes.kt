package os.kei.ui.page.main.github.release

/**
 * How a page of releases is split between the wide layout's two lanes.
 *
 * [first] is the browsing lane: the release history, collapsed, in the order the repository publishes it.
 * [second] starts empty and fills with the releases the reader has opened.
 */
internal data class GitHubReleaseLanes(
    val first: List<IndexedValue<GitHubReleaseRow>>,
    val second: List<IndexedValue<GitHubReleaseRow>>,
)

/**
 * Splits a page of releases into the browsing lane and the reading lane.
 *
 * The same problem as the tracked list one level up, and worse here: an open release is a name, a set of
 * pills, release notes and an asset panel, so on a tablet one of them fills the column and the history you
 * were scanning is gone. The card moves instead — it leaves the browsing lane and opens beside it, and the
 * list of versions keeps its full height. Which is what this page is *for*: picking a version off a list is
 * a comparison, and a comparison needs both things visible.
 *
 * [readingIds] is every release opened since this page of results was loaded, not just the ones open now. A
 * release that has been read stays in the reading lane after it is closed, so closing one does not fling it
 * back across the page while the reader is still looking at it. Paging clears the set, because the lane
 * belongs to the list of results being read rather than to the visit — page forward and the reading lane is
 * empty again, which is right, since none of those releases are on screen any more.
 *
 * Both lanes keep the page's own order. On a chronological history that is not a preference: a lane sorted
 * by when the reader happened to touch things would stop being a history.
 *
 * Indices are carried through because a lane no longer knows where its rows sat in the flat list, and the
 * first release on the page is tagged for instrumentation.
 */
internal fun githubReleaseLanesFor(
    rows: List<GitHubReleaseRow>,
    readingIds: Set<String>,
): GitHubReleaseLanes {
    val indexed = rows.withIndex().toList()
    if (indexed.isEmpty() || readingIds.isEmpty()) {
        return GitHubReleaseLanes(first = indexed, second = emptyList())
    }
    val (second, first) = indexed.partition { row -> row.value.entry.id in readingIds }
    return GitHubReleaseLanes(first = first, second = second)
}
