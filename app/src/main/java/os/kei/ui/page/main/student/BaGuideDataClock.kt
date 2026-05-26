package os.kei.ui.page.main.student

internal fun interface BaGuideDataClock {
    fun nowMs(): Long
}

internal object BaGuideSystemDataClock : BaGuideDataClock {
    override fun nowMs(): Long = System.currentTimeMillis()
}
