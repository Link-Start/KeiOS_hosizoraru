package os.kei.ui.page.main.github.share

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import os.kei.R
import os.kei.core.log.AppLogger
import os.kei.feature.github.data.local.GitHubShareImportFlowStore
import os.kei.feature.github.data.local.GitHubTrackStoreSignals
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.notification.GitHubShareImportNotificationHelper

private const val GITHUB_SHARE_IMPORT_DELIVERY_RUNNER_TAG = "GitHubShareImportDelivery"

internal object GitHubShareImportDeliveryRunner {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var activeJob: Job? = null

    fun launchActivePreviewDelivery(context: Context): Boolean {
        return launchDelivery(context = context)
    }

    fun launchSelectedPreviewDelivery(
        context: Context,
        preview: GitHubShareImportPreview,
        selectedAsset: GitHubReleaseAssetFile
    ): Boolean {
        return launchDelivery(
            context = context,
            selectedPreview = preview,
            selectedAsset = selectedAsset
        )
    }

    private fun launchDelivery(
        context: Context,
        selectedPreview: GitHubShareImportPreview? = null,
        selectedAsset: GitHubReleaseAssetFile? = null
    ): Boolean {
        val appContext = context.applicationContext
        synchronized(lock) {
            activeJob?.takeIf { it.isActive }?.let {
                GitHubTrackStoreSignals.notifyChanged()
                return false
            }
            val job = scope.launch {
                try {
                    persistSelectedPreview(selectedPreview, selectedAsset)
                    GitHubShareImportFlowCoordinator.sendActivePreviewAssetToInstaller(appContext)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    AppLogger.e(
                        GITHUB_SHARE_IMPORT_DELIVERY_RUNNER_TAG,
                        "Share import background delivery failed",
                        error
                    )
                    GitHubShareImportNotificationHelper.notifyFailed(
                        appContext,
                        appContext.getString(
                            R.string.github_share_import_error_app_managed_install_failed
                        )
                    )
                    GitHubTrackStoreSignals.notifyChanged()
                }
            }
            activeJob = job
            job.invokeOnCompletion {
                synchronized(lock) {
                    if (activeJob === job) {
                        activeJob = null
                    }
                }
            }
            return true
        }
    }

    private suspend fun persistSelectedPreview(
        preview: GitHubShareImportPreview?,
        selectedAsset: GitHubReleaseAssetFile?
    ) {
        if (preview == null || selectedAsset == null) return
        val selected = preview.copy(
            selectedAssetName = selectedAsset.name,
            sendInstallActionEnabled = true
        )
        withContext(Dispatchers.IO) {
            GitHubShareImportFlowStore.saveActivePreview(selected.toPendingPreviewRecord())
        }
        GitHubTrackStoreSignals.notifyChanged()
    }
}
