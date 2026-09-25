package os.kei.ui.page.main.student.page.state

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import os.kei.ui.page.main.student.BaGuideDataClock
import os.kei.ui.page.main.student.BaGuideRow
import os.kei.ui.page.main.student.BaGuideStudentDetailCacheMeta
import os.kei.ui.page.main.student.BaGuideStudentDetailFileCacheStore
import os.kei.ui.page.main.student.BaGuideStudentDetailFreshnessTier
import os.kei.ui.page.main.student.BaStudentGuideCacheSnapshot
import os.kei.ui.page.main.student.BaStudentGuideInfo
import os.kei.ui.page.main.student.catalog.BaGuideCatalogBundle
import os.kei.ui.page.main.student.catalog.BaGuideCatalogEntry
import os.kei.ui.page.main.student.catalog.BaGuideCatalogTab
import os.kei.ui.page.main.student.catalog.testCatalogEntry
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class BaStudentGuideRepositoryTest {
    @Test
    fun `fresh implemented student detail cache returns without network and writes migrated meta`() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Application>()
            val nowMs = 100L * DAY_MS
            val sourceUrl = "https://www.gamekee.com/ba/tj/10001.html"
            val cached = guideInfo(sourceUrl = sourceUrl, title = "缓存学生", syncedAtMs = nowMs - DAY_MS)
            val metaStore = tempMetaStore(context)
            var fetchCalled = false
            val repository =
                repository(
                    nowMs = nowMs,
                    cacheSnapshot = BaStudentGuideCacheSnapshot(cached, hasCache = true, isComplete = true, cached.syncedAtMs),
                    catalog = catalogBundle(
                        catalogEntry(
                            sourceUrl = sourceUrl,
                            contentId = 10001L,
                            tab = BaGuideCatalogTab.Student,
                            createdAtSec = (nowMs - 40L * DAY_MS) / 1000L,
                        ),
                    ),
                    metaStore = metaStore,
                    fetcher = {
                        fetchCalled = true
                        guideInfo(sourceUrl = sourceUrl, title = "网络学生", syncedAtMs = nowMs)
                    },
                )

            val result =
                repository.load(context, sourceUrl)

            assertEquals(cached, result.info)
            assertNull(result.error)
            assertFalse(fetchCalled)
            assertNotNull(result.cacheMeta)
            assertEquals(BaGuideStudentDetailFreshnessTier.Stable, result.cacheMeta.freshnessTier)
            val meta = metaStore.loadMeta(sourceUrl)
            assertNotNull(meta)
            assertEquals(BaGuideStudentDetailFreshnessTier.Stable, meta.freshnessTier)
            assertEquals(cached.syncedAtMs, meta.cachedAtMs)
            assertEquals(nowMs, meta.lastValidatedAtMs)
        }

    @Test
    fun `manual refresh implemented student detail fetches network and updates meta`() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Application>()
            val nowMs = 100L * DAY_MS
            val sourceUrl = "https://www.gamekee.com/ba/tj/10002.html"
            val cached = guideInfo(sourceUrl = sourceUrl, title = "旧缓存", syncedAtMs = nowMs - DAY_MS)
            val latest = guideInfo(sourceUrl = sourceUrl, title = "新详情", syncedAtMs = nowMs)
            val metaStore = tempMetaStore(context)
            var savedInfo: BaStudentGuideInfo? = null
            var fetchCount = 0
            val repository =
                repository(
                    nowMs = nowMs,
                    cacheSnapshot = BaStudentGuideCacheSnapshot(cached, hasCache = true, isComplete = true, cached.syncedAtMs),
                    catalog = catalogBundle(
                        catalogEntry(
                            sourceUrl = sourceUrl,
                            contentId = 10002L,
                            tab = BaGuideCatalogTab.Student,
                            createdAtSec = (nowMs - 2L * DAY_MS) / 1000L,
                        ),
                    ),
                    metaStore = metaStore,
                    fetcher = {
                        fetchCount += 1
                        latest
                    },
                    saver = { savedInfo = it },
                )

            val result =
                repository.load(context, sourceUrl, currentInfo = cached, manualRefresh = true)

            assertEquals(latest, result.info)
            assertNull(result.error)
            assertEquals(1, fetchCount)
            assertEquals(latest, savedInfo)
            assertNotNull(result.cacheMeta)
            assertEquals(BaGuideStudentDetailFreshnessTier.HotUpdate, result.cacheMeta.freshnessTier)
            val meta = metaStore.loadMeta(sourceUrl)
            assertNotNull(meta)
            assertEquals(BaGuideStudentDetailFreshnessTier.HotUpdate, meta.freshnessTier)
            assertEquals(nowMs, meta.cachedAtMs)
            assertEquals(nowMs, meta.lastValidatedAtMs)
        }

    @Test
    fun `manual refresh implemented student detail retains referenced media instead of clearing complete cache`() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Application>()
            val nowMs = 100L * DAY_MS
            val sourceUrl = "https://www.gamekee.com/ba/tj/10021.html"
            val cached = guideInfo(sourceUrl = sourceUrl, title = "媒体旧缓存", syncedAtMs = nowMs - DAY_MS)
            val latest =
                guideInfo(sourceUrl = sourceUrl, title = "媒体新详情", syncedAtMs = nowMs).copy(
                    skillRows = listOf(BaGuideRow("EX", "desc", imageUrl = "https://example.com/skill.png")),
                )
            val metaStore = tempMetaStore(context)
            var clearCalled = false
            var retainedSourceUrl = ""
            var retainedUrls: Set<String> = emptySet()
            val repository =
                repository(
                    nowMs = nowMs,
                    cacheSnapshot = BaStudentGuideCacheSnapshot(cached, hasCache = true, isComplete = true, cached.syncedAtMs),
                    catalog = catalogBundle(
                        catalogEntry(
                            sourceUrl = sourceUrl,
                            contentId = 10021L,
                            tab = BaGuideCatalogTab.Student,
                            createdAtSec = (nowMs - 40L * DAY_MS) / 1000L,
                        ),
                    ),
                    metaStore = metaStore,
                    fetcher = { latest },
                    clearer = { _, _ -> clearCalled = true },
                    mediaRetainer = { _, url, urls ->
                        retainedSourceUrl = url
                        retainedUrls = urls
                    },
                )

            val result =
                repository.load(context, sourceUrl, currentInfo = cached, manualRefresh = true)

            assertEquals(latest, result.info)
            assertFalse(clearCalled)
            assertEquals(sourceUrl, retainedSourceUrl)
            assertTrue(retainedUrls.contains("https://example.com/媒体新详情.png"))
            assertTrue(retainedUrls.contains("https://example.com/skill.png"))
        }

    @Test
    fun `implemented student incomplete cache still clears before saving refreshed detail`() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Application>()
            val nowMs = 100L * DAY_MS
            val sourceUrl = "https://www.gamekee.com/ba/tj/10022.html"
            val latest = guideInfo(sourceUrl = sourceUrl, title = "补全详情", syncedAtMs = nowMs)
            val metaStore = tempMetaStore(context)
            var clearedSourceUrl = ""
            val repository =
                repository(
                    nowMs = nowMs,
                    cacheSnapshot = BaStudentGuideCacheSnapshot(null, hasCache = true, isComplete = false, syncedAtMs = 0L),
                    catalog = catalogBundle(
                        catalogEntry(
                            sourceUrl = sourceUrl,
                            contentId = 10022L,
                            tab = BaGuideCatalogTab.Student,
                            createdAtSec = (nowMs - 40L * DAY_MS) / 1000L,
                        ),
                    ),
                    metaStore = metaStore,
                    fetcher = { latest },
                    clearer = { _, url -> clearedSourceUrl = url },
                )

            val result =
                repository.load(context, sourceUrl)

            assertEquals(latest, result.info)
            assertEquals(sourceUrl, clearedSourceUrl)
        }

    @Test
    fun `expired hot update student cache returns first paint and requests background validation`() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Application>()
            val nowMs = 100L * DAY_MS
            val sourceUrl = "https://www.gamekee.com/ba/tj/10003.html"
            val cached = guideInfo(sourceUrl = sourceUrl, title = "热更新缓存", syncedAtMs = nowMs - DAY_MS)
            val latest = guideInfo(sourceUrl = sourceUrl, title = "热更新网络", syncedAtMs = nowMs)
            val metaStore = tempMetaStore(context)
            val entry =
                catalogEntry(
                    sourceUrl = sourceUrl,
                    contentId = 10003L,
                    tab = BaGuideCatalogTab.Student,
                    createdAtSec = (nowMs - 2L * DAY_MS) / 1000L,
                )
            metaStore.saveMeta(
                detailMeta(
                    sourceUrl = sourceUrl,
                    contentId = entry.contentId,
                    tier = BaGuideStudentDetailFreshnessTier.HotUpdate,
                    catalogCreatedAtSec = entry.createdAtSec,
                    cachedAtMs = cached.syncedAtMs,
                    lastValidatedAtMs = nowMs - 2L * HOUR_MS,
                ),
            )
            var fetchCount = 0
            val repository =
                repository(
                    nowMs = nowMs,
                    cacheSnapshot = BaStudentGuideCacheSnapshot(cached, hasCache = true, isComplete = true, cached.syncedAtMs),
                    catalog = catalogBundle(entry),
                    metaStore = metaStore,
                    fetcher = {
                        fetchCount += 1
                        latest
                    },
                )

            val firstPaint =
                repository.load(context, sourceUrl)

            assertEquals(cached, firstPaint.info)
            assertNull(firstPaint.error)
            assertTrue(firstPaint.validateInBackground)
            assertNotNull(firstPaint.cacheMeta)
            assertEquals(BaGuideStudentDetailFreshnessTier.HotUpdate, firstPaint.cacheMeta.freshnessTier)
            assertEquals(0, fetchCount)

            val validation =
                repository.load(context, sourceUrl, currentInfo = cached, forceValidation = true)

            assertEquals(latest, validation.info)
            assertNull(validation.error)
            assertFalse(validation.validateInBackground)
            assertNotNull(validation.cacheMeta)
            assertEquals(nowMs, validation.cacheMeta.lastValidatedAtMs)
            assertEquals(1, fetchCount)
        }

    @Test
    fun `npc satellite detail keeps legacy refresh interval and skips student meta`() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Application>()
            val nowMs = 100L * DAY_MS
            val sourceUrl = "https://www.gamekee.com/ba/tj/20001.html"
            val cached = guideInfo(sourceUrl = sourceUrl, title = "卫星缓存", syncedAtMs = nowMs - 2L * HOUR_MS)
            val metaStore = tempMetaStore(context)
            var fetchCalled = false
            val repository =
                repository(
                    nowMs = nowMs,
                    refreshIntervalHours = 12,
                    cacheSnapshot = BaStudentGuideCacheSnapshot(cached, hasCache = true, isComplete = true, cached.syncedAtMs),
                    catalog = catalogBundle(
                        catalogEntry(
                            sourceUrl = sourceUrl,
                            contentId = 20001L,
                            tab = BaGuideCatalogTab.NpcSatellite,
                            createdAtSec = (nowMs - DAY_MS) / 1000L,
                        ),
                    ),
                    metaStore = metaStore,
                    fetcher = {
                        fetchCalled = true
                        guideInfo(sourceUrl = sourceUrl, title = "网络卫星", syncedAtMs = nowMs)
                    },
                )

            val result =
                repository.load(context, sourceUrl)

            assertEquals(cached, result.info)
            assertNull(result.error)
            assertFalse(fetchCalled)
            assertNull(metaStore.loadMeta(sourceUrl))
        }

    @Test
    fun `concurrent forced validations for the same student share one network fetch`() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Application>()
            val nowMs = 100L * DAY_MS
            val sourceUrl = "https://www.gamekee.com/ba/tj/10004.html"
            val cached = guideInfo(sourceUrl = sourceUrl, title = "并发旧缓存", syncedAtMs = nowMs - DAY_MS)
            val latest = guideInfo(sourceUrl = sourceUrl, title = "并发新详情", syncedAtMs = nowMs)
            val entry =
                catalogEntry(
                    sourceUrl = sourceUrl,
                    contentId = 10004L,
                    tab = BaGuideCatalogTab.Student,
                    createdAtSec = (nowMs - 40L * DAY_MS) / 1000L,
                )
            val metaStore = tempMetaStore(context)
            metaStore.saveMeta(
                detailMeta(
                    sourceUrl = sourceUrl,
                    contentId = entry.contentId,
                    tier = BaGuideStudentDetailFreshnessTier.Stable,
                    catalogCreatedAtSec = entry.createdAtSec,
                    cachedAtMs = cached.syncedAtMs,
                    lastValidatedAtMs = nowMs - 5L * DAY_MS,
                ),
            )
            val releaseFetch = CompletableDeferred<Unit>()
            val firstFetchStarted = CompletableDeferred<Unit>()
            var fetchCount = 0
            val repository =
                repository(
                    nowMs = nowMs,
                    cacheSnapshot = BaStudentGuideCacheSnapshot(cached, hasCache = true, isComplete = true, cached.syncedAtMs),
                    catalog = catalogBundle(entry),
                    metaStore = metaStore,
                    fetcher = {
                        fetchCount += 1
                        if (fetchCount == 1) {
                            firstFetchStarted.complete(Unit)
                        }
                        releaseFetch.await()
                        latest
                    },
                )

            val first =
                async {
                    repository.load(context, sourceUrl, currentInfo = cached, forceValidation = true)
                }
            firstFetchStarted.await()
            val second =
                async {
                    repository.load(context, sourceUrl, currentInfo = cached, forceValidation = true)
                }
            releaseFetch.complete(Unit)
            val results = awaitAll(first, second)

            assertEquals(1, fetchCount)
            assertEquals(listOf(latest, latest), results.map { it.info })
            assertTrue(results.all { it.error == null })
        }

    @Test
    fun `student cache inside retry window skips automatic background validation`() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Application>()
            val nowMs = 100L * DAY_MS
            val sourceUrl = "https://www.gamekee.com/ba/tj/10005.html"
            val cached = guideInfo(sourceUrl = sourceUrl, title = "重试窗口缓存", syncedAtMs = nowMs - DAY_MS)
            val entry =
                catalogEntry(
                    sourceUrl = sourceUrl,
                    contentId = 10005L,
                    tab = BaGuideCatalogTab.Student,
                    createdAtSec = (nowMs - 2L * DAY_MS) / 1000L,
                )
            val metaStore = tempMetaStore(context)
            metaStore.saveMeta(
                detailMeta(
                    sourceUrl = sourceUrl,
                    contentId = entry.contentId,
                    tier = BaGuideStudentDetailFreshnessTier.HotUpdate,
                    catalogCreatedAtSec = entry.createdAtSec,
                    cachedAtMs = cached.syncedAtMs,
                    lastValidatedAtMs = nowMs - 2L * HOUR_MS,
                ).copy(
                    failureCount = 2,
                    lastFailureAtMs = nowMs - 5L * 60L * 1000L,
                    nextRetryAtMs = nowMs + HOUR_MS,
                ),
            )
            var fetchCalled = false
            val repository =
                repository(
                    nowMs = nowMs,
                    cacheSnapshot = BaStudentGuideCacheSnapshot(cached, hasCache = true, isComplete = true, cached.syncedAtMs),
                    catalog = catalogBundle(entry),
                    metaStore = metaStore,
                    fetcher = {
                        fetchCalled = true
                        guideInfo(sourceUrl = sourceUrl, title = "重试窗口网络", syncedAtMs = nowMs)
                    },
                )

            val result =
                repository.load(context, sourceUrl)

            assertEquals(cached, result.info)
            assertNull(result.error)
            assertFalse(result.validateInBackground)
            assertFalse(fetchCalled)
        }

    @Test
    fun `changed detail url keeps first seen time from content id meta`() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Application>()
            val nowMs = 100L * DAY_MS
            val oldUrl = "https://www.gamekee.com/ba/tj/10006-old.html"
            val newUrl = "https://www.gamekee.com/ba/tj/10006.html"
            val cached = guideInfo(sourceUrl = newUrl, title = "迁移缓存", syncedAtMs = nowMs - DAY_MS)
            val metaStore = tempMetaStore(context)
            val oldFirstSeenAtMs = nowMs - 60L * DAY_MS
            metaStore.saveMeta(
                detailMeta(
                    sourceUrl = oldUrl,
                    contentId = 10006L,
                    tier = BaGuideStudentDetailFreshnessTier.Stable,
                    catalogCreatedAtSec = 0L,
                    cachedAtMs = nowMs - 10L * DAY_MS,
                    lastValidatedAtMs = nowMs - 5L * DAY_MS,
                ).copy(firstSeenAtMs = oldFirstSeenAtMs),
            )
            var fetchCalled = false
            val repository =
                repository(
                    nowMs = nowMs,
                    cacheSnapshot = BaStudentGuideCacheSnapshot(cached, hasCache = true, isComplete = true, cached.syncedAtMs),
                    catalog = catalogBundle(
                        catalogEntry(
                            sourceUrl = newUrl,
                            contentId = 10006L,
                            tab = BaGuideCatalogTab.Student,
                            createdAtSec = 0L,
                        ),
                    ),
                    metaStore = metaStore,
                    fetcher = {
                        fetchCalled = true
                        guideInfo(sourceUrl = newUrl, title = "迁移网络", syncedAtMs = nowMs)
                    },
                )

            val result =
                repository.load(context, newUrl)

            assertEquals(cached, result.info)
            assertFalse(fetchCalled)
            assertNotNull(result.cacheMeta)
            assertEquals(oldFirstSeenAtMs, result.cacheMeta.firstSeenAtMs)
            assertEquals(oldFirstSeenAtMs, metaStore.loadMeta(newUrl)?.firstSeenAtMs)
        }

    @Test
    fun `detail refresh extracts release date and upserts catalog release index`() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Application>()
            val nowMs = 100L * DAY_MS
            val sourceUrl = "https://www.gamekee.com/ba/tj/10007.html"
            val cached = guideInfo(sourceUrl = sourceUrl, title = "日期旧缓存", syncedAtMs = nowMs - DAY_MS)
            val latest =
                guideInfo(sourceUrl = sourceUrl, title = "日期新详情", syncedAtMs = nowMs).copy(
                    profileRows = listOf(BaGuideRow("实装日期", "2025年1月2日")),
                )
            val metaStore = tempMetaStore(context)
            var upsertedReleaseDates: Map<Long, Long> = emptyMap()
            val repository =
                repository(
                    nowMs = nowMs,
                    cacheSnapshot = BaStudentGuideCacheSnapshot(cached, hasCache = true, isComplete = true, cached.syncedAtMs),
                    catalog = catalogBundle(
                        catalogEntry(
                            sourceUrl = sourceUrl,
                            contentId = 10007L,
                            tab = BaGuideCatalogTab.Student,
                            createdAtSec = (nowMs - 40L * DAY_MS) / 1000L,
                        ),
                    ),
                    metaStore = metaStore,
                    fetcher = { latest },
                    releaseDateIndexUpserter = { upsertedReleaseDates = it },
                )

            val result =
                repository.load(context, sourceUrl, currentInfo = cached, manualRefresh = true)

            assertEquals(latest, result.info)
            assertNotNull(result.cacheMeta)
            assertTrue(result.cacheMeta.releaseDateSec > 0L)
            assertEquals(result.cacheMeta.releaseDateSec, upsertedReleaseDates[10007L])
        }

    @Test
    fun `clear guide cache removes payload media and detail meta`() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Application>()
            val nowMs = 100L * DAY_MS
            val sourceUrl = "https://www.gamekee.com/ba/tj/10008.html"
            val metaStore = tempMetaStore(context)
            metaStore.saveMeta(
                detailMeta(
                    sourceUrl = sourceUrl,
                    contentId = 10008L,
                    tier = BaGuideStudentDetailFreshnessTier.Stable,
                    catalogCreatedAtSec = (nowMs - 40L * DAY_MS) / 1000L,
                    cachedAtMs = nowMs - DAY_MS,
                    lastValidatedAtMs = nowMs - HOUR_MS,
                ),
            )
            var clearedSourceUrl = ""
            val repository =
                repository(
                    nowMs = nowMs,
                    cacheSnapshot = BaStudentGuideCacheSnapshot.EMPTY,
                    catalog = null,
                    metaStore = metaStore,
                    fetcher = { guideInfo(sourceUrl = sourceUrl, title = "网络学生", syncedAtMs = nowMs) },
                    clearer = { _, clearedUrl -> clearedSourceUrl = clearedUrl },
                )

            repository.clearGuideCache(context, sourceUrl)

            assertEquals(sourceUrl, clearedSourceUrl)
            assertNull(metaStore.loadMeta(sourceUrl))
        }

    @Test
    fun `clear orphan student detail cache removes stale meta and cached payload`() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Application>()
            val nowMs = 100L * DAY_MS
            val oldUrl = "https://www.gamekee.com/ba/tj/10031-old.html"
            val newUrl = "https://www.gamekee.com/ba/tj/10031.html"
            val keepUrl = "https://www.gamekee.com/ba/tj/10032.html"
            val metaStore = tempMetaStore(context)
            val oldMeta =
                detailMeta(
                    sourceUrl = oldUrl,
                    contentId = 10031L,
                    tier = BaGuideStudentDetailFreshnessTier.Stable,
                    catalogCreatedAtSec = (nowMs - 40L * DAY_MS) / 1000L,
                    cachedAtMs = nowMs - DAY_MS,
                    lastValidatedAtMs = nowMs - HOUR_MS,
                )
            val keepMeta =
                detailMeta(
                    sourceUrl = keepUrl,
                    contentId = 10032L,
                    tier = BaGuideStudentDetailFreshnessTier.Stable,
                    catalogCreatedAtSec = (nowMs - 40L * DAY_MS) / 1000L,
                    cachedAtMs = nowMs - DAY_MS,
                    lastValidatedAtMs = nowMs - HOUR_MS,
                )
            metaStore.saveMeta(oldMeta)
            metaStore.saveMeta(keepMeta)
            val clearedUrls = mutableListOf<String>()
            val repository =
                repository(
                    nowMs = nowMs,
                    cacheSnapshot = BaStudentGuideCacheSnapshot.EMPTY,
                    catalog =
                        catalogBundle(
                            catalogEntry(
                                sourceUrl = newUrl,
                                contentId = 10031L,
                                tab = BaGuideCatalogTab.Student,
                                createdAtSec = (nowMs - 40L * DAY_MS) / 1000L,
                            ),
                            catalogEntry(
                                sourceUrl = keepUrl,
                                contentId = 10032L,
                                tab = BaGuideCatalogTab.Student,
                                createdAtSec = (nowMs - 40L * DAY_MS) / 1000L,
                            ),
                        ),
                    metaStore = metaStore,
                    fetcher = { guideInfo(sourceUrl = it, title = "网络学生", syncedAtMs = nowMs) },
                    clearer = { _, url -> clearedUrls += url },
                )

            val removedCount = repository.clearOrphanStudentDetailCache(context)

            assertEquals(1, removedCount)
            assertEquals(listOf(oldUrl), clearedUrls)
            assertNull(metaStore.loadMeta(oldUrl))
            assertEquals(keepMeta, metaStore.loadMeta(keepUrl))
        }

    @Test
    fun `background validation candidates prefer recent and newest favorites within limit`() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Application>()
            val nowMs = 100L * DAY_MS
            val recentUrl = "https://www.gamekee.com/ba/tj/10041.html"
            val favoriteNewUrl = "https://www.gamekee.com/ba/tj/10042.html"
            val favoriteOldUrl = "https://www.gamekee.com/ba/tj/10043.html"
            val npcUrl = "https://www.gamekee.com/ba/tj/10044.html"
            val catalog =
                catalogBundle(
                    catalogEntry(
                        sourceUrl = recentUrl,
                        contentId = 10041L,
                        tab = BaGuideCatalogTab.Student,
                        createdAtSec = (nowMs - 40L * DAY_MS) / 1000L,
                    ),
                    catalogEntry(
                        sourceUrl = favoriteNewUrl,
                        contentId = 10042L,
                        tab = BaGuideCatalogTab.Student,
                        createdAtSec = (nowMs - 40L * DAY_MS) / 1000L,
                    ),
                    catalogEntry(
                        sourceUrl = favoriteOldUrl,
                        contentId = 10043L,
                        tab = BaGuideCatalogTab.Student,
                        createdAtSec = (nowMs - 40L * DAY_MS) / 1000L,
                    ),
                    catalogEntry(
                        sourceUrl = npcUrl,
                        contentId = 10044L,
                        tab = BaGuideCatalogTab.NpcSatellite,
                        createdAtSec = (nowMs - 40L * DAY_MS) / 1000L,
                    ),
                )
            val fetchedUrls = mutableListOf<String>()
            val repository =
                repository(
                    nowMs = nowMs,
                    cacheSnapshot = BaStudentGuideCacheSnapshot.EMPTY,
                    catalog = catalog,
                    metaStore = tempMetaStore(context),
                    fetcher = { sourceUrl ->
                        fetchedUrls += sourceUrl
                        guideInfo(sourceUrl = sourceUrl, title = "网络学生", syncedAtMs = nowMs)
                    },
                )

            val summary =
                repository.validateFavoriteAndRecentStudentDetails(
                    context = context,
                    catalog = catalog,
                    favoriteContentIdsByFavoritedAtMs =
                        mapOf(
                            10043L to nowMs - HOUR_MS,
                            10042L to nowMs,
                            10044L to nowMs + HOUR_MS,
                        ),
                    recentSourceUrls = listOf(recentUrl, npcUrl),
                    maxCandidates = 2,
                    maxParallelism = 1,
                )

            assertEquals(2, summary.candidateCount)
            assertEquals(2, summary.completedCount)
            assertEquals(0, summary.failedCount)
            assertEquals(listOf(recentUrl, favoriteNewUrl), fetchedUrls)
        }

    @Test
    fun `background validation skips fresh cached favorite without network fetch`() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Application>()
            val nowMs = 100L * DAY_MS
            val sourceUrl = "https://www.gamekee.com/ba/tj/10045.html"
            val cached = guideInfo(sourceUrl = sourceUrl, title = "新鲜缓存", syncedAtMs = nowMs - HOUR_MS)
            val catalog =
                catalogBundle(
                    catalogEntry(
                        sourceUrl = sourceUrl,
                        contentId = 10045L,
                        tab = BaGuideCatalogTab.Student,
                        createdAtSec = (nowMs - 40L * DAY_MS) / 1000L,
                    ),
                )
            var fetchCalled = false
            val repository =
                repository(
                    nowMs = nowMs,
                    cacheSnapshot = BaStudentGuideCacheSnapshot(cached, hasCache = true, isComplete = true, cached.syncedAtMs),
                    catalog = catalog,
                    metaStore = tempMetaStore(context),
                    fetcher = {
                        fetchCalled = true
                        guideInfo(sourceUrl = sourceUrl, title = "网络学生", syncedAtMs = nowMs)
                    },
                )

            val summary =
                repository.validateFavoriteAndRecentStudentDetails(
                    context = context,
                    catalog = catalog,
                    favoriteContentIdsByFavoritedAtMs = mapOf(10045L to nowMs),
                    recentSourceUrls = emptyList(),
                    maxCandidates = 4,
                    maxParallelism = 1,
                )

            assertEquals(1, summary.candidateCount)
            assertEquals(1, summary.completedCount)
            assertFalse(fetchCalled)
        }

    private fun repository(
        nowMs: Long,
        refreshIntervalHours: Int = 12,
        cacheSnapshot: BaStudentGuideCacheSnapshot,
        cacheSnapshotLoader: suspend (String) -> BaStudentGuideCacheSnapshot = { cacheSnapshot },
        catalog: BaGuideCatalogBundle?,
        metaStore: BaGuideStudentDetailFileCacheStore,
        fetcher: suspend (String) -> BaStudentGuideInfo,
        saver: suspend (BaStudentGuideInfo) -> Unit = {},
        clearer: suspend (Context, String) -> Unit = { _, _ -> },
        mediaRetainer: suspend (Context, String, Set<String>) -> Unit = { _, _, _ -> },
        releaseDateIndexUpserter: suspend (Map<Long, Long>) -> Unit = {},
    ): BaStudentGuideRepository =
        BaStudentGuideRepository(
            ioDispatcher = Dispatchers.Unconfined,
            parseDispatcher = Dispatchers.Unconfined,
            clock = BaGuideDataClock { nowMs },
            refreshIntervalLoader = { refreshIntervalHours },
            cacheSnapshotLoader = cacheSnapshotLoader,
            cacheSaver = saver,
            cacheClearer = clearer,
            mediaCacheRetainer = mediaRetainer,
            guideFetcher = { sourceUrl, _, _, _ -> fetcher(sourceUrl) },
            catalogBundleLoader = { catalog },
            detailCacheStoreProvider = { metaStore },
            releaseDateIndexUpserter = releaseDateIndexUpserter,
        )

    private fun tempMetaStore(context: Application): BaGuideStudentDetailFileCacheStore {
        val root = File(context.filesDir, "student-repository-cache-test-${System.nanoTime()}").apply {
            deleteRecursively()
            mkdirs()
        }
        return BaGuideStudentDetailFileCacheStore(root)
    }

    private suspend fun BaStudentGuideRepository.load(
        context: Context,
        sourceUrl: String,
        currentInfo: BaStudentGuideInfo? = null,
        manualRefresh: Boolean = false,
        forceValidation: Boolean = false,
    ) = loadGuide(
        context = context,
        sourceUrl = sourceUrl,
        currentInfo = currentInfo,
        manualRefresh = manualRefresh,
        forceValidation = forceValidation,
        loadFailedText = "加载失败",
        refreshFailedKeepCacheText = "保留缓存",
    )

    private fun guideInfo(
        sourceUrl: String,
        title: String,
        syncedAtMs: Long,
    ): BaStudentGuideInfo =
        BaStudentGuideInfo(
            sourceUrl = sourceUrl,
            title = title,
            subtitle = "GameKee",
            description = "desc",
            imageUrl = "https://example.com/$title.png",
            summary = "summary",
            stats = listOf("学校" to "夏莱"),
            profileRows = listOf(BaGuideRow("学校", "夏莱")),
            syncedAtMs = syncedAtMs,
        )

    private fun catalogBundle(vararg entries: BaGuideCatalogEntry): BaGuideCatalogBundle =
        BaGuideCatalogBundle(
            entriesByTab =
                mapOf(
                    BaGuideCatalogTab.Student to entries.toList().filter { it.tab == BaGuideCatalogTab.Student },
                    BaGuideCatalogTab.NpcSatellite to
                        entries.toList().filter { it.tab == BaGuideCatalogTab.NpcSatellite },
                ),
            syncedAtMs = 1_000L,
        )

    private fun catalogEntry(
        sourceUrl: String,
        contentId: Long,
        tab: BaGuideCatalogTab,
        createdAtSec: Long,
    ): BaGuideCatalogEntry =
        testCatalogEntry(
            contentId = contentId,
            tab = tab,
            iconUrl = "https://example.com/icon.png",
            order = 1,
            createdAtSec = createdAtSec,
            detailUrl = sourceUrl,
        )

    private fun detailMeta(
        sourceUrl: String,
        contentId: Long,
        tier: BaGuideStudentDetailFreshnessTier,
        catalogCreatedAtSec: Long,
        cachedAtMs: Long,
        lastValidatedAtMs: Long,
    ): BaGuideStudentDetailCacheMeta =
        BaGuideStudentDetailCacheMeta(
            sourceUrl = sourceUrl,
            contentId = contentId,
            tab = BaGuideCatalogTab.Student,
            catalogCreatedAtSec = catalogCreatedAtSec,
            releaseDateSec = 0L,
            firstSeenAtMs = 0L,
            cachedAtMs = cachedAtMs,
            lastValidatedAtMs = lastValidatedAtMs,
            lastChangedAtMs = cachedAtMs,
            nextAutoRefreshAtMs = lastValidatedAtMs + tier.validationIntervalMs,
            freshnessTier = tier,
            contentHash = "cached",
            unchangedValidationCount = 0,
            failureCount = 0,
            lastFailureAtMs = 0L,
            nextRetryAtMs = 0L,
        )

    private companion object {
        private const val HOUR_MS = 60L * 60L * 1000L
        private const val DAY_MS = 24L * HOUR_MS
    }
}
