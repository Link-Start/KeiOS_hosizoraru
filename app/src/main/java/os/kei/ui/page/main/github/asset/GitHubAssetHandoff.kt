package os.kei.ui.page.main.github.asset

import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import os.kei.R
import os.kei.core.download.AppPrivateDownloadManager
import os.kei.core.intent.SafeExternalIntents
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.domain.GitHubReleaseAssetService
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubLookupStrategyOption
import os.kei.ui.page.main.github.query.systemDownloadManagerPackageName

/**
 * Where a hand-off went.
 *
 * A share needs no message: the installer or the share sheet that opens says it. A download can land in
 * four places that look the same from the button, so those name where it went.
 */
internal enum class GitHubAssetHandoffRoute(
    @param:StringRes val messageRes: Int?,
) {
    /** Straight to the installer 分享到安装器 names. */
    ShareTarget(null),

    /** The system share sheet, because no installer is chosen. */
    ShareChooser(null),
    SystemDownloadManager(R.string.github_toast_downloader_system_builtin),
    SystemDefault(R.string.github_toast_downloader_system_default),
    SelectedDownloader(R.string.github_toast_downloader_selected),

    /** The chosen downloader would not take the link, so the system default did. */
    SelectedDownloaderFallback(R.string.github_toast_downloader_fallback_system),
}

internal sealed interface GitHubAssetHandoffResult {
    /** What to tell the reader, or null when what opened already tells them. */
    val messageRes: Int?

    /** Says [messageRes] through the caller's own toast, and answers whether the file left. */
    fun report(toast: (Int) -> Unit): Boolean {
        messageRes?.let(toast)
        return this is Delivered
    }

    data class Delivered(
        val route: GitHubAssetHandoffRoute,
    ) : GitHubAssetHandoffResult {
        override val messageRes: Int? get() = route.messageRes
    }

    data class Failed(
        @param:StringRes override val messageRes: Int,
    ) : GitHubAssetHandoffResult
}

/**
 * The one way a release file leaves KeiOS: shared, or handed to a downloader.
 *
 * Every surface that shows a file offers the same two actions — the tracked card and its APK info sheet,
 * the release history, the F-Droid version history, an Actions artifact, the share-import flow — and the
 * settings sheet promises what they do once, for all of them: 分享到安装器 sends a share straight to the
 * chosen installer, 下载器 picks where a download goes. Each surface used to build its own intent, and the
 * two history pages built ones that read neither setting, so the same share button went to InstallerX on
 * the tracked card and to a chooser one page further in (issue #29).
 */
internal object GitHubAssetHandoff {
    /**
     * Extras an online installer reads to start installing while it downloads, rather than treating the
     * link as text to save.
     */
    private val onlineChannelExtras: Map<String, Any> =
        mapOf(
            "channel" to "Online",
            "extra_channel" to "Online",
            "online_channel" to true,
        )

    private val defaultAssetService = GitHubReleaseAssetService()

    /**
     * The link an asset leaves through: the API asset link when the token strategy is the configured
     * source, since that is the one a private or rate-limited repository still answers, else the browser
     * download link.
     */
    suspend fun assetUrl(
        lookupConfig: GitHubLookupConfig,
        asset: GitHubReleaseAssetFile,
        assetService: GitHubReleaseAssetService = defaultAssetService,
    ): String =
        assetService.resolvePreferredDownloadUrl(
            asset = asset,
            useApiAssetUrl = lookupConfig.selectedStrategy == GitHubLookupStrategyOption.GitHubApiToken,
            apiToken = lookupConfig.apiToken.trim(),
        )

    suspend fun shareAsset(
        context: Context,
        lookupConfig: GitHubLookupConfig,
        asset: GitHubReleaseAssetFile,
        newTask: Boolean = false,
    ): GitHubAssetHandoffResult =
        share(
            context = context,
            lookupConfig = lookupConfig,
            url = assetUrl(lookupConfig, asset),
            subject = asset.name,
            chooserTitle = context.getString(R.string.github_share_apk_link_title),
            newTask = newTask,
        )

    suspend fun downloadAsset(
        context: Context,
        lookupConfig: GitHubLookupConfig,
        asset: GitHubReleaseAssetFile,
        newTask: Boolean = false,
    ): GitHubAssetHandoffResult =
        openInDownloader(
            context = context,
            lookupConfig = lookupConfig,
            url = assetUrl(lookupConfig, asset),
            fileName = asset.name,
            newTask = newTask,
        )

    /** Share-import's single send: a chosen installer takes the file, otherwise the downloader does. */
    suspend fun sendAssetToConfiguredChannel(
        context: Context,
        lookupConfig: GitHubLookupConfig,
        asset: GitHubReleaseAssetFile,
        newTask: Boolean = false,
    ): GitHubAssetHandoffResult =
        if (lookupConfig.onlineShareTargetPackage.isNotBlank()) {
            shareAsset(context, lookupConfig, asset, newTask)
        } else {
            downloadAsset(context, lookupConfig, asset, newTask)
        }

    fun share(
        context: Context,
        lookupConfig: GitHubLookupConfig,
        url: String,
        subject: String,
        chooserTitle: String,
        newTask: Boolean = false,
    ): GitHubAssetHandoffResult {
        val failed = GitHubAssetHandoffResult.Failed(R.string.github_toast_share_link_failed)
        val safeUrl = SafeExternalIntents.httpsExternalUrlOrNull(url) ?: return failed
        val targetPackage = lookupConfig.onlineShareTargetPackage.trim()
        val send =
            SafeExternalIntents.textShareIntent(
                text = safeUrl,
                subject = subject,
                targetPackage = targetPackage,
                extras = if (targetPackage.isNotBlank()) onlineChannelExtras else emptyMap(),
            )
        val intent = if (targetPackage.isNotBlank()) send else Intent.createChooser(send, chooserTitle)
        if (newTask) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!context.startActivitySafely(intent)) return failed
        return GitHubAssetHandoffResult.Delivered(
            if (targetPackage.isNotBlank()) {
                GitHubAssetHandoffRoute.ShareTarget
            } else {
                GitHubAssetHandoffRoute.ShareChooser
            },
        )
    }

    fun openInDownloader(
        context: Context,
        lookupConfig: GitHubLookupConfig,
        url: String,
        fileName: String,
        mimeType: String = "",
        newTask: Boolean = false,
    ): GitHubAssetHandoffResult {
        val failed = GitHubAssetHandoffResult.Failed(R.string.github_toast_open_downloader_failed)
        val safeUrl = SafeExternalIntents.httpsExternalUrlOrNull(url) ?: return failed
        val route =
            when (val preferredPackage = lookupConfig.preferredDownloaderPackage.trim()) {
                systemDownloadManagerPackageName ->
                    GitHubAssetHandoffRoute.SystemDownloadManager.takeIf {
                        runCatching {
                            AppPrivateDownloadManager.enqueueHttpsDownload(
                                context = context,
                                url = safeUrl,
                                fileName = fileName,
                                mimeType = mimeType,
                            )
                        }.isSuccess
                    }

                "" ->
                    GitHubAssetHandoffRoute.SystemDefault.takeIf {
                        SafeExternalIntents.startBrowsableUrl(context, safeUrl, newTask = newTask)
                    }

                else ->
                    when {
                        SafeExternalIntents.startBrowsableUrl(context, safeUrl, preferredPackage, newTask) ->
                            GitHubAssetHandoffRoute.SelectedDownloader

                        SafeExternalIntents.startBrowsableUrl(context, safeUrl, newTask = newTask) ->
                            GitHubAssetHandoffRoute.SelectedDownloaderFallback

                        else -> null
                    }
            }
        return route?.let(GitHubAssetHandoffResult::Delivered) ?: failed
    }

    private fun Context.startActivitySafely(intent: Intent): Boolean =
        runCatching {
            startActivity(intent)
            true
        }.getOrDefault(false)
}
