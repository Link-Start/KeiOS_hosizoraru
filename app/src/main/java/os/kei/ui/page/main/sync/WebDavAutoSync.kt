package os.kei.ui.page.main.sync

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import os.kei.core.log.AppLogger
import os.kei.feature.webdav.model.WebDavConfig
import java.util.concurrent.atomic.AtomicInteger

/**
 * Application-scoped WebDAV auto-sync coordinator.
 *
 * Two trigger points (matching the user's chosen "Auto on launch + on change" model):
 *  1. **Launch sync** — when [init] is called from [Application.onCreate], if a config exists and
 *     auto-sync is enabled, run one full pull-merge-push pass for every enabled item.
 *  2. **Push-on-background** — when the app moves to background (last foreground Activity stops),
 *     re-export every enabled item, compute its content hash, and push only those whose hash has
 *     drifted from the last persisted [WebDavSyncStore.getItemContentHash]. This catches local
 *     edits that happened during the foreground session without requiring per-store change
 *     listeners (the stores that lack change signals are the reason "on-change" had to be moved
 *     here from the original plan).
 *
 * All work runs on [Dispatchers.IO] under a single [SupervisorJob] so individual item failures
 * never tear down the whole pass. Concurrent triggers are serialised through [mutex] so two
 * lifecycle-driven passes never race the same engine + store state.
 */
internal object WebDavAutoSync {
    private const val TAG = "WebDavAutoSync"

    /** Wait this long after the app backgrounds before pushing — avoids thrashing on quick swaps. */
    private const val BACKGROUND_PUSH_DELAY_MS = 800L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val engine = WebDavSyncEngine()
    private val mutex = Mutex()
    private val foregroundCount = AtomicInteger(0)
    private var pendingBackgroundJob: Job? = null
    private var initialized = false
    private lateinit var appContext: Context

    /**
     * Wire the coordinator into the application lifecycle. Call once from
     * [Application.onCreate]. Subsequent calls are no-ops.
     */
    fun init(application: Application) {
        if (initialized) return
        initialized = true
        appContext = application.applicationContext
        application.registerActivityLifecycleCallbacks(LifecycleObserver)
        scope.launch { runLaunchSync() }
    }

    /** First app start (cold launch) → one full sync if config + auto-sync allow it. */
    private suspend fun runLaunchSync() {
        val config = WebDavSyncStore.loadConfig() ?: return
        if (!WebDavSyncStore.isAutoSyncEnabled()) return
        runFullSync(config, reason = "launch")
    }

    /** App moved to background → push the items whose local content has drifted since last sync. */
    private fun schedulePushIfChanged() {
        pendingBackgroundJob?.cancel()
        pendingBackgroundJob = scope.launch {
            delay(BACKGROUND_PUSH_DELAY_MS)
            val config = WebDavSyncStore.loadConfig() ?: return@launch
            if (!WebDavSyncStore.isAutoSyncEnabled()) return@launch
            pushChangedItems(config)
        }
    }

    private suspend fun runFullSync(config: WebDavConfig, reason: String) = mutex.withLock {
        runCatching {
            val ports = buildWebDavSyncDataPorts(appContext)
            val targets = WebDavSyncItem.entries.filter { WebDavSyncStore.isItemEnabled(it) }
            for (item in targets) {
                val port = ports[item] ?: continue
                val outcome = engine.sync(config, item, port)
                if (!outcome.isSuccess) {
                    AppLogger.w(TAG, "auto-sync ($reason) ${item.name} → ${outcome.status} ${outcome.detail.orEmpty()}")
                }
            }
            WebDavSyncStore.setLastFullSyncTime(System.currentTimeMillis())
        }.onFailure { AppLogger.w(TAG, "auto-sync ($reason) failed", it) }
    }

    private suspend fun pushChangedItems(config: WebDavConfig) = mutex.withLock {
        runCatching {
            val ports = buildWebDavSyncDataPorts(appContext)
            val targets = WebDavSyncItem.entries.filter { WebDavSyncStore.isItemEnabled(it) }
            for (item in targets) {
                val port = ports[item] ?: continue
                val currentHash = WebDavSyncEngine.contentHash(port.exportJson())
                val storedHash = WebDavSyncStore.getItemContentHash(item)
                if (currentHash == storedHash) continue
                val outcome = engine.sync(config, item, port)
                if (!outcome.isSuccess) {
                    AppLogger.w(TAG, "auto-push ${item.name} → ${outcome.status} ${outcome.detail.orEmpty()}")
                }
            }
        }.onFailure { AppLogger.w(TAG, "auto-push failed", it) }
    }

    private object LifecycleObserver : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) {
            // Reaching foreground → cancel any pending push so we don't fire while the user is back.
            if (foregroundCount.getAndIncrement() == 0) {
                pendingBackgroundJob?.cancel()
                pendingBackgroundJob = null
            }
        }
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) {
            if (foregroundCount.decrementAndGet() == 0) {
                schedulePushIfChanged()
            }
        }
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}
