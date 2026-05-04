package os.kei.ui.page.main.student.catalog.component.bgm

internal data class BaGuideBgmTrack(
    val id: String,
    val title: String,
    val subtitle: String,
    val durationLabel: String,
    val searchAlias: String
) {
    companion object {
        val Empty = BaGuideBgmTrack(
            id = "",
            title = "",
            subtitle = "",
            durationLabel = "",
            searchAlias = ""
        )
    }
}
