package os.kei.ui.page.main.github.page.action

import android.content.Intent
import os.kei.R
import os.kei.core.download.AppPrivateDownloadManager
import os.kei.core.intent.SafeExternalIntents
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubLookupStrategyOption

internal class GitHubAssetTransferActions(
    private val env: GitHubPageActionEnvironment,
) {
    private val context get() = env.context
    private val state get() = env.state
    private val repository get() = env.repository
    private val systemDmOption get() = env.systemDmOption

    suspend fun sendAssetToConfiguredChannel(asset: GitHubReleaseAssetFile): Boolean =
        if (state.lookupConfig.onlineShareTargetPackage.isNotBlank()) {
            shareApkLink(asset)
        } else {
            openApkInDownloader(asset)
        }

    suspend fun shareApkLink(asset: GitHubReleaseAssetFile): Boolean {
        val resolvedUrl =
            SafeExternalIntents.httpsExternalUrlOrNull(resolvePreferredAssetUrl(asset))
                ?: run {
                    env.toast(R.string.github_toast_share_link_failed)
                    return false
                }
        val onlineSharePackage = state.lookupConfig.onlineShareTargetPackage.trim()
        val intent =
            SafeExternalIntents.textShareIntent(
                text = resolvedUrl,
                subject = asset.name,
                targetPackage = onlineSharePackage,
                extras =
                    if (onlineSharePackage.isNotBlank()) {
                        mapOf(
                            "channel" to "Online",
                            "extra_channel" to "Online",
                            "online_channel" to true,
                        )
                    } else {
                        emptyMap()
                    },
            )
        return runCatching {
            if (onlineSharePackage.isNotBlank()) {
                context.startActivity(intent)
            } else {
                context.startActivity(
                    Intent.createChooser(intent, context.getString(R.string.github_share_apk_link_title)),
                )
            }
            true
        }.getOrElse {
            env.toast(R.string.github_toast_share_link_failed)
            false
        }
    }

    suspend fun openApkInDownloader(asset: GitHubReleaseAssetFile): Boolean {
        val resolvedUrl =
            SafeExternalIntents.httpsExternalUrlOrNull(resolvePreferredAssetUrl(asset))
                ?: run {
                    env.toast(R.string.github_toast_open_downloader_failed)
                    return false
                }
        val preferredPackage = state.lookupConfig.preferredDownloaderPackage.trim()
        return runCatching {
            when (preferredPackage) {
                systemDmOption.packageName -> {
                    enqueueWithSystemDownloadManager(resolvedUrl, asset.name)
                    env.toast(R.string.github_toast_downloader_system_builtin)
                }

                "" -> {
                    require(SafeExternalIntents.startBrowsableUrl(context, resolvedUrl))
                    env.toast(R.string.github_toast_downloader_system_default)
                }

                else -> {
                    require(SafeExternalIntents.startBrowsableUrl(context, resolvedUrl, preferredPackage))
                    env.toast(R.string.github_toast_downloader_selected)
                }
            }
            true
        }.recoverCatching {
            if (preferredPackage.isNotBlank() && preferredPackage != systemDmOption.packageName) {
                require(SafeExternalIntents.startBrowsableUrl(context, resolvedUrl))
                env.toast(R.string.github_toast_downloader_fallback_system)
                true
            } else {
                throw it
            }
        }.getOrElse {
            env.toast(R.string.github_toast_open_downloader_failed)
            false
        }
    }

    private suspend fun resolvePreferredAssetUrl(asset: GitHubReleaseAssetFile): String {
        val token = state.lookupConfig.apiToken.trim()
        val preferApiAsset =
            state.lookupConfig.selectedStrategy == GitHubLookupStrategyOption.GitHubApiToken
        return repository.resolvePreferredDownloadUrl(
            asset = asset,
            useApiAssetUrl = preferApiAsset,
            apiToken = token,
        )
    }

    private fun enqueueWithSystemDownloadManager(
        url: String,
        fileName: String,
    ) {
        AppPrivateDownloadManager.enqueueHttpsDownload(
            context = context,
            url = url,
            fileName = fileName,
        )
    }
}
