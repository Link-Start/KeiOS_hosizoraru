package os.kei.ui.page.main.student.catalog.component

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class BaGuideCatalogEntryRowsTest {
    @Test
    fun `one column is one entry per row, unchanged`() {
        val rows = baGuideCatalogEntryRows(entries = ('a'..'e').toList(), columnsPerRow = 1)

        assertEquals(5, rows.size)
        assertEquals(('a'..'e').toList(), rows.flatMap { it.entries })
        assertEquals(listOf(0, 1, 2, 3, 4), rows.map { it.startIndex })
        assertTrue(rows.none { it.fullSpan })
    }

    @Test
    fun `two columns flow row-major, so reading order is the list order`() {
        val rows = baGuideCatalogEntryRows(entries = ('a'..'g').toList(), columnsPerRow = 2)

        // Left to right, top to bottom, is the list from start to end. This is the whole contract: two
        // columns must not become a first half and a second half.
        assertEquals(('a'..'g').toList(), rows.flatMap { it.entries })
        assertEquals(
            listOf(listOf('a', 'b'), listOf('c', 'd'), listOf('e', 'f'), listOf('g')),
            rows.map { it.entries },
        )
        assertEquals(listOf(0, 2, 4, 6), rows.map { it.startIndex })
    }

    @Test
    fun `an odd last entry is a short row, not a full span`() {
        val rows = baGuideCatalogEntryRows(entries = listOf(1, 2, 3), columnsPerRow = 2)

        // A leftover keeps a column's width so it lines up under the column above it; only an entry that
        // asked for the whole row gets it. The layout tells them apart by this flag alone.
        assertEquals(listOf(listOf(1, 2), listOf(3)), rows.map { it.entries })
        assertTrue(rows.none { it.fullSpan })
    }

    @Test
    fun `a full-span entry takes its own row and ends the one being filled`() {
        val rows =
            baGuideCatalogEntryRows(
                entries = listOf(0, 1, 2, 3, 4, 5),
                columnsPerRow = 2,
                isFullSpan = { it == 1 || it == 4 },
            )

        // 1 is full span, so 0 cannot be paired with it and cannot be paired with 2 either -- that would
        // put 2 before 1. Order wins over packing, every time.
        assertEquals(
            listOf(listOf(0), listOf(1), listOf(2, 3), listOf(4), listOf(5)),
            rows.map { it.entries },
        )
        assertEquals(listOf(false, true, false, true, false), rows.map { it.fullSpan })
        assertEquals(listOf(0, 1, 2, 4, 5), rows.map { it.startIndex })
        assertEquals(listOf(0, 1, 2, 3, 4, 5), rows.flatMap { it.entries })
    }

    @Test
    fun `every entry appears exactly once, whatever the spans`() {
        val entries = (0 until 37).toList()
        listOf<(Int) -> Boolean>({ false }, { it % 5 == 0 }, { true }, { it > 30 }).forEach { isFullSpan ->
            val rows = baGuideCatalogEntryRows(entries, columnsPerRow = 2, isFullSpan = isFullSpan)
            assertEquals(entries, rows.flatMap { it.entries })
            rows.forEach { row ->
                assertEquals(entries.subList(row.startIndex, row.startIndex + row.entries.size), row.entries)
            }
        }
    }

    @Test
    fun `visible items resolve to the entries those rows actually hold`() {
        val rows = baGuideCatalogEntryRows(entries = (0 until 10).toList(), columnsPerRow = 2)

        // Rows 1..2 are entries 2..5. Preloading has to know that, or in two columns it warms the icons of
        // entries half a screen away from the ones on screen.
        val range =
            baGuideCatalogVisibleEntryRange(
                rows = rows,
                visibleItemRange =
                    BaGuideVisibleItemRange(firstItemIndex = 1, lastItemIndex = 2, visibleItemCount = 2),
                entryStartIndex = 0,
            )

        assertEquals(2, range.firstItemIndex)
        assertEquals(5, range.lastItemIndex)
        // Entries on screen, not rows: the preload window scales with what is visible.
        assertEquals(4, range.visibleItemCount)
    }

    @Test
    fun `a leading status item still offsets the mapping`() {
        val rows = baGuideCatalogEntryRows(entries = (0 until 6).toList(), columnsPerRow = 2)

        // Item 0 is an error or header card; the first entry row is item 1.
        val range =
            baGuideCatalogVisibleEntryRange(
                rows = rows,
                visibleItemRange =
                    BaGuideVisibleItemRange(firstItemIndex = 0, lastItemIndex = 1, visibleItemCount = 2),
                entryStartIndex = 1,
            )

        assertEquals(0, range.firstItemIndex)
        assertEquals(1, range.lastItemIndex)
    }

    @Test
    fun `one column resolves exactly as it did before rows existed`() {
        val rows = baGuideCatalogEntryRows(entries = (0 until 8).toList(), columnsPerRow = 1)
        val itemRange = BaGuideVisibleItemRange(firstItemIndex = 3, lastItemIndex = 6, visibleItemCount = 4)

        // The identity the preload builders used to compute for themselves: item index minus the start
        // offset is the entry index. Nothing about the phone's preloading changes.
        val range = baGuideCatalogVisibleEntryRange(rows = rows, visibleItemRange = itemRange, entryStartIndex = 2)

        assertEquals(1, range.firstItemIndex)
        assertEquals(4, range.lastItemIndex)
        assertEquals(4, range.visibleItemCount)
    }

    @Test
    fun `nothing visible resolves to nothing`() {
        val rows = baGuideCatalogEntryRows(entries = (0 until 4).toList(), columnsPerRow = 2)

        assertTrue(
            baGuideCatalogVisibleEntryRange(
                rows = rows,
                visibleItemRange =
                    BaGuideVisibleItemRange(firstItemIndex = -1, lastItemIndex = -1, visibleItemCount = 0),
                entryStartIndex = 0,
            ).isEmpty,
        )
        assertTrue(
            baGuideCatalogVisibleEntryRange(
                rows = emptyList<BaGuideCatalogEntryRow<Int>>(),
                visibleItemRange =
                    BaGuideVisibleItemRange(firstItemIndex = 0, lastItemIndex = 1, visibleItemCount = 2),
                entryStartIndex = 0,
            ).isEmpty,
        )
        // Scrolled past everything the rows cover — the status items below the list.
        assertTrue(
            baGuideCatalogVisibleEntryRange(
                rows = rows,
                visibleItemRange =
                    BaGuideVisibleItemRange(firstItemIndex = 9, lastItemIndex = 9, visibleItemCount = 1),
                entryStartIndex = 0,
            ).isEmpty,
        )
    }
}
