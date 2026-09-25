package os.kei.mcp.bridge

import org.junit.Test
import os.kei.ui.page.main.ba.support.BaCraftFunction
import os.kei.ui.page.main.ba.support.BaCraftGrade
import os.kei.ui.page.main.ba.support.BaCraftSlot
import os.kei.ui.page.main.ba.support.BaCraftState
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val MINUTE = 60L * 1000L
private const val HOUR = 60L * MINUTE
private const val START = 1_700_000_000_000L

/**
 * The Craft Chamber's `ba_snapshot` lines.
 *
 * Pinned as whole strings rather than by field, because the value of this text is that a client can
 * parse it: a renamed key or a moved separator is a silent break for every reader.
 */
class McpBaCraftSnapshotTextTest {
    private fun line(
        craft: BaCraftState,
        function: BaCraftFunction = BaCraftFunction.Generate,
        index: Int = 0,
        nowMs: Long = START,
    ): String = mcpBaCraftSlotLine(craft, function, index, nowMs)

    @Test
    fun `an idle slot reports zeroes rather than being omitted`() {
        // Every slot is always emitted, so a client can index by slot without tracking which exist.
        assertEquals(
            "craftSlot[generate1]=state:idle | startedAtMs:0 | endAtMs:0 | durationMs:0 |" +
                " customDuration:false | grades: | label:",
            line(BaCraftState()),
        )
    }

    @Test
    fun `a running slot carries its grades and its absolute end`() {
        val craft =
            BaCraftState(
                generate =
                    listOf(
                        BaCraftSlot(
                            startedAtMs = START,
                            grades = listOf(BaCraftGrade.High, BaCraftGrade.Low),
                            label = "tech notes",
                        ),
                    ),
            )

        assertEquals(
            "craftSlot[generate1]=state:running | startedAtMs:$START |" +
                " endAtMs:${START + 3L * HOUR + 30L * MINUTE} | durationMs:${3L * HOUR + 30L * MINUTE} |" +
                " customDuration:false | grades:high,low | label:tech notes",
            line(craft, nowMs = START + HOUR),
        )
    }

    @Test
    fun `an elapsed slot reads ready, matching the card`() {
        val craft =
            BaCraftState(generate = listOf(BaCraftSlot(startedAtMs = START, grades = listOf(BaCraftGrade.Low))))

        assertTrue(line(craft, nowMs = START + HOUR).startsWith("craftSlot[generate1]=state:ready |"))
    }

    @Test
    fun `a custom total is flagged, not silently substituted`() {
        // durationMs is the effective total either way, so without the flag a client could not tell a
        // hand-entered countdown from a summed one.
        val craft =
            BaCraftState(
                generate =
                    listOf(
                        BaCraftSlot(
                            startedAtMs = START,
                            grades = listOf(BaCraftGrade.Highest),
                            customDurationMs = 10L * MINUTE,
                        ),
                    ),
            )

        val text = line(craft)
        assertTrue("durationMs:${10L * MINUTE} " in text, text)
        assertTrue("customDuration:true" in text, text)
        // The grades stay visible: they say what is being made, even when they no longer set the clock.
        assertTrue("grades:highest" in text, text)
    }

    @Test
    fun `fusion slots are named apart from generate and numbered from one`() {
        val craft =
            BaCraftState(fusion = List(3) { BaCraftSlot(startedAtMs = START, grades = listOf(BaCraftGrade.Normal)) })

        assertTrue(line(craft, BaCraftFunction.Fusion, 0).startsWith("craftSlot[fusion1]="))
        assertTrue(line(craft, BaCraftFunction.Fusion, 2).startsWith("craftSlot[fusion3]="))
    }

    @Test
    fun `a slot past the persisted list still emits`() {
        // slotAt pads, so an older build's shorter list cannot make a line disappear.
        val craft = BaCraftState(generate = listOf(BaCraftSlot(startedAtMs = START, grades = listOf(BaCraftGrade.Low))))

        assertTrue(line(craft, index = 2).startsWith("craftSlot[generate3]=state:idle |"))
    }
}
