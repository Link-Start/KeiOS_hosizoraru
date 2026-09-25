package os.kei.ui.page.main.model

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import org.junit.Assert.assertEquals
import org.junit.Test

class BottomPageIconScaleTest {
    /** Starting from a bare Modifier cannot tell a chained layer from a dropped chain. */
    @Test
    fun upstreamChainSurvivesBothBranches() {
        val upstream = Modifier.graphicsLayer { }

        assertEquals(1, upstream.elementCount())
        assertEquals(2, upstream.bottomPageIconScale(BottomPage.Ba).elementCount())
        assertEquals(1, upstream.bottomPageIconScale(BottomPage.Os).elementCount())
    }
}

private fun Modifier.elementCount(): Int = foldIn(0) { count, _ -> count + 1 }
