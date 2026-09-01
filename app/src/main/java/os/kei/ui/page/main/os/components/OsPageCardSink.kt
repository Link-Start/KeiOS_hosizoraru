package os.kei.ui.page.main.os.components

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

/** One OS card, with the identity a lazy container keys and recycles on. */
@Immutable
internal class OsCardEntry(
    val key: String,
    val contentType: String,
    val content: @Composable () -> Unit,
)

/**
 * Where an OS card goes.
 *
 * The card builders used to be `LazyListScope` extensions, which tied every card to being a row of one
 * list. The two-column layout needs the same cards sorted into lanes *before* anything is emitted — the
 * second column is reserved for the activity shortcuts, and a staggered grid cannot honour that because it
 * places by height, not by kind. A sink is the smallest change that frees them: each builder still says
 * "here is a card, with this key and this recycling type", and the caller decides whether that becomes a
 * list item or a cell in a column.
 */
internal fun interface OsCardSink {
    fun card(
        key: String,
        contentType: String,
        content: @Composable () -> Unit,
    )
}

/** Collects the cards a builder block emits, in order, without laying any of them out. */
internal fun buildOsCards(block: OsCardSink.() -> Unit): List<OsCardEntry> =
    buildList {
        val sink = OsCardSink { key, contentType, content -> add(OsCardEntry(key, contentType, content)) }
        sink.block()
    }

/** The single-column layout: one list item per card, exactly as before. */
internal fun LazyListScope.osCardItems(cards: List<OsCardEntry>) {
    cards.forEach { entry ->
        item(key = entry.key, contentType = entry.contentType) {
            entry.content()
        }
    }
}

/** The two lanes of the wide layout, or `null` when there is nothing to put in the second one. */
@Immutable
internal class OsCardLanes(
    val primary: List<OsCardEntry>,
    val secondary: List<OsCardEntry>,
)

internal const val OS_ACTIVITY_CARD_CONTENT_TYPE = "os_shortcut_activity_card"

/**
 * Splits the cards into the two columns.
 *
 * The second column is the activity shortcuts. They are the page's *own* content — the ones a teacher adds,
 * names and reorders — while everything above them is a fixed readout of the device, so giving them a
 * standing column is what stops them being pushed off the bottom of a long system list. That also makes the
 * assignment stable: it does not move when a card is expanded, which a height-packed layout could not
 * promise.
 *
 * With no activity card showing — none added, or all of them hidden — the column would be empty, so the
 * Shell card moves across instead. It is the one card on this page that is used rather than read, and it
 * is where a teacher goes next when there are no shortcuts yet.
 *
 * If even that is hidden there is nothing worth reserving a column for, and `null` sends the page back to
 * one, which is better than a wide page with a dead half.
 */
internal fun osCardLanes(
    cards: List<OsCardEntry>,
    shellCardKey: String,
): OsCardLanes? {
    val activities = cards.filter { entry -> entry.contentType == OS_ACTIVITY_CARD_CONTENT_TYPE }
    val secondary =
        activities.ifEmpty { cards.filter { entry -> entry.key == shellCardKey } }
    if (secondary.isEmpty()) return null
    val secondaryKeys = secondary.mapTo(mutableSetOf()) { entry -> entry.key }
    return OsCardLanes(
        primary = cards.filterNot { entry -> entry.key in secondaryKeys },
        secondary = secondary,
    )
}
