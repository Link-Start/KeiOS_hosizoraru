package os.kei.ui.page.main.github.release

/**
 * How a page of releases is split between the wide layout's two lanes.
 *
 * [first] is the browsing lane: the release history, collapsed, in the order the repository publishes it.
 * [second] holds the releases worth reading — the two the page anchors there, plus whatever the reader opens.
 */
internal data class GitHubReleaseLanes(
    val first: List<IndexedValue<GitHubReleaseRow>>,
    val second: List<IndexedValue<GitHubReleaseRow>>,
)

/**
 * The releases that belong in the reading lane before the reader touches anything.
 *
 * The latest release and the newest pre-release are what this page is opened for: one is what you get if
 * you do nothing, the other is what you get if you want the next thing early, and the whole reason to
 * come down here is usually to weigh one against the other. They are already the two the page opens by
 * default — this puts them where an open card belongs rather than leaving them there as a side effect of
 * being open, so collapsing one does not make it look like any other row in the history.
 *
 * [firstPage] gates both, for the reason the default expansion is also once-only: `latest` is flagged on
 * the first page and nowhere else, and "the newest pre-release *on this page*" is a different and useless
 * thing five pages into the history — a repository whose CI publishes one per push would anchor an
 * arbitrary old build on every page. A release looked up by tag carries neither flag, so it anchors
 * nothing on its own.
 *
 * Atom mode marks neither, because the feed cannot tell them apart, so nothing is anchored there. The
 * page's own expansion fallback still opens the newest entry, which puts that one in the lane the
 * ordinary way.
 */
internal fun githubReleaseAnchorIds(
    rows: List<GitHubReleaseRow>,
    firstPage: Boolean,
): Set<String> {
    if (!firstPage) return emptySet()
    val latest = rows.firstOrNull { row -> row.entry.latest }
    // First match, not every match: the *newest* pre-release, since the rows arrive newest first.
    val newestPreRelease = rows.firstOrNull { row -> row.entry.prerelease }
    return setOfNotNull(latest?.entry?.id, newestPreRelease?.entry?.id)
}

/**
 * Splits a page of releases into the browsing lane and the reading lane.
 *
 * The same problem as the tracked list one level up, and worse here: an open release is a name, a set of
 * pills, release notes and an asset panel, so on a tablet one of them fills the column and the history you
 * were scanning is gone. The card moves instead — it leaves the browsing lane and opens beside it, and the
 * list of versions keeps its full height. Which is what this page is *for*: picking a version off a list is
 * a comparison, and a comparison needs both things visible.
 *
 * [readingIds] is [githubReleaseAnchorIds] plus every release opened since this page of results was loaded —
 * not just the ones open now. A release that has been read stays in the reading lane after it is closed, so
 * closing one does not fling it back across the page while the reader is still looking at it. What the reader
 * opened is forgotten on paging, because that half of the lane belongs to the list of results being read
 * rather than to the visit: page forward and only the anchors are left, which is right, since none of those
 * other releases are on screen any more.
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
