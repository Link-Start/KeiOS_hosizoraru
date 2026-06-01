package os.kei.feature.home.data

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.core.log.AppLogger
import os.kei.core.prefs.CacheFreshnessSnapshot
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.local.GitHubTrackStoreSignals
import os.kei.feature.github.model.GitHubLookupStrategyOption
import os.kei.feature.github.model.GitHubTrackedReleaseStatus
import os.kei.feature.home.model.HOME_BA_AP_MAX
import os.kei.feature.home.model.HOME_BA_CAFE_DAILY_AP_BY_LEVEL
import os.kei.feature.home.model.HOME_BA_DEFAULT_FRIEND_CODE
import os.kei.feature.home.model.HomeAppOverview
import os.kei.feature.home.model.HomeBaOverview
import os.kei.feature.home.model.HomeGitHubOverview
import os.kei.feature.home.model.HomeMcpOverview
import os.kei.feature.home.model.HomeOverviewCard
import os.kei.feature.home.model.HomeOverviewSnapshot
import os.kei.feature.home.model.HomeWebDavOverview
import os.kei.feature.home.model.defaultHomeOverviewCards
import os.kei.mcp.server.McpServerUiState
import os.kei.ui.page.main.ba.support.BASettingsStore
import os.kei.ui.page.main.ba.support.BASettingsStoreSignals
import os.kei.ui.page.main.settings.cache.CacheStores
import os.kei.ui.page.main.sync.WebDavSyncItem
import os.kei.ui.page.main.sync.WebDavSyncStore
import os.kei.ui.page.main.sync.WebDavSyncStoreSignals

private const val TAG = "HomeOverviewRepository"

internal fun interface HomeOverviewClock {
    fun nowMs(): Long
}

internal object HomeSystemOverviewClock : HomeOverviewClock {
    override fun nowMs(): Long = System.currentTimeMillis()
}

internal class HomeOverviewRepository(
    private val context: Context,
    private val mcpUiState: StateFlow<McpServerUiState>,
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.fileIo,
    private val clock: HomeOverviewClock = HomeSystemOverviewClock,
) {
    private val appContext = context.applicationContext
    private val refreshRequests =
        MutableSharedFlow<String>(
            replay = 1,
            extraBufferCapacity = 1,
        )
    private val visibleOverviewCards = MutableStateFlow(defaultHomeOverviewCards())
    private val showCacheFreshnessInCards = MutableStateFlow(false)

    fun observeOverview(): Flow<HomeOverviewSnapshot> {
        val storedOverviewFlow =
            buildHomeOverviewStoreRefreshFlow(
                refreshRequests = refreshRequests,
                githubVersions = GitHubTrackStoreSignals.version,
                baHomeOverviewVersions = BASettingsStoreSignals.homeOverviewVersion,
                webDavVersions = WebDavSyncStoreSignals.version,
            ).onStart { emit("initial") }
                .map { reason ->
                    loadStoredOverview(reason)
                }
        return combine(
            mcpUiState,
            storedOverviewFlow,
            visibleOverviewCards,
            showCacheFreshnessInCards,
        ) { mcpState, storedOverview, visibleCards, showCacheFreshness ->
            HomeOverviewSnapshot(
                appOverview = storedOverview.appOverview,
                mcpOverview = mcpState.toHomeOverview(),
                githubOverview = storedOverview.githubOverview,
                webDavOverview = storedOverview.webDavOverview,
                baOverview = storedOverview.baOverview,
                visibleOverviewCards = visibleCards,
                showCacheFreshnessInCards = showCacheFreshness,
            )
        }.distinctUntilChanged()
    }

    fun requestRefresh(reason: String) {
        refreshRequests.tryEmit(reason)
    }

    suspend fun setOverviewCardVisible(
        card: HomeOverviewCard,
        visible: Boolean,
    ) {
        val updated =
            visibleOverviewCards.value
                .toMutableSet()
                .apply {
                    if (visible) add(card) else remove(card)
                }.toSet()
        visibleOverviewCards.value = updated
        withContext(ioDispatcher) {
            HomeOverviewPrefs.saveVisibleOverviewCards(updated)
        }
    }

    suspend fun setCacheFreshnessVisibleInCards(visible: Boolean) {
        showCacheFreshnessInCards.value = visible
        withContext(ioDispatcher) {
            HomeOverviewPrefs.saveCacheFreshnessVisibleInCards(visible)
        }
    }

    private suspend fun loadStoredOverview(reason: String): StoredHomeOverview =
        withContext(ioDispatcher) {
            val visibleCards =
                runCatching { HomeOverviewPrefs.loadVisibleOverviewCards() }
                    .onFailure { error ->
                        AppLogger.w(
                            TAG,
                            "loadHomeVisibleOverviewCards failed (reason=$reason)",
                            error,
                        )
                    }.getOrElse { defaultHomeOverviewCards() }
            visibleOverviewCards.value = visibleCards
            showCacheFreshnessInCards.value = HomeOverviewPrefs.loadCacheFreshnessVisibleInCards()
            val cacheFreshnessById = loadHomeCacheFreshnessById(appContext)

            val baOverview =
                runCatching {
                    loadHomeBaOverview(cacheFreshnessById["ba_calendar"] ?: CacheFreshnessSnapshot.Empty)
                }.onFailure { error ->
                    AppLogger.w(
                        TAG,
                        "loadHomeBaOverview failed (reason=$reason)",
                        error,
                    )
                }.getOrElse { HomeBaOverview(loaded = true) }
            val githubOverview =
                runCatching {
                    loadHomeGitHubOverview(
                        cacheFreshness = cacheFreshnessById["github"] ?: CacheFreshnessSnapshot.Empty,
                        nowMs = clock.nowMs(),
                    )
                }.onFailure { error ->
                    AppLogger.w(
                        TAG,
                        "loadHomeGitHubOverview failed (reason=$reason)",
                        error,
                    )
                }.getOrElse { HomeGitHubOverview(loaded = true) }
            StoredHomeOverview(
                appOverview = loadHomeAppOverview(appContext),
                githubOverview = githubOverview,
                webDavOverview = loadHomeWebDavOverview(),
                baOverview = baOverview,
            )
        }

    private fun McpServerUiState.toHomeOverview(): HomeMcpOverview =
        HomeMcpOverview(
            running = running,
            runningSinceEpochMs = runningSinceEpochMs,
            port = port,
            endpointPath = endpointPath,
            serverName = serverName,
            authTokenConfigured = authToken.isNotBlank(),
            authTokenPreview = buildTokenPreview(authToken),
            connectedClients = connectedClients,
            allowExternal = allowExternal,
        )

    private data class StoredHomeOverview(
        val appOverview: HomeAppOverview,
        val githubOverview: HomeGitHubOverview,
        val webDavOverview: HomeWebDavOverview,
        val baOverview: HomeBaOverview,
    )
}

private fun buildTokenPreview(token: String): String {
    val trimmed = token.trim()
    if (trimmed.isBlank()) return ""
    if (trimmed.length <= 4) return trimmed
    return "${trimmed.take(2)}…${trimmed.takeLast(2)}"
}

private fun loadHomeAppOverview(context: Context): HomeAppOverview =
    try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        HomeAppOverview(
            versionName = info.versionName.orEmpty(),
            versionCode = info.longVersionCode,
            loaded = true,
        )
    } catch (_: PackageManager.NameNotFoundException) {
        HomeAppOverview()
    } catch (_: RuntimeException) {
        HomeAppOverview()
    }

internal fun buildHomeOverviewStoreRefreshFlow(
    refreshRequests: Flow<String>,
    githubVersions: Flow<Long>,
    baHomeOverviewVersions: Flow<Long>,
    webDavVersions: Flow<Long>,
): Flow<String> =
    merge(
        refreshRequests,
        githubVersions
            .drop(1)
            .map { version -> "github_store_$version" },
        baHomeOverviewVersions
            .drop(1)
            .map { version -> "ba_store_$version" },
        webDavVersions
            .drop(1)
            .map { version -> "webdav_store_$version" },
    )

private fun loadHomeGitHubOverview(
    cacheFreshness: CacheFreshnessSnapshot,
    nowMs: Long,
): HomeGitHubOverview {
    val snapshot = GitHubTrackStore.loadSnapshot()
    val activeStrategyId = snapshot.lookupConfig.selectedStrategy.storageId
    val matchedCacheByTrackId =
        snapshot.items.associate { item ->
            val cache =
                snapshot.checkCache[item.id]
                    ?.takeIf { entry ->
                        entry.sourceStrategyId.ifBlank { GitHubLookupStrategyOption.AtomFeed.storageId } == activeStrategyId
                    }
            item.id to cache
        }
    val cacheHitCount = matchedCacheByTrackId.count { it.value != null }
    return HomeGitHubOverview(
        trackedCount = snapshot.items.size,
        cacheHitCount = cacheHitCount,
        updatableCount = matchedCacheByTrackId.count { it.value?.hasUpdate == true },
        preReleaseUpdateCount = matchedCacheByTrackId.count { it.value?.hasPreReleaseUpdate == true },
        failedCount =
            matchedCacheByTrackId.count { (_, entry) ->
                entry?.message?.let(GitHubTrackedReleaseStatus::isFailureMessage) == true
            },
        strategy = snapshot.lookupConfig.selectedStrategy,
        apiTokenConfigured = snapshot.lookupConfig.apiToken.isNotBlank(),
        shareImportLinkageEnabled = snapshot.lookupConfig.shareImportLinkageEnabled,
        pendingShareImport = snapshot.pendingShareImportTrack != null,
        refreshIntervalHours = snapshot.refreshIntervalHours,
        cachedRefreshMs = if (cacheHitCount > 0) snapshot.lastRefreshMs else 0L,
        cacheLabelNowMs = nowMs,
        cacheFreshness = cacheFreshness,
        loaded = true,
    )
}

private fun loadHomeBaOverview(cacheFreshness: CacheFreshnessSnapshot): HomeBaOverview {
    val snapshot = BASettingsStore.loadSnapshot()
    val activated = snapshot.idFriendCode != HOME_BA_DEFAULT_FRIEND_CODE
    val apCurrent = snapshot.apCurrent.coerceIn(0.0, HOME_BA_AP_MAX.toDouble()).toInt()
    val cafeLevel = snapshot.cafeLevel.coerceIn(1, HOME_BA_CAFE_DAILY_AP_BY_LEVEL.size)
    val cafeCap = HOME_BA_CAFE_DAILY_AP_BY_LEVEL[cafeLevel - 1]
    val cafeStored =
        snapshot.cafeStoredAp
            .coerceAtLeast(0.0)
            .toInt()
            .coerceAtMost(cafeCap)

    return HomeBaOverview(
        activated = activated,
        serverIndex = snapshot.serverIndex,
        apCurrent = apCurrent,
        apLimit = snapshot.apLimit,
        apNotifyEnabled = snapshot.apNotifyEnabled,
        apNotifyThreshold = snapshot.apNotifyThreshold,
        cafeLevel = cafeLevel,
        cafeStored = cafeStored,
        cafeCap = cafeCap,
        cacheFreshness = cacheFreshness,
        loaded = true,
    )
}

private fun loadHomeWebDavOverview(): HomeWebDavOverview =
    HomeWebDavOverview(
        configured = WebDavSyncStore.hasConfig(),
        autoSyncEnabled = WebDavSyncStore.isAutoSyncEnabled(),
        enabledItemCount = WebDavSyncItem.entries.count(WebDavSyncStore::isItemEnabled),
        totalItemCount = WebDavSyncItem.entries.size,
        lastFullSyncTimeMs = WebDavSyncStore.getLastFullSyncTime(),
    )

private fun loadHomeCacheFreshnessById(context: Context): Map<String, CacheFreshnessSnapshot> =
    runCatching {
        CacheStores.list(context).associate { entry -> entry.id to entry.freshness }
    }.getOrNull().orEmpty()
