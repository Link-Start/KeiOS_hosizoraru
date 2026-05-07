package os.kei.feature.home.data

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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
import os.kei.core.log.AppLogger
import os.kei.core.prefs.CacheFreshnessSnapshot
import os.kei.core.prefs.CacheStores
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.local.GitHubTrackStoreSignals
import os.kei.feature.github.model.GitHubLookupStrategyOption
import os.kei.feature.github.model.GitHubTrackedReleaseStatus
import os.kei.feature.home.model.HOME_BA_AP_MAX
import os.kei.feature.home.model.HOME_BA_CAFE_DAILY_AP_BY_LEVEL
import os.kei.feature.home.model.HOME_BA_DEFAULT_FRIEND_CODE
import os.kei.feature.home.model.HomeBaOverview
import os.kei.feature.home.model.HomeGitHubOverview
import os.kei.feature.home.model.HomeMcpOverview
import os.kei.feature.home.model.HomeOverviewCard
import os.kei.feature.home.model.HomeOverviewSnapshot
import os.kei.feature.home.model.defaultHomeOverviewCards
import os.kei.mcp.server.McpServerUiState
import os.kei.ui.page.main.ba.support.BASettingsStore
import os.kei.ui.page.main.ba.support.BASettingsStoreSignals

private const val TAG = "HomeOverviewRepository"

internal class HomeOverviewRepository(
    private val context: Context,
    private val mcpUiState: StateFlow<McpServerUiState>,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val appContext = context.applicationContext
    private val refreshRequests = MutableSharedFlow<String>(
        replay = 1,
        extraBufferCapacity = 1
    )
    private val visibleOverviewCards = MutableStateFlow(defaultHomeOverviewCards())
    private val showCacheFreshnessInCards = MutableStateFlow(false)

    fun observeOverview(): Flow<HomeOverviewSnapshot> {
        val storedOverviewFlow = buildHomeOverviewStoreRefreshFlow(
            refreshRequests = refreshRequests,
            githubVersions = GitHubTrackStoreSignals.version,
            baVersions = BASettingsStoreSignals.version
        )
            .onStart { emit("initial") }
            .map { reason ->
                loadStoredOverview(reason)
            }
        return combine(
            mcpUiState,
            storedOverviewFlow,
            visibleOverviewCards,
            showCacheFreshnessInCards
        ) { mcpState, storedOverview, visibleCards, showCacheFreshness ->
            HomeOverviewSnapshot(
                mcpOverview = mcpState.toHomeOverview(),
                githubOverview = storedOverview.githubOverview,
                baOverview = storedOverview.baOverview,
                visibleOverviewCards = visibleCards,
                showCacheFreshnessInCards = showCacheFreshness
            )
        }.distinctUntilChanged()
    }

    fun requestRefresh(reason: String) {
        refreshRequests.tryEmit(reason)
    }

    suspend fun setOverviewCardVisible(card: HomeOverviewCard, visible: Boolean) {
        val updated = visibleOverviewCards.value.toMutableSet().apply {
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

    private suspend fun loadStoredOverview(reason: String): StoredHomeOverview {
        return withContext(ioDispatcher) {
            val visibleCards = runCatching { HomeOverviewPrefs.loadVisibleOverviewCards() }
                .onFailure { error ->
                    AppLogger.w(
                        TAG,
                        "loadHomeVisibleOverviewCards failed (reason=$reason)",
                        error
                    )
                }
                .getOrElse { defaultHomeOverviewCards() }
            visibleOverviewCards.value = visibleCards
            showCacheFreshnessInCards.value = HomeOverviewPrefs.loadCacheFreshnessVisibleInCards()
            val cacheFreshnessById = loadHomeCacheFreshnessById(appContext)

            val baOverview = runCatching {
                loadHomeBaOverview(cacheFreshnessById["ba_calendar"] ?: CacheFreshnessSnapshot.Empty)
            }
                .onFailure { error ->
                    AppLogger.w(
                        TAG,
                        "loadHomeBaOverview failed (reason=$reason)",
                        error
                    )
                }
                .getOrElse { HomeBaOverview(loaded = true) }
            val githubOverview = runCatching {
                loadHomeGitHubOverview(cacheFreshnessById["github"] ?: CacheFreshnessSnapshot.Empty)
            }
                .onFailure { error ->
                    AppLogger.w(
                        TAG,
                        "loadHomeGitHubOverview failed (reason=$reason)",
                        error
                    )
                }
                .getOrElse { HomeGitHubOverview(loaded = true) }
            StoredHomeOverview(
                githubOverview = githubOverview,
                baOverview = baOverview
            )
        }
    }

    private fun McpServerUiState.toHomeOverview(): HomeMcpOverview {
        return HomeMcpOverview(
            running = running,
            runningSinceEpochMs = runningSinceEpochMs,
            port = port,
            endpointPath = endpointPath,
            serverName = serverName,
            authTokenConfigured = authToken.isNotBlank(),
            authTokenPreview = buildTokenPreview(authToken),
            connectedClients = connectedClients,
            allowExternal = allowExternal
        )
    }

    private data class StoredHomeOverview(
        val githubOverview: HomeGitHubOverview,
        val baOverview: HomeBaOverview
    )
}

private fun buildTokenPreview(token: String): String {
    val trimmed = token.trim()
    if (trimmed.isBlank()) return ""
    if (trimmed.length <= 4) return trimmed
    return "${trimmed.take(2)}…${trimmed.takeLast(2)}"
}

internal fun buildHomeOverviewStoreRefreshFlow(
    refreshRequests: Flow<String>,
    githubVersions: Flow<Long>,
    baVersions: Flow<Long>
): Flow<String> {
    return merge(
        refreshRequests,
        githubVersions
            .drop(1)
            .map { version -> "github_store_$version" },
        baVersions
            .drop(1)
            .map { version -> "ba_store_$version" }
    )
}

private fun loadHomeGitHubOverview(cacheFreshness: CacheFreshnessSnapshot): HomeGitHubOverview {
    val snapshot = GitHubTrackStore.loadSnapshot()
    val activeStrategyId = snapshot.lookupConfig.selectedStrategy.storageId
    val matchedCacheByTrackId = snapshot.items.associate { item ->
        val cache = snapshot.checkCache[item.id]
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
        failedCount = matchedCacheByTrackId.count { (_, entry) ->
            entry?.message?.let(GitHubTrackedReleaseStatus::isFailureMessage) == true
        },
        strategy = snapshot.lookupConfig.selectedStrategy,
        apiTokenConfigured = snapshot.lookupConfig.apiToken.isNotBlank(),
        shareImportLinkageEnabled = snapshot.lookupConfig.shareImportLinkageEnabled,
        pendingShareImport = snapshot.pendingShareImportTrack != null,
        refreshIntervalHours = snapshot.refreshIntervalHours,
        cachedRefreshMs = if (cacheHitCount > 0) snapshot.lastRefreshMs else 0L,
        cacheFreshness = cacheFreshness,
        loaded = true
    )
}

private fun loadHomeBaOverview(cacheFreshness: CacheFreshnessSnapshot): HomeBaOverview {
    val snapshot = BASettingsStore.loadSnapshot()
    val activated = snapshot.idFriendCode != HOME_BA_DEFAULT_FRIEND_CODE
    val apCurrent = snapshot.apCurrent.coerceIn(0.0, HOME_BA_AP_MAX.toDouble()).toInt()
    val cafeLevel = snapshot.cafeLevel.coerceIn(1, HOME_BA_CAFE_DAILY_AP_BY_LEVEL.size)
    val cafeCap = HOME_BA_CAFE_DAILY_AP_BY_LEVEL[cafeLevel - 1]
    val cafeStored = snapshot.cafeStoredAp.coerceAtLeast(0.0).toInt().coerceAtMost(cafeCap)

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
        loaded = true
    )
}

private fun loadHomeCacheFreshnessById(context: Context): Map<String, CacheFreshnessSnapshot> {
    return runCatching {
        CacheStores.list(context).associate { entry -> entry.id to entry.freshness }
    }.getOrNull().orEmpty()
}
