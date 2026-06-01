package os.kei.ui.page.main.host.pager

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf

@Immutable
internal data class MainPagerPageScrollBounds(
    val canScrollBackward: Boolean = false,
    val canScrollForward: Boolean = false,
)

@Stable
internal class MainPagerPageScrollBoundsState {
    private val boundsByPage = mutableStateMapOf<Int, MainPagerPageScrollBounds>()

    fun update(
        pageIndex: Int,
        canScrollBackward: Boolean,
        canScrollForward: Boolean,
    ) {
        val next =
            MainPagerPageScrollBounds(
                canScrollBackward = canScrollBackward,
                canScrollForward = canScrollForward,
            )
        if (boundsByPage[pageIndex] != next) {
            boundsByPage[pageIndex] = next
        }
    }

    fun boundsFor(pageIndex: Int): MainPagerPageScrollBounds =
        boundsByPage[pageIndex] ?: MainPagerPageScrollBounds()
}
