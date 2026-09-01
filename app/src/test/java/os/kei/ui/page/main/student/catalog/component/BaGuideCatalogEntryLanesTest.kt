package os.kei.ui.page.main.student.catalog.component

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class BaGuideCatalogEntryLanesTest {
    @Test
    fun `one column is the whole list, untouched`() {
        val entries = ('a'..'e').toList()

        val lanes = baGuideCatalogEntryLanes(entries = entries, columnCount = 1)

        assertEquals(1, lanes.size)
        assertEquals(entries, lanes.single())
    }

    @Test
    fun `two lanes alternate, so each lane is still the list in order`() {
        val lanes = baGuideCatalogEntryLanes(entries = ('a'..'g').toList(), columnCount = 2)

        // Not a first half and a second half. Halving would put 'e' at the top of the right lane, and
        // since the lanes scroll apart there is no arrangement that puts it back next to 'd'.
        assertEquals(listOf('a', 'c', 'e', 'g'), lanes[0])
        assertEquals(listOf('b', 'd', 'f'), lanes[1])

        // Level with each other, reading across a row is the list from start to end.
        val readAcross = lanes[0].zip(lanes[1]).flatMap { (left, right) -> listOf(left, right) }
        assertEquals(('a'..'f').toList(), readAcross)

        // And each lane on its own is ascending, which is the property that survives them drifting apart.
        assertEquals(lanes[0].sorted(), lanes[0])
        assertEquals(lanes[1].sorted(), lanes[1])
    }

    @Test
    fun `every entry lands in exactly one lane`() {
        listOf(0, 1, 2, 7, 36, 275).forEach { size ->
            val entries = (0 until size).toList()
            val lanes = baGuideCatalogEntryLanes(entries = entries, columnCount = 2)

            assertEquals(entries, lanes.flatten().sorted())
            assertEquals(size, lanes.sumOf { lane -> lane.size })
        }
    }

    @Test
    fun `a lane entry's place in the flat list is arithmetic`() {
        val entries = (0 until 9).toList()
        val lanes = baGuideCatalogEntryLanes(entries = entries, columnCount = 2)

        lanes.forEachIndexed { lane, laneEntries ->
            laneEntries.forEachIndexed { laneEntryIndex, entry ->
                assertEquals(
                    entry,
                    baGuideCatalogLaneEntryIndex(lane, laneEntryIndex, columnCount = 2),
                )
            }
        }
    }

    @Test
    fun `preloading sees both lanes, wherever each has drifted to`() {
        // Lane 0 near the top, lane 1 scrolled well down: the screen is both, so preloading is both.
        val indices =
            baGuideCatalogVisibleLaneEntryIndices(
                laneVisibleItemIndices = listOf(listOf(0, 1, 2), listOf(20, 21)),
                laneEntryStartIndices = listOf(0, 0),
                columnCount = 2,
                entryCount = 100,
            )

        // Lane 0 items 0..2 are entries 0, 2, 4; lane 1 items 20..21 are entries 41, 43.
        assertEquals(listOf(0, 2, 4, 41, 43), indices)
    }

    @Test
    fun `a status card offsets only the lane that carries it`() {
        // The error card is item 0 of the leading lane, so that lane's first entry is item 1. The other
        // lane has no status card and starts at item 0 — which is exactly why the offset is per lane.
        val indices =
            baGuideCatalogVisibleLaneEntryIndices(
                laneVisibleItemIndices = listOf(listOf(0, 1, 2), listOf(0, 1)),
                laneEntryStartIndices = listOf(1, 0),
                columnCount = 2,
                entryCount = 100,
            )

        // Lane 0: item 0 is the card and drops out, items 1..2 are entries 0 and 2.
        // Lane 1: items 0..1 are entries 1 and 3.
        assertEquals(listOf(0, 1, 2, 3), indices)
    }

    @Test
    fun `one lane resolves exactly as it did before lanes existed`() {
        val indices =
            baGuideCatalogVisibleLaneEntryIndices(
                laneVisibleItemIndices = listOf(listOf(2, 3, 4)),
                laneEntryStartIndices = listOf(1),
                columnCount = 1,
                entryCount = 100,
            )

        // Item index minus the start offset is the entry index. Nothing about a phone's preloading moves.
        assertEquals(listOf(1, 2, 3), indices)
    }

    @Test
    fun `entries past the end of the list are dropped`() {
        // A lane can be showing its trailing status item, whose index is past every entry it holds.
        val indices =
            baGuideCatalogVisibleLaneEntryIndices(
                laneVisibleItemIndices = listOf(listOf(0, 1, 2), listOf(0, 1, 2)),
                laneEntryStartIndices = listOf(0, 0),
                columnCount = 2,
                entryCount = 5,
            )

        // Lane 0 items 0..2 are entries 0, 2, 4; lane 1's would be 1, 3, 5 -- and 5 is past the end.
        assertEquals(listOf(0, 1, 2, 3, 4), indices)
        assertTrue(indices.all { index -> index < 5 })
    }

    @Test
    fun `nothing visible resolves to nothing`() {
        assertTrue(
            baGuideCatalogVisibleLaneEntryIndices(
                laneVisibleItemIndices = listOf(emptyList(), emptyList()),
                laneEntryStartIndices = listOf(0, 0),
                columnCount = 2,
                entryCount = 10,
            ).isEmpty(),
        )
        assertTrue(
            baGuideCatalogVisibleLaneEntryIndices(
                laneVisibleItemIndices = listOf(listOf(0, 1)),
                laneEntryStartIndices = listOf(0),
                columnCount = 1,
                entryCount = 0,
            ).isEmpty(),
        )
    }
}
