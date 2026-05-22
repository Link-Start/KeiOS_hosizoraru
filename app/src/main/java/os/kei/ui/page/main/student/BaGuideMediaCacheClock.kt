package os.kei.ui.page.main.student

internal interface BaGuideMediaCacheClock {
    fun nowMs(): Long
}

internal object BaGuideSystemMediaCacheClock : BaGuideMediaCacheClock {
    override fun nowMs(): Long = System.currentTimeMillis()
}
