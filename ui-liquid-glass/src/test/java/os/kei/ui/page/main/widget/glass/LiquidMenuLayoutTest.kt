package os.kei.ui.page.main.widget.glass

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Apple defines fixed top-row sizes: Small is four symbol-only items, Medium is three
 * symbol-plus-label items, Large is a plain list. What this adds to was an unbounded `Row` with
 * `weight(1f)` that showed labels regardless — so it could not express Small at all, and had no notion
 * of a count being wrong.
 *
 * Two properties are worth pinning. **No action is ever dropped** — overflow from a bounded layout
 * becomes a list row rather than disappearing. And **auto mode never relocates anything**: a caller who
 * passes quick actions has made a design decision, so recognising Apple's counts must not become a
 * licence to rearrange every other count. The all-counts round-trip test covers the first; the auto test
 * covers the second.
 */
class LiquidMenuLayoutTest {
    private fun action(id: String) =
        LiquidGlassActionMenuQuickAction(
            id = id,
            icon = ImageVector.Builder(id, 1.dp, 1.dp, 1f, 1f).build(),
            label = id,
            onClick = {},
        )

    private fun actions(count: Int) = List(count) { index -> action("a$index") }

    private fun plan(
        requested: LiquidMenuLayout?,
        count: Int,
    ) = resolveLiquidMenuLayoutPlan(requested, actions(count))

    @Test
    fun autoResolvesByCountToApplesLayouts() {
        assertEquals(LiquidMenuLayout.Small, plan(null, 4).layout)
        assertEquals(LiquidMenuLayout.Medium, plan(null, 3).layout)
    }

    @Test
    fun autoNeverRelocatesAQuickActionForACountAppleDoesNotDefine() {
        // The important half of the rule. A caller passing quick actions has made a design decision, so
        // auto mode keeps the flexible labelled row rather than quietly moving anything into the list.
        listOf(1, 2, 5, 8).forEach { count ->
            val resolved = plan(null, count)
            assertEquals(null, resolved.layout, "count=$count")
            assertEquals(count, resolved.topRow.size, "count=$count")
            assertTrue(resolved.listed.isEmpty(), "count=$count")
            assertTrue(resolved.topRowShowsLabels, "count=$count")
        }
    }

    @Test
    fun noQuickActionsIsLargeWithNothingListed() {
        val resolved = resolveLiquidMenuLayoutPlan(null, emptyList())

        assertEquals(LiquidMenuLayout.Large, resolved.layout)
        assertTrue(resolved.topRow.isEmpty())
        assertTrue(resolved.listed.isEmpty())
    }

    @Test
    fun anExplicitLayoutTakesWhatItSeatsAndListsTheRest() {
        val medium = plan(LiquidMenuLayout.Medium, 5)
        assertEquals(LiquidMenuLayout.Medium, medium.layout)
        assertEquals(listOf("a0", "a1", "a2"), medium.topRow.map { it.id })
        assertEquals(listOf("a3", "a4"), medium.listed.map { it.id })

        val small = plan(LiquidMenuLayout.Small, 6)
        assertEquals(LiquidMenuLayout.Small, small.layout)
        assertEquals(4, small.topRow.size)
        assertEquals(2, small.listed.size)
    }

    @Test
    fun aBoundedLayoutItCannotFillIsDeclinedRatherThanHalfDrawn() {
        // Two items in a four-slot grid reads as broken, so the flexible row is used instead — and the
        // actions stay in the top row where the caller put them.
        listOf(LiquidMenuLayout.Small to 3, LiquidMenuLayout.Medium to 2).forEach { (requested, count) ->
            val resolved = plan(requested, count)
            assertEquals(null, resolved.layout, "$requested/$count")
            assertEquals(count, resolved.topRow.size, "$requested/$count")
            assertTrue(resolved.listed.isEmpty(), "$requested/$count")
        }
    }

    @Test
    fun askingForLargeKeepsEveryQuickActionInTheList() {
        val resolved = plan(LiquidMenuLayout.Large, 4)

        assertEquals(LiquidMenuLayout.Large, resolved.layout)
        assertTrue(resolved.topRow.isEmpty())
        assertEquals(4, resolved.listed.size)
    }

    @Test
    fun everyPlanAccountsForEveryActionExactlyOnce() {
        // The invariant behind all of the above: nothing is dropped and nothing is duplicated.
        listOf<LiquidMenuLayout?>(null, LiquidMenuLayout.Small, LiquidMenuLayout.Medium, LiquidMenuLayout.Large)
            .forEach { requested ->
                (0..8).forEach { count ->
                    val resolved = plan(requested, count)
                    val seen = resolved.topRow.map { it.id } + resolved.listed.map { it.id }
                    assertEquals(
                        actions(count).map { it.id },
                        seen,
                        "requested=$requested count=$count",
                    )
                }
            }
    }

    @Test
    fun onlySmallDropsItsLabels() {
        assertTrue(!plan(null, 4).topRowShowsLabels, "Small is symbol-only by definition")
        assertTrue(plan(null, 3).topRowShowsLabels)
    }

    @Test
    fun aListedQuickActionKeepsItsSymbolLabelAndBehaviour() {
        var clicked = false
        val source =
            LiquidGlassActionMenuQuickAction(
                id = "export",
                icon = ImageVector.Builder("export", 1.dp, 1.dp, 1f, 1f).build(),
                label = "Export",
                enabled = false,
                variant = GlassVariant.SheetDangerAction,
                onClick = { clicked = true },
            )

        val row = source.asMenuRow()

        assertEquals("export", row.id)
        assertEquals("Export", row.text)
        // Apple asks for a uniform treatment inside a group, so the symbol comes along as the leading icon.
        assertEquals(source.icon, row.leadingIcon)
        assertEquals(false, row.enabled)
        assertEquals(GlassVariant.SheetDangerAction, row.variant)
        row.onClick()
        assertTrue(clicked)
    }
}
