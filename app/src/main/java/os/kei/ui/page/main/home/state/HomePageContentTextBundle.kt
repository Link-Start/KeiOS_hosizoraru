package os.kei.ui.page.main.home.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
    val webDavTitle: String,
    val shizukuTitle: String,
    val mcpCardTitle: String,
    val githubCardTitle: String,
    val webDavCardTitle: String,
    val baCardTitle: String,
    val loading: String,
    val githubUnconfigured: String,
    val githubNoCache: String,
    val githubPendingRefresh: String,
    val githubSharePending: String,
    val githubRefreshing: String,
    val justNow: String,
    val githubNotRefreshed: String,
    val commonFilled: String,
    val commonNotUsed: String,
    val mcpRuntimePending: String,
    val baStatusActive: String,
    val baStatusInactive: String,
    val webDavUnconfigured: String,
    val webDavAutoSync: String,
    val webDavManualSync: String,
    val webDavNeverSynced: String,
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
    val statSyncItems: String,
    val statLastFullSync: String,
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
    private val githubRefreshingProgressPattern: String,
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

    fun githubRefreshingProgress(
        completed: Int,
        target: Int,
        totalTracked: Int,
    ): String = githubRefreshingProgressPattern.formatLocalized(completed, target, totalTracked)

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
internal fun rememberHomePageContentTextBundle(): HomePageContentTextBundle =
    HomePageContentTextBundle(
        commonNa = stringResource(R.string.common_na),
        appName = stringResource(R.string.app_name),
        tagline = stringResource(R.string.home_header_tagline),
        mcpTitle = stringResource(R.string.page_mcp_title),
        githubTitle = stringResource(R.string.github_page_title),
        webDavTitle = stringResource(R.string.home_status_webdav),
        shizukuTitle = stringResource(R.string.home_status_shizuku),
        mcpCardTitle = stringResource(R.string.home_card_title_mcp),
        githubCardTitle = stringResource(R.string.home_card_title_github_cache),
        webDavCardTitle = stringResource(R.string.home_card_title_webdav),
        baCardTitle = stringResource(R.string.home_card_title_ba),
        loading = stringResource(R.string.home_status_loading),
        githubUnconfigured = stringResource(R.string.home_github_status_unconfigured),
        githubNoCache = stringResource(R.string.home_github_status_no_cache),
        githubPendingRefresh = stringResource(R.string.home_github_status_pending_refresh),
        githubSharePending = stringResource(R.string.home_github_share_pending),
        githubRefreshing = stringResource(R.string.home_github_status_refreshing),
        justNow = stringResource(R.string.home_time_just_now),
        githubNotRefreshed = stringResource(R.string.github_refresh_ago_not_refreshed),
        commonFilled = stringResource(R.string.common_filled),
        commonNotUsed = stringResource(R.string.common_not_used),
        mcpRuntimePending = stringResource(R.string.mcp_runtime_pending),
        baStatusActive = stringResource(R.string.home_ba_status_active),
        baStatusInactive = stringResource(R.string.home_ba_status_inactive),
        webDavUnconfigured = stringResource(R.string.home_webdav_status_unconfigured),
        webDavAutoSync = stringResource(R.string.home_webdav_status_auto_sync),
        webDavManualSync = stringResource(R.string.home_webdav_status_manual_sync),
        webDavNeverSynced = stringResource(R.string.home_webdav_last_sync_never),
        valueOn = stringResource(R.string.home_value_on),
        valueOff = stringResource(R.string.home_value_off),
        valueAuthorized = stringResource(R.string.home_value_authorized),
        valueUnauthorized = stringResource(R.string.home_value_unauthorized),
        cacheStateFresh = stringResource(R.string.home_cache_state_fresh),
        cacheStateStale = stringResource(R.string.home_cache_state_stale),
        cacheStateEmpty = stringResource(R.string.home_cache_state_empty),
        appVersionUnknownFallback = stringResource(R.string.home_app_version_unknown_fallback),
        appVersionUnknown = stringResource(R.string.home_app_version_unknown),
        networkLanShort = stringResource(R.string.mcp_network_mode_lan_short),
        networkLocalOnlyShort = stringResource(R.string.mcp_network_mode_local_only_short),
        mcpStatusRunning = stringResource(R.string.home_mcp_status_running),
        mcpStatusStopped = stringResource(R.string.home_mcp_status_stopped),
        statStatus = stringResource(R.string.home_stat_status),
        statRuntime = stringResource(R.string.home_stat_runtime),
        statClients = stringResource(R.string.home_stat_clients),
        statNetwork = stringResource(R.string.home_stat_network),
        statPort = stringResource(R.string.home_stat_port),
        statToken = stringResource(R.string.home_stat_token),
        statStableUpdates = stringResource(R.string.home_stat_stable_updates),
        statPreReleaseUpdates = stringResource(R.string.home_stat_prerelease_updates),
        statFailed = stringResource(R.string.home_stat_failed),
        statTracked = stringResource(R.string.home_stat_tracked),
        statCached = stringResource(R.string.home_stat_cached),
        statCacheState = stringResource(R.string.home_stat_cache_state),
        statShare = stringResource(R.string.home_stat_share),
        statLastUpdate = stringResource(R.string.home_stat_last_update),
        statSyncItems = stringResource(R.string.home_stat_sync_items),
        statLastFullSync = stringResource(R.string.home_stat_last_full_sync),
        statAp = stringResource(R.string.home_stat_ap),
        statCafeAp = stringResource(R.string.home_stat_cafe_ap),
        statApRemaining = stringResource(R.string.home_stat_ap_remaining),
        statBaServer = stringResource(R.string.home_stat_ba_server),
        statBaNotify = stringResource(R.string.home_stat_ba_notify),
        baServerCn = stringResource(R.string.home_ba_server_cn),
        baServerGlobal = stringResource(R.string.home_ba_server_global),
        baServerJp = stringResource(R.string.home_ba_server_jp),
        uptimeDaysHoursPattern = stringResource(R.string.mcp_uptime_days_hours),
        uptimeDaysHoursMinutesPattern = stringResource(R.string.mcp_uptime_days_hours_minutes),
        uptimeHoursPattern = stringResource(R.string.mcp_uptime_hours),
        uptimeHoursMinutesPattern = stringResource(R.string.mcp_uptime_hours_minutes),
        uptimeMinutesPattern = stringResource(R.string.mcp_uptime_minutes),
        githubCountPattern = stringResource(R.string.github_overview_value_count),
        shortHoursPattern = stringResource(R.string.home_value_short_hours),
        refreshPairPattern = stringResource(R.string.home_value_refresh_pair),
        githubRefreshingProgressPattern = stringResource(R.string.home_github_refreshing_progress),
        devicesCountPattern = stringResource(R.string.home_value_devices_count),
        failedCountPattern = stringResource(R.string.home_value_failed_count),
        fractionPattern = stringResource(R.string.home_value_fraction),
        cafeFractionPattern = stringResource(R.string.home_value_cafe_fraction),
        thresholdPlusPattern = stringResource(R.string.home_value_threshold_plus),
    )

private fun String.formatLocalized(vararg args: Any): String = String.format(Locale.getDefault(), this, *args)
