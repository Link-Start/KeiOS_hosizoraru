package os.kei

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.StatFs
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.gif.AnimatedImageDecoder
import coil3.memory.MemoryCache
import coil3.request.allowHardware
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okio.Path.Companion.toOkioPath
import os.kei.core.background.AppBackgroundScheduler
import os.kei.core.log.AppLogger
import os.kei.core.perf.Android17AnomalyProfiler
import os.kei.core.system.AppPackageChangedEvent
import os.kei.core.system.AppPackageChangedEvents
import os.kei.feature.github.data.remote.GitHubVersionUtils
import os.kei.ui.page.main.github.share.GitHubShareImportFlowCoordinator
import os.kei.ui.page.main.github.share.GitHubShareImportPendingScheduler

private const val COIL_MEMORY_CACHE_PERCENT = 0.25
private const val COIL_DISK_CACHE_DEFAULT_BYTES = 96L * 1024L * 1024L
private const val COIL_DISK_CACHE_MAX_BYTES = 192L * 1024L * 1024L
private const val COIL_DISK_CACHE_FREE_SPACE_RATIO = 0.02
private const val COIL_DISK_CACHE_DIR = "coil_image_cache"

class KeiOSApp : Application() {
    companion object {
        @Volatile
        private lateinit var instance: KeiOSApp

        val appContext: Application
            get() = instance
    }

    /**
     * Application-scoped supervisor for non-UI background work that should outlive any single
     * Activity but must still cancel when the process is torn down. Use [Dispatchers.Default] so
     * launching warm-up work does not steal the IO pool from in-flight network calls.
     */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val packageChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_PACKAGE_ADDED,
                Intent.ACTION_PACKAGE_REMOVED,
                Intent.ACTION_PACKAGE_REPLACED,
                Intent.ACTION_PACKAGE_CHANGED,
                Intent.ACTION_PACKAGE_FULLY_REMOVED -> {
                    val pkg = intent.data?.schemeSpecificPart?.trim().orEmpty()
                    if (pkg.isNotBlank()) {
                        val event = AppPackageChangedEvent(
                            packageName = pkg,
                            action = intent.action.orEmpty(),
                            replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                        )
                        AppPackageChangedEvents.publish(event)
                        GitHubShareImportFlowCoordinator.handlePackageChangedAsync(
                            this@KeiOSApp,
                            event
                        )
                    }
                    GitHubVersionUtils.invalidateInstalledLaunchableAppsCache()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // First-frame critical path: keep this list as small as possible. Anything that touches
        // MMKV, AlarmManager, or scans tracked-app state should run via [applicationScope] below.
        MMKV.initialize(this)
        AppLogger.refreshLevelFromPrefs()
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .memoryCache {
                    MemoryCache.Builder()
                        .maxSizePercent(context, COIL_MEMORY_CACHE_PERCENT)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(context.cacheDir.resolve(COIL_DISK_CACHE_DIR).toOkioPath())
                        .maxSizeBytes(resolveCoilDiskCacheBytes(context))
                        .build()
                }
                .allowHardware(true)
                .components {
                    add(AnimatedImageDecoder.Factory())
                }
                .build()
        }
        Android17AnomalyProfiler.install(this)
        registerPackageChangedReceiver()
        scheduleDeferredStartupWork()
    }

    private fun scheduleDeferredStartupWork() {
        // AlarmManager wiring and pending share-import scheduling both read from MMKV and only feed
        // background notification flows. Push them off the critical path so the first Compose frame
        // is not delayed by tracked-app fan-out work.
        applicationScope.launch {
            runCatching { AppBackgroundScheduler.scheduleAll(this@KeiOSApp) }
            runCatching { GitHubShareImportPendingScheduler.scheduleNext(this@KeiOSApp) }
        }
    }

    private fun registerPackageChangedReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
            addDataScheme("package")
        }
        registerReceiver(packageChangedReceiver, filter, RECEIVER_NOT_EXPORTED)
    }

    /**
     * Adaptive Coil 3 disk-cache budget. Picks ~2% of free cache-partition space and clamps to
     * [COIL_DISK_CACHE_DEFAULT_BYTES]…[COIL_DISK_CACHE_MAX_BYTES] so low-storage devices never
     * thrash on cache trim while high-storage devices still get the full Coil benefits.
     */
    private fun resolveCoilDiskCacheBytes(context: Context): Long {
        val available = runCatching {
            val stat = StatFs(context.cacheDir.absolutePath)
            (stat.availableBytes * COIL_DISK_CACHE_FREE_SPACE_RATIO).toLong()
        }.getOrDefault(COIL_DISK_CACHE_DEFAULT_BYTES)
        return available.coerceIn(COIL_DISK_CACHE_DEFAULT_BYTES, COIL_DISK_CACHE_MAX_BYTES)
    }
}
