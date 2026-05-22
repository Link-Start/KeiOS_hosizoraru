package os.kei.ui.page.main.os.shortcut

internal interface OsActivityShortcutCardClock {
    fun nowMs(): Long
}

internal object OsSystemActivityShortcutCardClock : OsActivityShortcutCardClock {
    override fun nowMs(): Long = System.currentTimeMillis()
}
