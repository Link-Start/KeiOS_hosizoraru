package os.kei.ui.page.main.student.catalog.component.bgm

import androidx.compose.runtime.Immutable

@Immutable
internal data class BaGuideBgmChromePresentationState(
    val mode: BaGuideBgmBottomChromeMode,
    val searchFieldVisible: Boolean,
)

internal enum class BaGuideBgmBottomChromeMode {
    Expanded,
    Compact,
    SearchExpanded,
    SearchInput,
    ;

    val isSearchMode: Boolean
        get() = this == SearchExpanded || this == SearchInput
}

internal object BaGuideBgmChromePresentationDeriver {
    fun derive(
        searchVisible: Boolean,
        searchInputActive: Boolean,
        compact: Boolean,
    ): BaGuideBgmChromePresentationState {
        val mode =
            when {
                searchInputActive -> BaGuideBgmBottomChromeMode.SearchInput
                searchVisible -> BaGuideBgmBottomChromeMode.SearchExpanded
                compact -> BaGuideBgmBottomChromeMode.Compact
                else -> BaGuideBgmBottomChromeMode.Expanded
            }
        return BaGuideBgmChromePresentationState(
            mode = mode,
            searchFieldVisible = mode.isSearchMode,
        )
    }
}

internal fun resolveBaGuideBgmBottomChromeMode(
    searchVisible: Boolean,
    searchInputActive: Boolean,
    compact: Boolean,
): BaGuideBgmBottomChromeMode =
    BaGuideBgmChromePresentationDeriver
        .derive(
            searchVisible = searchVisible,
            searchInputActive = searchInputActive,
            compact = compact,
        ).mode
