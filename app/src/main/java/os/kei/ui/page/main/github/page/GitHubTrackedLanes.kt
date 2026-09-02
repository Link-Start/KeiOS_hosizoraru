package os.kei.ui.page.main.github.page

import os.kei.feature.github.model.GitHubTrackedApp

/**
 * How the tracked list is split between the wide layout's two lanes.
 *
 * [first] is the browsing lane: every card that is not being held open, collapsed, in list order.
 * [second] starts empty and fills with cards the reader has opened, plus anything they pinned.
 */
internal data class GitHubTrackedLanes(
    val first: List<GitHubTrackedApp>,
    val second: List<GitHubTrackedApp>,
)

/**
 * Splits [sortedTracked] into the browsing lane and the reading lane.
 *
 * The problem this solves is specific to a wide window: a tracked card expands to several hundred dp of
 * versions, assets and health, so opening one on a tablet leaves a column showing one card and part of
 * another. Two lanes of collapsed cards do not help on their own, because expanding still eats the lane it
 * is in. So the card *moves*: it leaves the browsing lane and opens in the reading lane beside it, and the
 * list you were scanning keeps its full height.
 *
 * [detainedIds] is why a collapsed card can still be in the reading lane. A card that has been opened stays
 * there after it is closed, so closing one does not fling it back across the page while the reader is still
 * looking at it; the page clears that set when it stops being the visible page, and the card goes home
 * between visits rather than during one.
 *
 * [pinnedIds] are permanent residents of the reading lane. On a phone a pin means the top of the list; here
 * it means the lane that does not scroll away, which is the same intent in the shape the window allows.
 *
 * Order in the reading lane is pins first — in the order they were pinned — then everything opened this
 * visit, in the list's own order. Both lanes stay in [sortedTracked]'s order otherwise, so neither becomes
 * a pile in the order things happened to be touched.
 */
internal fun githubTrackedLanesFor(
    sortedTracked: List<GitHubTrackedApp>,
    pinnedIds: List<String>,
    detainedIds: Collection<String>,
): GitHubTrackedLanes {
    if (sortedTracked.isEmpty()) return GitHubTrackedLanes(first = emptyList(), second = emptyList())
    val pinnedOrder = pinnedIds.withIndex().associate { (index, id) -> id to index }
    val secondLaneIds = pinnedOrder.keys + detainedIds
    if (secondLaneIds.isEmpty()) return GitHubTrackedLanes(first = sortedTracked, second = emptyList())
    val (second, first) = sortedTracked.partition { item -> item.id in secondLaneIds }
    return GitHubTrackedLanes(
        first = first,
        // A pinned card outranks one merely opened, and two pinned cards keep the order they were pinned
        // in. Everything else falls back to the list's order, which `sortedTracked` already carries.
        second = second.sortedBy { item -> pinnedOrder[item.id] ?: Int.MAX_VALUE },
    )
}

/**
 * The single-column order: pinned tracks first, then the list's own order.
 *
 * Ahead of the archived partition as well as the sort, because a pin says "keep this where I can see it"
 * and a track with nothing to report is exactly the case the reader reached for the pin to solve.
 */
internal fun githubTrackedSortedWithPinsFirst(
    sortedTracked: List<GitHubTrackedApp>,
    pinnedIds: List<String>,
): List<GitHubTrackedApp> {
    if (pinnedIds.isEmpty() || sortedTracked.isEmpty()) return sortedTracked
    val pinnedOrder = pinnedIds.withIndex().associate { (index, id) -> id to index }
    if (sortedTracked.none { item -> item.id in pinnedOrder }) return sortedTracked
    val (pinned, rest) = sortedTracked.partition { item -> item.id in pinnedOrder }
    return pinned.sortedBy { item -> pinnedOrder.getValue(item.id) } + rest
}
