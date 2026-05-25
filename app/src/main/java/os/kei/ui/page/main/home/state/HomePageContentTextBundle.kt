package os.kei.ui.page.main.home.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import os.kei.R
import java.util.Locale

@Immutable
internal data class HomePageContentTextBundle(
    val commonNa: String,
    val appName: String,
    val tagline: String,
    val mcpTitle: String,
    val githubTitle: String,
    val baTitle: String,
    val shizukuTitle: String,
    val mcpCardTitle: String,
    val githubCardTitle: String,
    val baCardTitle: String,
    val loading: String,
    val githubUnconfigured: String,
    val githubNoCache: String,
    val githubPendingRefresh: String,
    val githubSharePending: String,
    val justNow: String,
    val githubNotRefreshed: String,
    val commonFilled: String,
    val commonNotUsed: String,
    val mcpRuntimePending: String,
    val baStatusActive: String,
    val baStatusInactive: String,
    val valueOn: String,
    val valueOff: String,
    val valueAuthorized: String,
    val valueUnauthorized: String,
    val cacheStateFresh: String,
    val cacheStateStale: String,
    val cacheStateEmpty: String,
    val networkLanShort: String,
    val networkLocalOnlyShort: String,
    val mcpStatusRunning: String,
    val mcpStatusStopped: String,
    val statStatus: String,
    val statRuntime: String,
    val statClients: String,
    val statNetwork: String,
    val statPort: String,
    val statToken: String,
    val statStableUpdates: String,
    val statPreReleaseUpdates: String,
    val statFailed: String,
    val statTracked: String,
    val statCached: String,
    val statCacheState: String,
    val statShare: String,
    val statLastUpdate: String,
    val statAp: String,
    val statCafeAp: String,
    val statApRemaining: String,
    val statBaServer: String,
    val statBaNotify: String,
    val baServerCn: String,
    val baServerGlobal: String,
    val baServerJp: String,
    val appVersionUnknownFallback: String,
    val appVersionUnknown: String,
    private val uptimeDaysHoursPattern: String,
    private val uptimeDaysHoursMinutesPattern: String,
    private val uptimeHoursPattern: String,
    private val uptimeHoursMinutesPattern: String,
    private val uptimeMinutesPattern: String,
    private val githubCountPattern: String,
    private val shortHoursPattern: String,
    private val refreshPairPattern: String,
    private val devicesCountPattern: String,
    private val failedCountPattern: String,
    private val fractionPattern: String,
    private val cafeFractionPattern: String,
    private val thresholdPlusPattern: String,
) {
    fun githubCount(count: Int): String = githubCountPattern.formatLocalized(count)

    fun shortHours(hours: Int): String = shortHoursPattern.formatLocalized(hours)

    fun refreshPair(
        interval: String,
        ago: String,
    ): String = refreshPairPattern.formatLocalized(interval, ago)

    fun devicesCount(count: Int): String = devicesCountPattern.formatLocalized(count)

    fun failedCount(count: Int): String = failedCountPattern.formatLocalized(count)

    fun fraction(
        current: Int,
        limit: Int,
    ): String = fractionPattern.formatLocalized(current, limit)

    fun cafeFraction(
        level: Int,
        stored: Int,
        cap: Int,
    ): String = cafeFractionPattern.formatLocalized(level, stored, cap)

    fun thresholdPlus(value: Int): String = thresholdPlusPattern.formatLocalized(value)

    fun uptime(durationMs: Long): String {
        val totalMinutes = durationMs.coerceAtLeast(0L) / 60_000L
        val days = totalMinutes / 1_440L
        val hours = (totalMinutes % 1_440L) / 60L
        val minutes = totalMinutes % 60L
        return when {
            days > 0L && minutes == 0L -> uptimeDaysHoursPattern.formatLocalized(days, hours)
            days > 0L -> uptimeDaysHoursMinutesPattern.formatLocalized(days, hours, minutes)
            hours > 0L && minutes == 0L -> uptimeHoursPattern.formatLocalized(hours)
            hours > 0L -> uptimeHoursMinutesPattern.formatLocalized(hours, minutes)
            else -> uptimeMinutesPattern.formatLocalized(minutes)
        }
    }
}

@Composable
internal fun rememberHomePageContentTextBundle(): HomePageContentTextBundle {
    val commonNa = stringResource(R.string.common_na)
    val appName = stringResource(R.string.app_name)
    val tagline = stringResource(R.string.home_header_tagline)
    val mcpTitle = stringResource(R.string.page_mcp_title)
    val githubTitle = stringResource(R.string.github_page_title)
    val baTitle = stringResource(R.string.home_status_ba)
    val shizukuTitle = stringResource(R.string.home_status_shizuku)
    val mcpCardTitle = stringResource(R.string.home_card_title_mcp)
    val githubCardTitle = stringResource(R.string.home_card_title_github_cache)
    val baCardTitle = stringResource(R.string.home_card_title_ba)
    val loading = stringResource(R.string.home_status_loading)
    val githubUnconfigured = stringResource(R.string.home_github_status_unconfigured)
    val githubNoCache = stringResource(R.string.home_github_status_no_cache)
    val githubPendingRefresh = stringResource(R.string.home_github_status_pending_refresh)
    val githubSharePending = stringResource(R.string.home_github_share_pending)
    val justNow = stringResource(R.string.home_time_just_now)
    val githubNotRefreshed = stringResource(R.string.github_refresh_ago_not_refreshed)
    val commonFilled = stringResource(R.string.common_filled)
    val commonNotUsed = stringResource(R.string.common_not_used)
    val mcpRuntimePending = stringResource(R.string.mcp_runtime_pending)
    val baStatusActive = stringResource(R.string.home_ba_status_active)
    val baStatusInactive = stringResource(R.string.home_ba_status_inactive)
    val valueOn = stringResource(R.string.home_value_on)
    val valueOff = stringResource(R.string.home_value_off)
    val valueAuthorized = stringResource(R.string.home_value_authorized)
    val valueUnauthorized = stringResource(R.string.home_value_unauthorized)
    val cacheStateFresh = stringResource(R.string.home_cache_state_fresh)
    val cacheStateStale = stringResource(R.string.home_cache_state_stale)
    val cacheStateEmpty = stringResource(R.string.home_cache_state_empty)
    val appVersionUnknownFallback = stringResource(R.string.home_app_version_unknown_fallback)
    val appVersionUnknown = stringResource(R.string.home_app_version_unknown)
    val networkLanShort = stringResource(R.string.mcp_network_mode_lan_short)
    val networkLocalOnlyShort = stringResource(R.string.mcp_network_mode_local_only_short)
    val mcpStatusRunning = stringResource(R.string.home_mcp_status_running)
    val mcpStatusStopped = stringResource(R.string.home_mcp_status_stopped)
    val statStatus = stringResource(R.string.home_stat_status)
    val statRuntime = stringResource(R.string.home_stat_runtime)
    val statClients = stringResource(R.string.home_stat_clients)
    val statNetwork = stringResource(R.string.home_stat_network)
    val statPort = stringResource(R.string.home_stat_port)
    val statToken = stringResource(R.string.home_stat_token)
    val statStableUpdates = stringResource(R.string.home_stat_stable_updates)
    val statPreReleaseUpdates = stringResource(R.string.home_stat_prerelease_updates)
    val statFailed = stringResource(R.string.home_stat_failed)
    val statTracked = stringResource(R.string.home_stat_tracked)
    val statCached = stringResource(R.string.home_stat_cached)
    val statCacheState = stringResource(R.string.home_stat_cache_state)
    val statShare = stringResource(R.string.home_stat_share)
    val statLastUpdate = stringResource(R.string.home_stat_last_update)
    val statAp = stringResource(R.string.home_stat_ap)
    val statCafeAp = stringResource(R.string.home_stat_cafe_ap)
    val statApRemaining = stringResource(R.string.home_stat_ap_remaining)
    val statBaServer = stringResource(R.string.home_stat_ba_server)
    val statBaNotify = stringResource(R.string.home_stat_ba_notify)
    val baServerCn = stringResource(R.string.home_ba_server_cn)
    val baServerGlobal = stringResource(R.string.home_ba_server_global)
    val baServerJp = stringResource(R.string.home_ba_server_jp)
    val uptimeDaysHoursPattern = stringResource(R.string.mcp_uptime_days_hours)
    val uptimeDaysHoursMinutesPattern = stringResource(R.string.mcp_uptime_days_hours_minutes)
    val uptimeHoursPattern = stringResource(R.string.mcp_uptime_hours)
    val uptimeHoursMinutesPattern = stringResource(R.string.mcp_uptime_hours_minutes)
    val uptimeMinutesPattern = stringResource(R.string.mcp_uptime_minutes)
    val githubCountPattern = stringResource(R.string.github_overview_value_count)
    val shortHoursPattern = stringResource(R.string.home_value_short_hours)
    val refreshPairPattern = stringResource(R.string.home_value_refresh_pair)
    val devicesCountPattern = stringResource(R.string.home_value_devices_count)
    val failedCountPattern = stringResource(R.string.home_value_failed_count)
    val fractionPattern = stringResource(R.string.home_value_fraction)
    val cafeFractionPattern = stringResource(R.string.home_value_cafe_fraction)
    val thresholdPlusPattern = stringResource(R.string.home_value_threshold_plus)

    return remember(
        commonNa, appName, tagline, mcpTitle, githubTitle, baTitle, shizukuTitle,
        mcpCardTitle, githubCardTitle, baCardTitle, loading,
        githubUnconfigured, githubNoCache, githubPendingRefresh, githubSharePending, justNow,
        githubNotRefreshed, commonFilled, commonNotUsed, mcpRuntimePending,
        baStatusActive, baStatusInactive, valueOn, valueOff, valueAuthorized, valueUnauthorized,
        cacheStateFresh, cacheStateStale, cacheStateEmpty, appVersionUnknownFallback, appVersionUnknown,
        networkLanShort, networkLocalOnlyShort, mcpStatusRunning, mcpStatusStopped,
        statStatus, statRuntime, statClients, statNetwork, statPort, statToken,
        statStableUpdates, statPreReleaseUpdates, statFailed, statTracked, statCached, statCacheState,
        statShare, statLastUpdate, statAp, statCafeAp, statApRemaining, statBaServer, statBaNotify,
        baServerCn, baServerGlobal, baServerJp,
        uptimeDaysHoursPattern, uptimeDaysHoursMinutesPattern, uptimeHoursPattern,
        uptimeHoursMinutesPattern, uptimeMinutesPattern,
        githubCountPattern, shortHoursPattern, refreshPairPattern, devicesCountPattern,
        failedCountPattern, fractionPattern, cafeFractionPattern, thresholdPlusPattern,
    ) {
        HomePageContentTextBundle(
            commonNa = commonNa,
            appName = appName,
            tagline = tagline,
            mcpTitle = mcpTitle,
            githubTitle = githubTitle,
            baTitle = baTitle,
            shizukuTitle = shizukuTitle,
            mcpCardTitle = mcpCardTitle,
            githubCardTitle = githubCardTitle,
            baCardTitle = baCardTitle,
            loading = loading,
            githubUnconfigured = githubUnconfigured,
            githubNoCache = githubNoCache,
            githubPendingRefresh = githubPendingRefresh,
            githubSharePending = githubSharePending,
            justNow = justNow,
            githubNotRefreshed = githubNotRefreshed,
            commonFilled = commonFilled,
            commonNotUsed = commonNotUsed,
            mcpRuntimePending = mcpRuntimePending,
            baStatusActive = baStatusActive,
            baStatusInactive = baStatusInactive,
            valueOn = valueOn,
            valueOff = valueOff,
            valueAuthorized = valueAuthorized,
            valueUnauthorized = valueUnauthorized,
            cacheStateFresh = cacheStateFresh,
            cacheStateStale = cacheStateStale,
            cacheStateEmpty = cacheStateEmpty,
            appVersionUnknownFallback = appVersionUnknownFallback,
            appVersionUnknown = appVersionUnknown,
            networkLanShort = networkLanShort,
            networkLocalOnlyShort = networkLocalOnlyShort,
            mcpStatusRunning = mcpStatusRunning,
            mcpStatusStopped = mcpStatusStopped,
            statStatus = statStatus,
            statRuntime = statRuntime,
            statClients = statClients,
            statNetwork = statNetwork,
            statPort = statPort,
            statToken = statToken,
            statStableUpdates = statStableUpdates,
            statPreReleaseUpdates = statPreReleaseUpdates,
            statFailed = statFailed,
            statTracked = statTracked,
            statCached = statCached,
            statCacheState = statCacheState,
            statShare = statShare,
            statLastUpdate = statLastUpdate,
            statAp = statAp,
            statCafeAp = statCafeAp,
            statApRemaining = statApRemaining,
            statBaServer = statBaServer,
            statBaNotify = statBaNotify,
            baServerCn = baServerCn,
            baServerGlobal = baServerGlobal,
            baServerJp = baServerJp,
            uptimeDaysHoursPattern = uptimeDaysHoursPattern,
            uptimeDaysHoursMinutesPattern = uptimeDaysHoursMinutesPattern,
            uptimeHoursPattern = uptimeHoursPattern,
            uptimeHoursMinutesPattern = uptimeHoursMinutesPattern,
            uptimeMinutesPattern = uptimeMinutesPattern,
            githubCountPattern = githubCountPattern,
            shortHoursPattern = shortHoursPattern,
            refreshPairPattern = refreshPairPattern,
            devicesCountPattern = devicesCountPattern,
            failedCountPattern = failedCountPattern,
            fractionPattern = fractionPattern,
            cafeFractionPattern = cafeFractionPattern,
            thresholdPlusPattern = thresholdPlusPattern,
        )
    }
}

private fun String.formatLocalized(vararg args: Any): String = String.format(Locale.getDefault(), this, *args)
