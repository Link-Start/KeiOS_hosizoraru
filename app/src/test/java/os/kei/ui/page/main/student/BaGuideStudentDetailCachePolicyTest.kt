package os.kei.ui.page.main.student

import org.junit.Test
import os.kei.ui.page.main.student.catalog.BaGuideCatalogTab
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BaGuideStudentDetailCachePolicyTest {
    @Test
    fun `policy applies only to implemented students`() {
        val nowMs = 200L * DAY_MS

        assertNull(
            resolveBaGuideStudentDetailFreshnessTier(
                tab = BaGuideCatalogTab.NpcSatellite,
                catalogCreatedAtSec = (nowMs - 2L * DAY_MS) / 1000L,
                releaseDateSec = 0L,
                firstSeenAtMs = 0L,
                unchangedValidationCount = 0,
                nowMs = nowMs,
            ),
        )
    }

    @Test
    fun `recent catalog creation keeps student in hot update tier`() {
        val nowMs = 200L * DAY_MS

        assertEquals(
            BaGuideStudentDetailFreshnessTier.HotUpdate,
            resolveBaGuideStudentDetailFreshnessTier(
                tab = BaGuideCatalogTab.Student,
                catalogCreatedAtSec = (nowMs - 6L * DAY_MS) / 1000L,
                releaseDateSec = (nowMs - 120L * DAY_MS) / 1000L,
                firstSeenAtMs = 0L,
                unchangedValidationCount = 0,
                nowMs = nowMs,
            ),
        )
    }

    @Test
    fun `student tier follows conservative freshest known age`() {
        val nowMs = 240L * DAY_MS

        assertEquals(
            BaGuideStudentDetailFreshnessTier.Completion,
            resolveBaGuideStudentDetailFreshnessTier(
                tab = BaGuideCatalogTab.Student,
                catalogCreatedAtSec = (nowMs - 20L * DAY_MS) / 1000L,
                releaseDateSec = (nowMs - 140L * DAY_MS) / 1000L,
                firstSeenAtMs = nowMs - 100L * DAY_MS,
                unchangedValidationCount = 0,
                nowMs = nowMs,
            ),
        )
        assertEquals(
            BaGuideStudentDetailFreshnessTier.Stable,
            resolveBaGuideStudentDetailFreshnessTier(
                tab = BaGuideCatalogTab.Student,
                catalogCreatedAtSec = (nowMs - 45L * DAY_MS) / 1000L,
                releaseDateSec = 0L,
                firstSeenAtMs = nowMs - 120L * DAY_MS,
                unchangedValidationCount = 0,
                nowMs = nowMs,
            ),
        )
        assertEquals(
            BaGuideStudentDetailFreshnessTier.LongTerm,
            resolveBaGuideStudentDetailFreshnessTier(
                tab = BaGuideCatalogTab.Student,
                catalogCreatedAtSec = (nowMs - 120L * DAY_MS) / 1000L,
                releaseDateSec = 0L,
                firstSeenAtMs = 0L,
                unchangedValidationCount = 0,
                nowMs = nowMs,
            ),
        )
    }

    @Test
    fun `archived tier needs old age and repeated unchanged validations`() {
        val nowMs = 240L * DAY_MS

        assertEquals(
            BaGuideStudentDetailFreshnessTier.LongTerm,
            resolveBaGuideStudentDetailFreshnessTier(
                tab = BaGuideCatalogTab.Student,
                catalogCreatedAtSec = (nowMs - 200L * DAY_MS) / 1000L,
                releaseDateSec = 0L,
                firstSeenAtMs = 0L,
                unchangedValidationCount = 2,
                nowMs = nowMs,
            ),
        )
        assertEquals(
            BaGuideStudentDetailFreshnessTier.Archived,
            resolveBaGuideStudentDetailFreshnessTier(
                tab = BaGuideCatalogTab.Student,
                catalogCreatedAtSec = (nowMs - 200L * DAY_MS) / 1000L,
                releaseDateSec = 0L,
                firstSeenAtMs = 0L,
                unchangedValidationCount = 3,
                nowMs = nowMs,
            ),
        )
    }

    @Test
    fun `validation decision respects tier interval retry and manual refresh`() {
        val nowMs = 20L * DAY_MS
        val meta =
            BaGuideStudentDetailCacheMeta(
                sourceUrl = SOURCE_URL,
                contentId = 1L,
                tab = BaGuideCatalogTab.Student,
                catalogCreatedAtSec = (nowMs - 2L * DAY_MS) / 1000L,
                releaseDateSec = 0L,
                firstSeenAtMs = 0L,
                cachedAtMs = nowMs - DAY_MS,
                lastValidatedAtMs = nowMs - 30L * 60L * 1000L,
                lastChangedAtMs = nowMs - DAY_MS,
                nextAutoRefreshAtMs = 0L,
                freshnessTier = BaGuideStudentDetailFreshnessTier.HotUpdate,
                contentHash = "hash",
                unchangedValidationCount = 0,
                failureCount = 0,
                lastFailureAtMs = 0L,
                nextRetryAtMs = nowMs + 10L * 60L * 1000L,
            )

        // A missing parser version forces one validation, but still respects retry backoff.
        assertTrue(decideBaGuideStudentDetailCacheRefresh(
            meta = meta.copy(nextRetryAtMs = 0L), manualRefresh = false,
            nowMs = nowMs, galleryParserVersion = 0,
        ).shouldValidate)
        assertFalse(decideBaGuideStudentDetailCacheRefresh(
            meta = meta, manualRefresh = false,
            nowMs = nowMs, galleryParserVersion = 0,
        ).shouldValidate)
        assertFalse(
            decideBaGuideStudentDetailCacheRefresh(
                meta = meta,
                manualRefresh = false,
                nowMs = nowMs,
            ).shouldValidate,
        )
        assertTrue(
            decideBaGuideStudentDetailCacheRefresh(
                meta = meta,
                manualRefresh = true,
                nowMs = nowMs,
            ).shouldValidate,
        )
        assertTrue(
            decideBaGuideStudentDetailCacheRefresh(
                meta = meta.copy(
                    lastValidatedAtMs = nowMs - 90L * 60L * 1000L,
                    nextRetryAtMs = 0L,
                ),
                manualRefresh = false,
                nowMs = nowMs,
            ).shouldValidate,
        )
    }

    private companion object {
        private const val SOURCE_URL = "https://www.gamekee.com/ba/tj/1.html"
        private const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
