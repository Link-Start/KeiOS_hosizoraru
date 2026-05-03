package os.kei.ui.page.main.host.pager

internal fun shouldReduceBottomBarEffectsDuringMotion(
    scrollEffectReductionEnabled: Boolean,
    activePageListScrollInProgress: Boolean = false
): Boolean {
    return false
}
