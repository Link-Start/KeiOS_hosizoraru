package os.kei.ui.page.main.github.share

internal fun interface GitHubShareImportClock {
    fun nowMs(): Long
}

internal object GitHubSystemShareImportClock : GitHubShareImportClock {
    override fun nowMs(): Long = System.currentTimeMillis()
}
