package os.kei.ui.page.main.widget.chrome

import kotlin.test.assertEquals
import org.junit.Test

class AppPageAlternatingLanesTest {
    @Test
    fun `one lane is the whole list, untouched`() {
        val items = ('a'..'e').toList()

        assertEquals(listOf(items), appPageAlternatingLanes(items, columnCount = 1))
    }

    @Test
    fun `lanes alternate, so each lane stays in the list's order`() {
        val lanes = appPageAlternatingLanes(('a'..'g').toList(), columnCount = 2)

        // Halving would be the obvious split and is the wrong one: lanes scroll independently, so a first
        // half and a second half would put 'e' at the top of the right lane with no way to read across.
        assertEquals(listOf('a', 'c', 'e', 'g'), lanes[0])
        assertEquals(listOf('b', 'd', 'f'), lanes[1])
        assertEquals(lanes[0].sorted(), lanes[0])
        assertEquals(lanes[1].sorted(), lanes[1])
    }

    @Test
    fun `level with each other, reading across a row is the list from start to end`() {
        val items = (0 until 8).toList()
        val lanes = appPageAlternatingLanes(items, columnCount = 2)

        val readAcross = lanes[0].zip(lanes[1]).flatMap { (left, right) -> listOf(left, right) }
        assertEquals(items, readAcross)
    }

    @Test
    fun `the pair overload is the two-lane split`() {
        val items = ('a'..'f').toList()
        val (left, right) = appPageAlternatingLanes(items)
        val lanes = appPageAlternatingLanes(items, columnCount = 2)

        // Settings, About and MCP take the pair; the catalogue and History take the list. One rule.
        assertEquals(lanes[0], left)
        assertEquals(lanes[1], right)
    }

    @Test
    fun `every item lands in exactly one lane, at any size`() {
        listOf(0, 1, 2, 7, 36, 275).forEach { size ->
            val items = (0 until size).toList()
            listOf(1, 2, 3).forEach { columns ->
                val lanes = appPageAlternatingLanes(items, columnCount = columns)
                assertEquals(items, lanes.flatten().sorted(), "size=$size columns=$columns")
                assertEquals(size, lanes.sumOf { lane -> lane.size }, "size=$size columns=$columns")
            }
        }
    }

    @Test
    fun `wrapping in withIndex keeps a lane's items addressable in the flat list`() {
        // What History does: a lane no longer knows where its records sit overall, and the staggered
        // entrance animation is indexed against the whole list rather than against a column.
        val lanes = appPageAlternatingLanes(('a'..'f').toList().withIndex().toList(), columnCount = 2)

        assertEquals(listOf(0, 2, 4), lanes[0].map { it.index })
        assertEquals(listOf(1, 3, 5), lanes[1].map { it.index })
        assertEquals(listOf('a', 'c', 'e'), lanes[0].map { it.value })
    }
}
