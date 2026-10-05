package os.kei.ui.page.main.student

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import os.kei.ui.page.main.student.catalog.BaGuideCatalogTab

private const val HOUR_MS = 60L * 60L * 1000L
private const val DAY_MS = 24L * HOUR_MS
private const val HOT_UPDATE_MAX_AGE_MS = 7L * DAY_MS
private const val COMPLETION_MAX_AGE_MS = 30L * DAY_MS
private const val STABLE_MAX_AGE_MS = 90L * DAY_MS
private const val LONG_TERM_MAX_AGE_MS = 180L * DAY_MS
private const val ARCHIVED_UNCHANGED_VALIDATION_THRESHOLD = 3

@Serializable
internal enum class BaGuideStudentDetailFreshnessTier(
    val storageId: String,
    val validationIntervalMs: Long,
) {
    HotUpdate("hot_update", HOUR_MS),
    Completion("completion", 12L * HOUR_MS),
    Stable("stable", 3L * DAY_MS),
    LongTerm("long_term", 14L * DAY_MS),
    Archived("archived", 30L * DAY_MS),
    Unknown("unknown", DAY_MS);

    companion object {
        fun fromStorageId(raw: String?): BaGuideStudentDetailFreshnessTier =
            entries.firstOrNull { it.storageId == raw } ?: Unknown
    }
}

@Immutable
@Serializable
internal data class BaGuideStudentDetailCacheMeta(
    val schema: Int = BA_GUIDE_STUDENT_DETAIL_META_SCHEMA_VERSION,
    val sourceUrl: String,
    val contentId: Long,
    val tab: BaGuideCatalogTab,
    val catalogCreatedAtSec: Long,
    val releaseDateSec: Long,
    val firstSeenAtMs: Long,
    val cachedAtMs: Long,
    val lastValidatedAtMs: Long,
    val lastChangedAtMs: Long,
    val nextAutoRefreshAtMs: Long,
    val freshnessTier: BaGuideStudentDetailFreshnessTier,
    val contentHash: String,
    val unchangedValidationCount: Int,
    val failureCount: Int,
    val lastFailureAtMs: Long,
    val nextRetryAtMs: Long,
)

internal data class BaGuideStudentDetailCacheRefreshDecision(
    val tier: BaGuideStudentDetailFreshnessTier,
    val shouldValidate: Boolean,
    val nextAutoRefreshAtMs: Long,
)

internal const val BA_GUIDE_STUDENT_DETAIL_META_SCHEMA_VERSION = 1

internal fun resolveBaGuideStudentDetailFreshnessTier(
    tab: BaGuideCatalogTab,
    catalogCreatedAtSec: Long,
    releaseDateSec: Long,
    firstSeenAtMs: Long,
    unchangedValidationCount: Int,
    nowMs: Long = System.currentTimeMillis(),
): BaGuideStudentDetailFreshnessTier? {
    if (tab != BaGuideCatalogTab.Student) return null
    val freshestKnownAtMs =
        listOf(
            catalogCreatedAtSec.secondsToMillisOrZero(),
            releaseDateSec.secondsToMillisOrZero(),
            firstSeenAtMs.coerceAtLeast(0L),
        ).filter { it > 0L }
            .maxOrNull()
            ?: return BaGuideStudentDetailFreshnessTier.Unknown
    val ageMs = (nowMs - freshestKnownAtMs).coerceAtLeast(0L)
    return when {
        ageMs <= HOT_UPDATE_MAX_AGE_MS -> BaGuideStudentDetailFreshnessTier.HotUpdate
        ageMs <= COMPLETION_MAX_AGE_MS -> BaGuideStudentDetailFreshnessTier.Completion
        ageMs <= STABLE_MAX_AGE_MS -> BaGuideStudentDetailFreshnessTier.Stable
        ageMs <= LONG_TERM_MAX_AGE_MS -> BaGuideStudentDetailFreshnessTier.LongTerm
        unchangedValidationCount >= ARCHIVED_UNCHANGED_VALIDATION_THRESHOLD -> BaGuideStudentDetailFreshnessTier.Archived
        else -> BaGuideStudentDetailFreshnessTier.LongTerm
    }
}

internal fun decideBaGuideStudentDetailCacheRefresh(
    meta: BaGuideStudentDetailCacheMeta,
    manualRefresh: Boolean,
    nowMs: Long = System.currentTimeMillis(),
    galleryParserVersion: Int = BA_GUIDE_GALLERY_PARSER_VERSION,
): BaGuideStudentDetailCacheRefreshDecision {
    val tier =
        resolveBaGuideStudentDetailFreshnessTier(
            tab = meta.tab,
            catalogCreatedAtSec = meta.catalogCreatedAtSec,
            releaseDateSec = meta.releaseDateSec,
            firstSeenAtMs = meta.firstSeenAtMs,
            unchangedValidationCount = meta.unchangedValidationCount,
            nowMs = nowMs,
        ) ?: meta.freshnessTier
    val nextAutoRefreshAtMs =
        if (meta.lastValidatedAtMs > 0L) {
            meta.lastValidatedAtMs + tier.validationIntervalMs
        } else {
            0L
        }
    val retryReady = meta.nextRetryAtMs <= 0L || nowMs >= meta.nextRetryAtMs
    val autoDue =
        galleryParserVersion < BA_GUIDE_GALLERY_PARSER_VERSION ||
        meta.lastValidatedAtMs <= 0L ||
            nextAutoRefreshAtMs <= 0L ||
            nowMs >= nextAutoRefreshAtMs
    return BaGuideStudentDetailCacheRefreshDecision(
        tier = tier,
        shouldValidate = manualRefresh || (retryReady && autoDue),
        nextAutoRefreshAtMs = nextAutoRefreshAtMs,
    )
}

private fun Long.secondsToMillisOrZero(): Long = if (this > 0L) this * 1000L else 0L
