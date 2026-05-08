package os.kei.ui.page.main.host.pager

import androidx.compose.runtime.Stable

@Stable
internal interface MainPagerStateContract {
    val pageCount: Int
    val currentPage: Int
    val targetPage: Int
    val settledPage: Int
    val currentPageOffsetFraction: Float
    val pagePosition: Float
    val isScrollInProgress: Boolean
    val selectedPage: Int
        get() = if (isProgrammaticNavigationInProgress) targetPage else currentPage
    val isProgrammaticNavigationInProgress: Boolean
        get() = false

    fun scrollToPage(page: Int)

    suspend fun animateToPage(
        target: Int,
        animationsEnabled: Boolean,
        durationMillis: Int
    )
}
