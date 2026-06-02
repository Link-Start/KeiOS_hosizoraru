package os.kei.ui.page.main.ba

import android.content.Context
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import os.kei.R
import os.kei.core.concurrency.AppDispatchers
import os.kei.ui.page.main.ba.support.BASettingsStore
import os.kei.ui.page.main.ba.support.BA_CALENDAR_CACHE_SCHEMA_VERSION
import os.kei.ui.page.main.ba.support.BA_POOL_CACHE_SCHEMA_VERSION
import os.kei.ui.page.main.ba.support.BaCalendarEntry
import os.kei.ui.page.main.ba.support.BaPoolEntry
import os.kei.ui.page.main.ba.support.decodeBaCalendarEntries
import os.kei.ui.page.main.ba.support.decodeBaPoolEntries
import os.kei.ui.page.main.ba.support.fetchBaCalendarRemoteResult
import os.kei.ui.page.main.ba.support.fetchBaPoolRemoteResult
import os.kei.ui.page.main.ba.support.runWithHardTimeout
import os.kei.ui.page.main.student.fetch.extractGuideContentIdFromUrl
import os.kei.ui.page.main.student.fetch.normalizeGuideUrl
import os.kei.ui.page.main.student.page.state.BaStudentGuideRepository

@Immutable
internal sealed interface BaPoolGuideOpenPlan {
    data object Missing : BaPoolGuideOpenPlan

    data class OpenInApp(
        val canonicalGuideUrl: String,
    ) : BaPoolGuideOpenPlan

    data class OpenExternal(
        val url: String,
    ) : BaPoolGuideOpenPlan
}

@Immutable
internal data class BaCalendarSyncSnapshot(
    val entries: List<BaCalendarEntry>,
    val loading: Boolean,
    val error: String?,
    val lastSyncMs: Long,
    val imageWarmEntries: List<BaCalendarEntry> = entries,
)

@Immutable
internal data class BaPoolSyncSnapshot(
    val entries: List<BaPoolEntry>,
    val loading: Boolean,
    val error: String?,
    val lastSyncMs: Long,
    val imageWarmEntries: List<BaPoolEntry> = entries,
)

internal object BaCalendarPoolRepository {
    private val poolStudentGuideUrlRepository = BaPoolStudentGuideUrlRepository()
    private val studentGuideRepository = BaStudentGuideRepository()

    fun loadSettingsSnapshot() = BASettingsStore.loadCalendarPoolSnapshot()

    suspend fun loadSettingsSnapshotAsync() =
        withContext(AppDispatchers.baFetch) {
            BASettingsStore.loadCalendarPoolSnapshot()
        }

    fun saveServerIndex(index: Int) {
        BASettingsStore.saveCalendarPoolServerIndex(index)
    }

    suspend fun saveServerIndexAsync(index: Int) {
        withContext(AppDispatchers.baFetch) {
            BASettingsStore.saveCalendarPoolServerIndex(index)
        }
    }

    suspend fun saveRefreshIntervalAsync(
        hours: Int,
        lastSyncMs: Long,
    ): BaRefreshIntervalPersistenceResult =
        BaSettingsPersistenceRepository.persistRefreshIntervalAsync(
            hours = hours,
            calendarLastSyncMs = lastSyncMs,
        )

    suspend fun saveActivityShowEndedAsync(enabled: Boolean) {
        withContext(AppDispatchers.baFetch) {
            BASettingsStore.saveActivityShowEnded(enabled)
        }
    }

    suspend fun savePoolShowEndedAsync(enabled: Boolean) {
        withContext(AppDispatchers.baFetch) {
            BASettingsStore.savePoolShowEnded(enabled)
        }
    }

    suspend fun saveShowCalendarPoolImagesAsync(enabled: Boolean) {
        withContext(AppDispatchers.baFetch) {
            BASettingsStore.saveShowCalendarPoolImages(enabled)
        }
    }

    suspend fun preparePoolGuideOpen(rawUrl: String): BaPoolGuideOpenPlan =
        withContext(AppDispatchers.baFetch) {
            val normalized = normalizeGuideUrl(rawUrl)
            if (normalized.isBlank()) {
                return@withContext BaPoolGuideOpenPlan.Missing
            }
            val gameKeeGuideUrl = normalized.toGameKeeGuideCanonicalUrlOrNull()
            if (gameKeeGuideUrl != null) {
                studentGuideRepository.saveCurrentUrlAsync(gameKeeGuideUrl)
                return@withContext BaPoolGuideOpenPlan.OpenInApp(gameKeeGuideUrl)
            }
            BaPoolGuideOpenPlan.OpenExternal(normalized)
        }

    suspend fun syncCalendar(
        context: Context,
        isPageActive: Boolean,
        serverIndex: Int,
        reloadSignal: Int,
        calendarRefreshIntervalHours: Int,
        hydrationReady: Boolean,
    ): BaCalendarSyncSnapshot {
        if (!hydrationReady) {
            return BaCalendarSyncSnapshot(
                entries = emptyList(),
                loading = true,
                error = null,
                lastSyncMs = 0L,
            )
        }
        val cacheSnapshot =
            withContext(AppDispatchers.baFetch) {
                BASettingsStore.loadCalendarCacheSnapshot(serverIndex)
            }
        val hasCache = cacheSnapshot.raw.isNotBlank()
        val plan =
            BaCalendarPoolSyncPlanner.build(
                context = context,
                cacheSyncMs = cacheSnapshot.syncMs,
                hasCache = hasCache,
                cacheSchemaVersion = cacheSnapshot.version,
                expectedSchemaVersion = BA_CALENDAR_CACHE_SCHEMA_VERSION,
                reloadSignal = reloadSignal,
                refreshIntervalHours = calendarRefreshIntervalHours,
            )
        val now = plan.nowMs
        val cachedEntries =
            withContext(AppDispatchers.baFetch) {
                if (hasCache) {
                    runCatching { decodeBaCalendarEntries(cacheSnapshot.raw, now) }
                        .getOrElse { emptyList() }
                } else {
                    emptyList()
                }
            }
        val cachedEntriesWithLocalImages =
            BaCalendarPoolCacheWriter.hydrateCalendarImages(
                context = context,
                serverIndex = serverIndex,
                entries = cachedEntries,
                localOnly = !plan.networkAvailable,
            )

        if (!plan.shouldRequestNetwork) {
            return BaCalendarSyncSnapshot(
                entries = cachedEntriesWithLocalImages,
                loading = false,
                error = null,
                lastSyncMs = cacheSnapshot.syncMs,
                imageWarmEntries = cachedEntries,
            )
        }

        if (!isPageActive && plan.hasCache) {
            return BaCalendarSyncSnapshot(
                entries = cachedEntriesWithLocalImages,
                loading = false,
                error = null,
                lastSyncMs = cacheSnapshot.syncMs,
                imageWarmEntries = cachedEntries,
            )
        }

        if (!plan.networkAvailable) {
            return BaCalendarSyncSnapshot(
                entries = cachedEntriesWithLocalImages,
                loading = false,
                error =
                    if (plan.hasCache) {
                        context.getString(R.string.ba_calendar_pool_error_offline_cached)
                    } else {
                        context.getString(R.string.ba_calendar_pool_error_offline_no_cache)
                    },
                lastSyncMs = cacheSnapshot.syncMs,
                imageWarmEntries = cachedEntries,
            )
        }

        val result =
            try {
                Result.success(
                    runWithHardTimeout(15_000L) {
                        fetchBaCalendarRemoteResult(serverIndex, now)
                    },
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                Result.failure(error)
            }
        if (result.isSuccess) {
            val entries = result.getOrThrow().entries
            if (entries.isNotEmpty()) {
                val entriesWithLocalImages =
                    BaCalendarPoolCacheWriter.saveCalendarAndHydrateImages(
                        context = context,
                        serverIndex = serverIndex,
                        entries = entries,
                        nowMs = now,
                    )
                BaCalendarPoolSyncNotifier.dispatchCalendarSyncNotifications(
                    context = context,
                    serverIndex = serverIndex,
                    previousEntries = cachedEntries,
                    nextEntries = entries,
                    nowMs = now,
                    hadCache = plan.hasCache,
                )
                return BaCalendarSyncSnapshot(
                    entries = entriesWithLocalImages,
                    loading = false,
                    error = null,
                    lastSyncMs = now,
                    imageWarmEntries = entries,
                )
            }
            return BaCalendarSyncSnapshot(
                entries = cachedEntriesWithLocalImages,
                loading = false,
                error = if (plan.hasCache) context.getString(R.string.ba_calendar_pool_error_empty_keep_cached) else null,
                lastSyncMs = cacheSnapshot.syncMs,
                imageWarmEntries = cachedEntries,
            )
        }

        return BaCalendarSyncSnapshot(
            entries = cachedEntriesWithLocalImages,
            loading = false,
            error =
                if (plan.hasCache) {
                    context.getString(R.string.ba_calendar_pool_error_sync_failed_cached)
                } else {
                    context.getString(R.string.ba_calendar_error_sync_failed)
                },
            lastSyncMs = cacheSnapshot.syncMs,
            imageWarmEntries = cachedEntries,
        )
    }

    suspend fun syncPool(
        context: Context,
        isPageActive: Boolean,
        serverIndex: Int,
        reloadSignal: Int,
        calendarRefreshIntervalHours: Int,
        hydrationReady: Boolean,
    ): BaPoolSyncSnapshot {
        if (!hydrationReady) {
            return BaPoolSyncSnapshot(
                entries = emptyList(),
                loading = true,
                error = null,
                lastSyncMs = 0L,
            )
        }
        val cacheSnapshot =
            withContext(AppDispatchers.baFetch) {
                BASettingsStore.loadPoolCacheSnapshot(serverIndex)
            }
        val hasCache = cacheSnapshot.raw.isNotBlank()
        val plan =
            BaCalendarPoolSyncPlanner.build(
                context = context,
                cacheSyncMs = cacheSnapshot.syncMs,
                hasCache = hasCache,
                cacheSchemaVersion = cacheSnapshot.version,
                expectedSchemaVersion = BA_POOL_CACHE_SCHEMA_VERSION,
                reloadSignal = reloadSignal,
                refreshIntervalHours = calendarRefreshIntervalHours,
            )
        val now = plan.nowMs
        val cachedEntries =
            withContext(AppDispatchers.baFetch) {
                val decodedCachedEntries =
                    if (hasCache) {
                        runCatching { decodeBaPoolEntries(cacheSnapshot.raw, now) }
                            .getOrElse { emptyList() }
                    } else {
                        emptyList()
                    }
                poolStudentGuideUrlRepository.resolve(
                    serverIndex = serverIndex,
                    entries = decodedCachedEntries,
                    allowCatalogNetwork = false,
                )
            }
        val cachedEntriesWithLocalImages =
            BaCalendarPoolCacheWriter.hydratePoolImages(
                context = context,
                serverIndex = serverIndex,
                entries = cachedEntries,
                localOnly = !plan.networkAvailable,
            )

        if (!plan.shouldRequestNetwork) {
            return BaPoolSyncSnapshot(
                entries = cachedEntriesWithLocalImages,
                loading = false,
                error = null,
                lastSyncMs = cacheSnapshot.syncMs,
                imageWarmEntries = cachedEntries,
            )
        }

        if (!isPageActive && plan.hasCache) {
            return BaPoolSyncSnapshot(
                entries = cachedEntriesWithLocalImages,
                loading = false,
                error = null,
                lastSyncMs = cacheSnapshot.syncMs,
                imageWarmEntries = cachedEntries,
            )
        }

        if (!plan.networkAvailable) {
            return BaPoolSyncSnapshot(
                entries = cachedEntriesWithLocalImages,
                loading = false,
                error =
                    if (plan.hasCache) {
                        context.getString(R.string.ba_calendar_pool_error_offline_cached)
                    } else {
                        context.getString(R.string.ba_calendar_pool_error_offline_no_cache)
                    },
                lastSyncMs = cacheSnapshot.syncMs,
                imageWarmEntries = cachedEntries,
            )
        }

        val result =
            try {
                Result.success(
                    runWithHardTimeout(15_000L) {
                        fetchBaPoolRemoteResult(serverIndex, now)
                    },
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                Result.failure(error)
            }
        if (result.isSuccess) {
            val entries =
                poolStudentGuideUrlRepository.resolve(
                    serverIndex = serverIndex,
                    entries = result.getOrThrow().entries,
                    allowCatalogNetwork = true,
                )
            if (entries.isNotEmpty()) {
                val entriesWithLocalImages =
                    BaCalendarPoolCacheWriter.savePoolAndHydrateImages(
                        context = context,
                        serverIndex = serverIndex,
                        entries = entries,
                        nowMs = now,
                    )
                BaCalendarPoolSyncNotifier.dispatchPoolSyncNotifications(
                    context = context,
                    serverIndex = serverIndex,
                    previousEntries = cachedEntries,
                    nextEntries = entries,
                    nowMs = now,
                    hadCache = plan.hasCache,
                )
                return BaPoolSyncSnapshot(
                    entries = entriesWithLocalImages,
                    loading = false,
                    error = null,
                    lastSyncMs = now,
                    imageWarmEntries = entries,
                )
            }
            return BaPoolSyncSnapshot(
                entries = cachedEntriesWithLocalImages,
                loading = false,
                error = if (plan.hasCache) context.getString(R.string.ba_calendar_pool_error_empty_keep_cached) else null,
                lastSyncMs = cacheSnapshot.syncMs,
                imageWarmEntries = cachedEntries,
            )
        }

        return BaPoolSyncSnapshot(
            entries = cachedEntriesWithLocalImages,
            loading = false,
            error =
                if (plan.hasCache) {
                    context.getString(R.string.ba_calendar_pool_error_sync_failed_cached)
                } else {
                    context.getString(R.string.ba_pool_error_sync_failed)
                },
            lastSyncMs = cacheSnapshot.syncMs,
            imageWarmEntries = cachedEntries,
        )
    }
}

private val baGuideDetailPathRegex = Regex("""^/ba/tj/\d+(?:\.html)?$""", RegexOption.IGNORE_CASE)

private fun String.toGameKeeGuideCanonicalUrlOrNull(): String? {
    val uri = runCatching { android.net.Uri.parse(this) }.getOrNull() ?: return null
    val host = uri.host?.lowercase().orEmpty()
    val hostAccepted = host == "www.gamekee.com" || host == "gamekee.com"
    if (!hostAccepted || !baGuideDetailPathRegex.matches(uri.path.orEmpty())) return null
    val contentId = extractGuideContentIdFromUrl(this)?.takeIf { it > 0L } ?: return null
    return "https://www.gamekee.com/ba/tj/$contentId.html"
}
