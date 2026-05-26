package os.kei.ui.page.main.ba

internal interface BaOfficeClock {
    fun nowMs(): Long
}

internal object BaSystemOfficeClock : BaOfficeClock {
    override fun nowMs(): Long = System.currentTimeMillis()
}
