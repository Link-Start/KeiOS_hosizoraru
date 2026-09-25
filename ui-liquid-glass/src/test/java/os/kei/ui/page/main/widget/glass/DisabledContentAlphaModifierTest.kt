package os.kei.ui.page.main.widget.glass

import androidx.compose.ui.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DisabledContentAlphaModifierTest {
    @Test
    fun onlyTheDisabledStatePaysForOneSharedAlphaLayer() {
        assertEquals(
            "enabled controls omit the identity alpha layer",
            0,
            disabledContentAlphaModifier(enabled = true).elementCount(),
        )
        assertEquals(
            "disabled controls keep a single alpha layer",
            1,
            disabledContentAlphaModifier(enabled = false).elementCount(),
        )
        assertSame(
            "the disabled modifier is shared across call sites",
            disabledContentAlphaModifier(enabled = false),
            disabledContentAlphaModifier(enabled = false),
        )
    }
}

private fun Modifier.elementCount(): Int = foldIn(0) { count, _ -> count + 1 }
