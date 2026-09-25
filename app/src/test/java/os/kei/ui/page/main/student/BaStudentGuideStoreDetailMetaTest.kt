package os.kei.ui.page.main.student

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import os.kei.ui.page.main.student.catalog.BaGuideCatalogTab
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class BaStudentGuideStoreDetailMetaTest {
    private lateinit var store: BaGuideStudentDetailFileCacheStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val root = File(context.filesDir, "student-detail-cache-test-${System.nanoTime()}").apply {
            deleteRecursively()
            mkdirs()
        }
        store = BaGuideStudentDetailFileCacheStore(root)
    }

    @Test
    fun `student detail cache meta builds from existing guide info`() {
        val nowMs = 400L * DAY_MS
        val sourceUrl = uniqueSourceUrl(12)
        val info =
            guideInfo(
                sourceUrl = sourceUrl,
                syncedAtMs = nowMs - DAY_MS,
                title = "缓存学生",
            )

        val meta =
            buildBaGuideStudentDetailCacheMetaFromInfo(
                info = info,
                contentId = 12L,
                tab = BaGuideCatalogTab.Student,
                catalogCreatedAtSec = (nowMs - 40L * DAY_MS) / 1000L,
                releaseDateSec = 0L,
                previous = null,
                nowMs = nowMs,
            )

        assertNotNull(meta)
        assertEquals(sourceUrl, meta.sourceUrl)
        assertEquals(12L, meta.contentId)
        assertEquals(BaGuideCatalogTab.Student, meta.tab)
        assertEquals(BaGuideStudentDetailFreshnessTier.Stable, meta.freshnessTier)
        assertEquals(info.syncedAtMs, meta.cachedAtMs)
        assertEquals(nowMs, meta.lastValidatedAtMs)
        assertEquals(nowMs + BaGuideStudentDetailFreshnessTier.Stable.validationIntervalMs, meta.nextAutoRefreshAtMs)
        store.saveMeta(meta)
        assertEquals(meta, store.loadMeta(sourceUrl))
    }

    @Test
    fun `npc satellite info does not build student detail meta`() {
        val nowMs = 400L * DAY_MS
        val sourceUrl = uniqueSourceUrl(13)
        val info = guideInfo(
            sourceUrl = sourceUrl,
            syncedAtMs = nowMs - DAY_MS,
            title = "卫星角色",
        )

        val meta =
            buildBaGuideStudentDetailCacheMetaFromInfo(
                info = info,
                contentId = 13L,
                tab = BaGuideCatalogTab.NpcSatellite,
                catalogCreatedAtSec = (nowMs - 2L * DAY_MS) / 1000L,
                releaseDateSec = 0L,
                previous = null,
                nowMs = nowMs,
            )

        assertNull(meta)
        assertNull(store.loadMeta(sourceUrl))
    }

    @Test
    fun `file store reports tier counts without decoding detail payload`() {
        val hot = detailMeta(uniqueSourceUrl(21), 21L, BaGuideStudentDetailFreshnessTier.HotUpdate)
        val longTerm = detailMeta(uniqueSourceUrl(22), 22L, BaGuideStudentDetailFreshnessTier.LongTerm)
        val archived = detailMeta(uniqueSourceUrl(23), 23L, BaGuideStudentDetailFreshnessTier.Archived)

        listOf(hot, longTerm, archived).forEach(store::saveMeta)

        val stats = store.stats()
        assertEquals(3, stats.totalCount)
        assertEquals(3, stats.studentCount)
        assertEquals(1, stats.hotUpdateCount)
        assertEquals(1, stats.longTermCount)
        assertEquals(1, stats.archivedCount)
        assertEquals(2_000L, stats.latestValidatedAtMs)
    }

    @Test
    fun `file store loads detail meta by content id`() {
        val sourceUrl = uniqueSourceUrl(24)
        val meta =
            detailMeta(
                sourceUrl = sourceUrl,
                contentId = 24L,
                tier = BaGuideStudentDetailFreshnessTier.Stable,
            )

        store.saveMeta(meta)

        assertEquals(meta, store.loadMetaByContentId(24L))
        assertNull(store.loadMetaByContentId(404L))
    }

    @Test
    fun `file store removes orphan student metas by active catalog sources`() {
        val keepSourceUrl = uniqueSourceUrl(26)
        val oldSourceUrl = uniqueSourceUrl(27)
        val keepMeta = detailMeta(keepSourceUrl, 26L, BaGuideStudentDetailFreshnessTier.Stable)
        val orphanMeta = detailMeta(oldSourceUrl, 27L, BaGuideStudentDetailFreshnessTier.Stable)
        val npcMeta =
            detailMeta(uniqueSourceUrl(28), 28L, BaGuideStudentDetailFreshnessTier.Stable)
                .copy(tab = BaGuideCatalogTab.NpcSatellite)
        store.saveMeta(keepMeta)
        store.saveMeta(orphanMeta)
        store.saveMeta(npcMeta)

        val removed =
            store.removeOrphanStudentMetas(
                mapOf(
                    26L to setOf(keepSourceUrl),
                    27L to setOf("https://www.gamekee.com/ba/tj/27-new.html"),
                ),
            )

        assertEquals(listOf(orphanMeta), removed)
        assertEquals(keepMeta, store.loadMeta(keepSourceUrl))
        assertNull(store.loadMeta(oldSourceUrl))
        assertEquals(npcMeta, store.loadMeta(npcMeta.sourceUrl))
    }

    @Test
    fun `catalog alignment preserves validation times while updating catalog signals`() {
        val nowMs = 400L * DAY_MS
        val sourceUrl = uniqueSourceUrl(25)
        val previous =
            detailMeta(
                sourceUrl = sourceUrl,
                contentId = 25L,
                tier = BaGuideStudentDetailFreshnessTier.Unknown,
            ).copy(
                catalogCreatedAtSec = 0L,
                releaseDateSec = 0L,
                firstSeenAtMs = nowMs - 45L * DAY_MS,
                lastValidatedAtMs = nowMs - 2L * DAY_MS,
                cachedAtMs = nowMs - 3L * DAY_MS,
            )

        val aligned =
            alignBaGuideStudentDetailCacheMetaWithCatalog(
                meta = previous,
                sourceUrl = sourceUrl,
                catalogCreatedAtSec = (nowMs - 40L * DAY_MS) / 1000L,
                releaseDateSec = (nowMs - 120L * DAY_MS) / 1000L,
                nowMs = nowMs,
            )

        assertEquals(previous.firstSeenAtMs, aligned.firstSeenAtMs)
        assertEquals(previous.lastValidatedAtMs, aligned.lastValidatedAtMs)
        assertEquals(previous.cachedAtMs, aligned.cachedAtMs)
        assertEquals((nowMs - 40L * DAY_MS) / 1000L, aligned.catalogCreatedAtSec)
        assertEquals((nowMs - 120L * DAY_MS) / 1000L, aligned.releaseDateSec)
        assertTrue(aligned.nextAutoRefreshAtMs > previous.lastValidatedAtMs)
    }

    @Test
    fun `guide payload migration moves oversized mmkv payloads to file cache and keeps reads working`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val payloadStore =
            BaStudentGuidePayloadFileCacheStore(
                File(context.filesDir, "student-payload-cache-test-${System.nanoTime()}").apply {
                    deleteRecursively()
                    mkdirs()
                },
            )
        val kv = InMemoryPayloadKeyValueStore()
        val sourceUrl = uniqueSourceUrl(31)
        val info =
            guideInfo(
                sourceUrl = sourceUrl,
                syncedAtMs = 500L * DAY_MS,
                title = "迁移学生",
            ).copy(
                profileRows =
                    List(32) { index ->
                        BaGuideRow("资料$index", "长文本-${"x".repeat(128)}")
                    },
            )

        writeGuideV2Payload(
            store = kv,
            id = guideCacheId(sourceUrl),
            payload = encodeGuideV2Payload(info),
        )

        val before =
            BaStudentGuidePayloadStorageStats(
                mmkvEntryCount = 1,
                fileEntryCount = payloadStore.entryCount(),
                totalEntryCount = 1,
                mmkvPayloadBytes = mmkvGuidePayloadBytes(kv, setOf(sourceUrl)),
                filePayloadBytes = payloadStore.payloadBytes(),
                filePayloadEnabled = payloadStore.isEnabled(),
            )
        assertEquals(1, before.totalEntryCount)
        assertTrue(before.mmkvPayloadBytes > 0L)
        assertEquals(0L, before.filePayloadBytes)
        assertTrue(
            before.shouldMigrateToFiles(
                BaStudentGuidePayloadMigrationThresholds(
                    minMmkvPayloadBytes = 1L,
                    minMmkvEntryCount = 1,
                ),
            ),
        )

        val migration =
            migrateBaStudentGuidePayloadsToFileStore(
                keyValueStore = kv,
                sourceUrls = setOf(sourceUrl),
                payloadStore = payloadStore,
            )
        payloadStore.setEnabled(migration.migratedEntryCount > 0)

        assertEquals(1, migration.migratedEntryCount)
        assertEquals(0, migration.skippedEntryCount)
        assertTrue(migration.remainingMmkvSources.isEmpty())
        assertNull(readGuideV2Payload(kv, guideCacheId(sourceUrl)))
        val after =
            BaStudentGuidePayloadStorageStats(
                mmkvEntryCount = 0,
                fileEntryCount = payloadStore.entryCount(),
                totalEntryCount = payloadStore.entryCount(),
                mmkvPayloadBytes = mmkvGuidePayloadBytes(kv, setOf(sourceUrl)),
                filePayloadBytes = payloadStore.payloadBytes(),
                filePayloadEnabled = payloadStore.isEnabled(),
            )
        assertEquals(1, after.totalEntryCount)
        assertEquals(0L, after.mmkvPayloadBytes)
        assertTrue(after.filePayloadBytes > 0L)
        assertFalse(after.shouldMigrateToFiles())
        val filePayload = payloadStore.loadPayload(sourceUrl)
        assertNotNull(filePayload)
        val decoded = decodeGuideV2InfoFromPayload(sourceUrl) { suffix -> filePayload[suffix].orEmpty() }
        assertNotNull(decoded)
        assertTrue(isGuideInfoPayloadComplete(decoded))
        assertEquals(info.title, decoded.title)
        assertEquals(info.profileRows.size, decoded.profileRows.size)
    }

    private fun guideInfo(
        sourceUrl: String,
        syncedAtMs: Long,
        title: String,
    ): BaStudentGuideInfo =
        BaStudentGuideInfo(
            sourceUrl = sourceUrl,
            title = title,
            subtitle = "GameKee",
            description = "desc",
            imageUrl = "https://example.com/$title.png",
            summary = "summary",
            stats = listOf("实装日期" to "2025年1月1日"),
            profileRows = listOf(BaGuideRow("学校", "夏莱")),
            syncedAtMs = syncedAtMs,
        )

    private fun detailMeta(
        sourceUrl: String,
        contentId: Long,
        tier: BaGuideStudentDetailFreshnessTier,
    ): BaGuideStudentDetailCacheMeta =
        BaGuideStudentDetailCacheMeta(
            sourceUrl = sourceUrl,
            contentId = contentId,
            tab = BaGuideCatalogTab.Student,
            catalogCreatedAtSec = 100L,
            releaseDateSec = 0L,
            firstSeenAtMs = 0L,
            cachedAtMs = 1_000L,
            lastValidatedAtMs = 2_000L,
            lastChangedAtMs = 1_000L,
            nextAutoRefreshAtMs = 3_000L,
            freshnessTier = tier,
            contentHash = "hash-$contentId",
            unchangedValidationCount = 1,
            failureCount = 0,
            lastFailureAtMs = 0L,
            nextRetryAtMs = 0L,
        )

    private fun uniqueSourceUrl(contentId: Long): String =
        "https://www.gamekee.com/ba/tj/$contentId-${System.nanoTime()}.html"

    private class InMemoryPayloadKeyValueStore : BaStudentGuidePayloadKeyValueStore {
        private val values = linkedMapOf<String, String>()

        override fun encode(key: String, value: String) {
            values[key] = value
        }

        override fun decodeString(key: String): String = values[key].orEmpty()

        override fun containsKey(key: String): Boolean = key in values

        override fun allKeys(): List<String> = values.keys.toList()

        override fun removeValueForKey(key: String) {
            values.remove(key)
        }
    }

    private companion object {
        private const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
