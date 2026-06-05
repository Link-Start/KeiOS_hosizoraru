package os.kei.ui.page.main.sync

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.feature.webdav.model.WebDavConfig

/**
 * Data boundary for the WebDAV sync page.
 *
 * The page-level ViewModel owns user intent and state reduction. Store reads, JSON counting,
 * WebDAV engine calls, and metadata rebuilding stay here so expensive sync data work can run on
 * a bounded dispatcher instead of competing with Compose's first-frame rendering.
 */
internal class WebDavSyncRepository(
    private val engine: WebDavSyncEngine = WebDavSyncEngine(),
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.fileIo,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    fun buildInitialState(): WebDavSyncUiState {
        val config = WebDavSyncStore.loadConfig()
        val provider = WebDavSyncStore.loadProvider()
        return WebDavSyncUiState(
            provider = provider,
            serverUrl = config?.serverUrl ?: provider.presetServerUrl.orEmpty(),
            username = config?.username.orEmpty(),
            appPassword = config?.appPassword.orEmpty(),
            remoteDir = config?.remoteDir ?: provider.defaultRemoteDir,
            isConfigured = config != null,
            autoSyncEnabled = WebDavSyncStore.isAutoSyncEnabled(),
            lastFullSyncTimeMs = WebDavSyncStore.getLastFullSyncTime(),
            lastRemoteProbeTimeMs = WebDavSyncStore.getLastRemoteProbeTime(),
            itemStates = buildItemStates(),
        )
    }

    suspend fun saveConfig(
        provider: WebDavProvider,
        serverUrl: String,
        username: String,
        appPassword: String,
        remoteDir: String,
    ): WebDavConfig =
        withContext(ioDispatcher) {
            provider.buildConfig(serverUrl, username, appPassword, remoteDir).also { config ->
                WebDavSyncStore.saveConfig(config, provider)
                engine.invalidate()
            }
        }

    suspend fun clearConfig(): WebDavSyncUiState =
        withContext(ioDispatcher) {
            WebDavSyncStore.clearConfig()
            engine.invalidate()
            buildInitialState()
        }

    suspend fun setAutoSyncEnabled(enabled: Boolean) =
        withContext(ioDispatcher) {
            WebDavSyncStore.setAutoSyncEnabled(enabled)
        }

    suspend fun setItemEnabled(
        item: WebDavSyncItem,
        enabled: Boolean,
        previous: Map<WebDavSyncItem, WebDavSyncItemUiState>,
    ): Map<WebDavSyncItem, WebDavSyncItemUiState> =
        withContext(ioDispatcher) {
            WebDavSyncStore.setItemEnabled(item, enabled)
            buildItemStates(previous)
        }

    suspend fun loadConfig(): WebDavConfig? =
        withContext(ioDispatcher) {
            WebDavSyncStore.loadConfig()
        }

    suspend fun loadEnabledItems(): List<WebDavSyncItem> =
        withContext(ioDispatcher) {
            WebDavSyncItem.entries.filter { WebDavSyncStore.isItemEnabled(it) }
        }

    suspend fun isItemEnabled(item: WebDavSyncItem): Boolean =
        withContext(ioDispatcher) {
            WebDavSyncStore.isItemEnabled(item)
        }

    suspend fun testConnection(config: WebDavConfig): WebDavConnectionOutcome =
        withContext(ioDispatcher) {
            engine.testConnection(config)
        }

    suspend fun invoke(
        kind: WebDavBatchKind,
        config: WebDavConfig,
        item: WebDavSyncItem,
        port: WebDavSyncDataPort,
        planItem: WebDavSyncPlanItem? = null,
    ): WebDavItemOutcome =
        withContext(ioDispatcher) {
            when (kind) {
                WebDavBatchKind.Sync -> engine.sync(config, item, port)
                WebDavBatchKind.Upload ->
                    engine.upload(
                        config = config,
                        item = item,
                        port = port,
                        expectedRemoteEtag =
                            (planItem?.remoteState as? WebDavSyncPlanRemoteState.Found)?.etag,
                        remoteKnownEmpty = planItem?.remoteState == WebDavSyncPlanRemoteState.Empty,
                    )
                WebDavBatchKind.Download -> engine.download(config, item, port)
            }
        }

    suspend fun preparePlan(
        kind: WebDavBatchKind,
        scope: WebDavSyncPlanScope,
        config: WebDavConfig,
        targets: List<WebDavSyncItem>,
        dataPorts: Map<WebDavSyncItem, WebDavSyncDataPort>,
    ): WebDavSyncPlan =
        withContext(ioDispatcher) {
            val items =
                targets.mapNotNull { item ->
                    val port = dataPorts[item] ?: return@mapNotNull null
                    engine.prepareChange(
                        config = config,
                        kind = kind,
                        item = item,
                        port = port,
                    )
                }
            val probedAtMs = nowMs()
            WebDavSyncStore.setLastRemoteProbeTime(probedAtMs)
            WebDavSyncPlan(
                kind = kind,
                scope = scope,
                createdAtMs = probedAtMs,
                items = items,
            )
        }

    suspend fun probeRemote(
        config: WebDavConfig,
        item: WebDavSyncItem,
        port: WebDavSyncDataPort,
    ): WebDavRemoteProbeResult =
        withContext(ioDispatcher) {
            val outcome = engine.probeRemote(config, item, port)
            WebDavRemoteProbeResult(
                outcome = outcome,
                summary = WebDavSyncStore.loadRemoteSummary(item),
            )
        }

    suspend fun recordRemoteProbeBatch(): Long =
        withContext(ioDispatcher) {
            WebDavSyncStore.setLastRemoteProbeTime(nowMs())
            WebDavSyncStore.getLastRemoteProbeTime()
        }

    suspend fun recordFullSyncBatch(): Long =
        withContext(ioDispatcher) {
            WebDavSyncStore.setLastFullSyncTime(nowMs())
            WebDavSyncStore.getLastFullSyncTime()
        }

    suspend fun loadItemStates(
        previous: Map<WebDavSyncItem, WebDavSyncItemUiState>,
    ): Map<WebDavSyncItem, WebDavSyncItemUiState> =
        withContext(ioDispatcher) {
            buildItemStates(previous)
        }

    suspend fun loadLastSyncTime(item: WebDavSyncItem): Long =
        withContext(ioDispatcher) {
            WebDavSyncStore.getLastSyncTime(item)
        }

    suspend fun loadLocalCounts(
        dataPorts: Map<WebDavSyncItem, WebDavSyncDataPort>,
    ): Map<WebDavSyncItem, Int> =
        withContext(ioDispatcher) {
            dataPorts.mapValues { (_, port) ->
                runCatching { port.localCount() }.getOrDefault(-1)
            }
        }

    fun buildItemStates(
        previous: Map<WebDavSyncItem, WebDavSyncItemUiState> = emptyMap(),
    ): Map<WebDavSyncItem, WebDavSyncItemUiState> =
        WebDavSyncItem.entries.associateWith { item ->
            WebDavSyncItemUiState(
                enabled = WebDavSyncStore.isItemEnabled(item),
                running = false,
                lastSyncTimeMs = WebDavSyncStore.getLastSyncTime(item),
                lastOutcome = previous[item]?.lastOutcome,
                remoteSummary = WebDavSyncStore.loadRemoteSummary(item),
                localCount = previous[item]?.localCount ?: -1,
                remoteProbeError = previous[item]?.remoteProbeError,
            )
        }

    fun defaultItemState(item: WebDavSyncItem): WebDavSyncItemUiState =
        WebDavSyncItemUiState(
            enabled = WebDavSyncStore.isItemEnabled(item),
            lastSyncTimeMs = WebDavSyncStore.getLastSyncTime(item),
            remoteSummary = WebDavSyncStore.loadRemoteSummary(item),
        )
}

internal data class WebDavRemoteProbeResult(
    val outcome: WebDavRemoteProbeOutcome,
    val summary: WebDavRemoteSummary?,
)
