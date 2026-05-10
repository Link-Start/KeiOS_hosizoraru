package os.kei

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import com.tencent.mmkv.MMKV
import org.lsposed.hiddenapibypass.HiddenApiBypass
import os.kei.core.background.AppBackgroundScheduler
import os.kei.core.log.AppLogger
import os.kei.core.perf.Android17AnomalyProfiler
import os.kei.core.system.AppPackageChangedEvent
import os.kei.core.system.AppPackageChangedEvents
import os.kei.feature.github.data.remote.GitHubVersionUtils
import os.kei.ui.page.main.github.share.GitHubShareImportFlowCoordinator
import os.kei.ui.page.main.github.share.GitHubShareImportPendingScheduler

class KeiOSApp : Application() {
    companion object {
        @Volatile
        private lateinit var instance: KeiOSApp

        val appContext: Application
            get() = instance
    }

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
        HiddenApiBypass.addHiddenApiExemptions("")
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .components {
                    add(AnimatedImageDecoder.Factory())
                }
                .build()
        }
        MMKV.initialize(this)
        AppLogger.refreshEnabledFromPrefs()
        Android17AnomalyProfiler.install(this)
        AppBackgroundScheduler.scheduleAll(this)
        GitHubShareImportPendingScheduler.scheduleNext(this)
        registerPackageChangedReceiver()
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
}
