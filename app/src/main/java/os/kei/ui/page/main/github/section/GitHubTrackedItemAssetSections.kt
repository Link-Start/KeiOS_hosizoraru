package os.kei.ui.page.main.github.section

import androidx.compose.runtime.Composable
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.isDirectApkTrack
import os.kei.feature.github.model.isGitHubRepositoryTrack
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.github.buildGitHubRepositoryHealth
import os.kei.ui.page.main.github.page.GitHubDecisionAssistDetailType

@Suppress("FunctionName")
@Composable
internal fun GitHubTrackedItemAssetSections(
    item: GitHubTrackedApp,
    state: VersionCheckUi,
    itemLookupConfig: GitHubLookupConfig,
    content: GitHubTrackedItemsContent,
    surfaces: GitHubTrackedItemsSurfaces,
    assetState: GitHubTrackedItemsAssetState,
    runtime: GitHubTrackedItemsRuntime,
    actions: GitHubTrackedItemsActions,
) {
    val assetBundle = assetState.apkAssetBundles[item.id]
    val assetLoading = assetState.apkAssetLoading[item.id] == true
    val assetError = assetState.apkAssetErrors[item.id].orEmpty()
    val assetExpanded = assetState.apkAssetExpanded[item.id] == true
    if (item.isGitHubRepositoryTrack() &&
        content.lookupConfig.decisionAssistEnabled &&
        content.lookupConfig.repositoryHealthCardEnabled
    ) {
        val health = buildGitHubRepositoryHealth(item, state)
        GitHubHealthPreviewBlock(
            health = health,
            onClick = {
                actions.onOpenDecisionAssistDetail(
                    GitHubDecisionAssistDetailType.RepositoryHealth,
                    item,
                )
            },
        )
    }
    if (item.isGitHubRepositoryTrack()) {
        GitHubTrackedItemAssetPanel(
            item = item,
            state = state,
            lookupConfig = itemLookupConfig,
            isDark = surfaces.isDark,
            contentBackdrop = surfaces.contentBackdrop,
            assetBundle = assetBundle,
            assetLoading = assetLoading,
            assetError = assetError,
            assetExpanded = assetExpanded,
            managedInstallLoading = assetState.managedInstallLoading,
            onOpenExternalUrl = actions.onOpenExternalUrl,
            onLoadApkAssets = actions.onLoadApkAssets,
            onRefreshTrackedItem = actions.onRefreshTrackedItem,
            onOpenApkInfo = actions.onOpenApkInfo,
            onOpenApkInDownloader = actions.onOpenApkInDownloader,
            onShareApkLink = actions.onShareApkLink,
            context = runtime.context,
            supportedAbis = runtime.supportedAbis,
        )
    }
    if (item.isDirectApkTrack()) {
        GitHubTrackedItemAssetPanel(
            item = item,
            state = state,
            lookupConfig = itemLookupConfig,
            isDark = surfaces.isDark,
            contentBackdrop = surfaces.contentBackdrop,
            assetBundle = null,
            assetLoading = false,
            assetError = "",
            assetExpanded = assetExpanded,
            managedInstallLoading = assetState.managedInstallLoading,
            onOpenExternalUrl = actions.onOpenExternalUrl,
            onLoadApkAssets = actions.onLoadApkAssets,
            onRefreshTrackedItem = actions.onRefreshTrackedItem,
            onOpenApkInfo = actions.onOpenApkInfo,
            onOpenApkInDownloader = actions.onOpenApkInDownloader,
            onShareApkLink = actions.onShareApkLink,
            context = runtime.context,
            supportedAbis = runtime.supportedAbis,
        )
        GitHubDirectApkRemoteHealthCard(
            item = item,
            state = state,
            onOpenExternalUrl = actions.onOpenExternalUrl,
        )
    }
}
