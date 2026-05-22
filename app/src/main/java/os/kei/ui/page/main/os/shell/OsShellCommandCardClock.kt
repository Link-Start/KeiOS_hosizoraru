package os.kei.ui.page.main.os.shell

internal interface OsShellCommandCardClock {
    fun nowMs(): Long
}

internal object OsShellCommandCardSystemClock : OsShellCommandCardClock {
    override fun nowMs(): Long = System.currentTimeMillis()
}
