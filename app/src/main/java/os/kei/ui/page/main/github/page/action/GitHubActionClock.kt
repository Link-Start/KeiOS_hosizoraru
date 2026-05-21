package os.kei.ui.page.main.github.page.action

internal fun interface GitHubActionClock {
    fun nowMs(): Long
}

internal object GitHubSystemActionClock : GitHubActionClock {
    override fun nowMs(): Long = System.currentTimeMillis()
}
