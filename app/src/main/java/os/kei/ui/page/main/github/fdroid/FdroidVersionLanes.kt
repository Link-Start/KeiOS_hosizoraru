package os.kei.ui.page.main.github.fdroid

/**
 * How the version history splits across two columns on a wide window.
 *
 * The same mechanic the release list uses, and for the same reason: picking a build off a history is a
 * comparison, and expanding a card inside the list you are comparing against costs you the list. So an
 * opened build moves to a second lane and the history keeps its full height beside it.
 *
 * Kept as a pure function over rows rather than a piece of the page, because that is the part worth
 * testing — the page below it only decides which lane gets the notices.
 */
internal data class FdroidVersionLanes(
    val first: List<IndexedValue<FdroidVersionRow>>,
    val second: List<IndexedValue<FdroidVersionRow>>,
)

/**
 * The builds that start in the reading lane, by rule rather than by being opened.
 *
 * A history's two important rows are the build this track would install and the newest pre-release, the
 * same pair the release list anchors — and here they are worth more, because on F-Droid neither is
 * simply the top of the list. The recommended build is whatever the track's selection mode, anti-feature
 * policy and this device's SDK level leave standing, which on a repository that publishes for newer
 * Androids can be several rows down; and a repository that ships betas interleaves them with stable
 * builds rather than flagging them.
 *
 * Anchoring is intrinsic to the row, so filtering the history does not invent an anchor: narrow the list
 * to something the recommended build is not in and nothing is anchored, which is correct — there is no
 * "the one you would install" among search results that exclude it.
 */
internal fun fdroidVersionAnchorIds(rows: List<FdroidVersionRow>): Set<String> {
    val recommended = rows.firstOrNull { row -> row.recommended }
    val newestPreRelease = rows.firstOrNull { row -> row.channel.isPreRelease }
    return setOfNotNull(recommended?.id, newestPreRelease?.id)
}

/**
 * Splits the history into the lane being browsed and the lane being read.
 *
 * Both lanes keep the history's own order. A history sorted by the order you happened to tap it in is
 * not a history, and version codes only mean anything read in sequence.
 */
internal fun fdroidVersionLanesFor(
    rows: List<FdroidVersionRow>,
    readingIds: Set<String>,
): FdroidVersionLanes {
    val indexed = rows.withIndex().toList()
    if (indexed.isEmpty() || readingIds.isEmpty()) {
        return FdroidVersionLanes(first = indexed, second = emptyList())
    }
    val (second, first) = indexed.partition { row -> row.value.id in readingIds }
    return FdroidVersionLanes(first = first, second = second)
}
