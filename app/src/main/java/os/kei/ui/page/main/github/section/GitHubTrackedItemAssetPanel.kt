@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.section

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.feature.github.data.remote.GitHubReleaseAssetBundle
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.ui.page.main.github.GitHubStatusPalette
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.github.asset.GitHubDirectApkAssetAccentKind
import os.kei.ui.page.main.github.asset.apkAssetTarget
import os.kei.ui.page.main.github.asset.directApkAssetPanelData
import os.kei.ui.page.main.github.isLocalAppUninstalled
import os.kei.ui.page.main.github.page.githubManagedInstallKey
import os.kei.ui.page.main.widget.motion.appExpandIn
import os.kei.ui.page.main.widget.motion.appExpandOut
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun GitHubTrackedItemAssetPanel(
    item: GitHubTrackedApp,
    state: VersionCheckUi,
    lookupConfig: GitHubLookupConfig,
    isDark: Boolean,
    assetBundle: GitHubReleaseAssetBundle?,
    assetLoading: Boolean,
    assetError: String,
    assetExpanded: Boolean,
    managedInstallLoading: SnapshotStateMap<String, Boolean>,
    onOpenExternalUrl: (String) -> Unit,
    onLoadApkAssets: (GitHubTrackedApp, VersionCheckUi, Boolean, Boolean, Boolean) -> Unit,
    onRefreshTrackedItem: (GitHubTrackedApp) -> Unit,
    onOpenApkInfo: (GitHubTrackedApp, GitHubReleaseAssetFile) -> Unit,
    onInstallApk: (GitHubTrackedApp, GitHubReleaseAssetFile) -> Unit,
    onOpenApkInDownloader: (GitHubTrackedApp, GitHubReleaseAssetFile) -> Unit,
    onShareApkLink: (GitHubReleaseAssetFile) -> Unit,
    context: Context,
    supportedAbis: List<String>,
) {
    val alwaysLatestReleaseDownload = item.alwaysShowLatestReleaseDownloadButton
    val latestReleaseAccent = Color(0xFF06B6D4)
    val directPanelData = item.directApkAssetPanelData(state)
    val renderedAssetBundle = assetBundle ?: directPanelData?.bundle
    val installFallbackMode = state.isLocalAppUninstalled()
    AnimatedVisibility(
        visible = assetExpanded || assetLoading || assetError.isNotBlank(),
        enter = appExpandIn(),
        exit = appExpandOut(),
    ) {
        Column(
            modifier =
                androidx.compose.ui.Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val target =
                state.apkAssetTarget(
                    owner = item.owner,
                    repo = item.repo,
                    context = context,
                    alwaysLatestRelease = alwaysLatestReleaseDownload,
                    allowLatestReleaseFallback = installFallbackMode,
                )
            val targetAccent =
                when {
                    directPanelData?.targetAccentKind == GitHubDirectApkAssetAccentKind.Install -> {
                        GitHubStatusPalette.Install
                    }

                    directPanelData?.targetAccentKind == GitHubDirectApkAssetAccentKind.PreRelease -> {
                        GitHubStatusPalette.PreRelease
                    }

                    directPanelData?.targetAccentKind == GitHubDirectApkAssetAccentKind.Stable -> {
                        GitHubStatusPalette.Update
                    }

                    installFallbackMode -> {
                        GitHubStatusPalette.Install
                    }

                    alwaysLatestReleaseDownload -> {
                        latestReleaseAccent
                    }

                    state.recommendsPreRelease || state.hasPreReleaseUpdate -> {
                        GitHubStatusPalette.PreRelease
                    }

                    else -> {
                        GitHubStatusPalette.Update
                    }
                }
            val summaryContainerColor =
                GitHubStatusPalette
                    .tonedSurface(
                        targetAccent,
                        isDark = isDark,
                    ).copy(alpha = if (isDark) 0.30f else 0.18f)
            val summaryBorderColor = targetAccent.copy(alpha = if (isDark) 0.30f else 0.20f)

            GitHubTrackedItemAssetSummaryCard(
                state = state,
                assetBundle = renderedAssetBundle,
                assetLoading = assetLoading,
                assetError = assetError,
                targetLabel =
                    directPanelData?.let { stringResource(it.targetLabelRes) }
                        ?: target?.label
                        ?: stringResource(
                            if (installFallbackMode) {
                                R.string.github_asset_target_install
                            } else {
                                R.string.github_item_label_update_assets
                            },
                        ),
                targetRawTag = directPanelData?.targetRawTag ?: target?.rawTag.orEmpty(),
                preciseApkVersionEnabled = lookupConfig.preciseApkVersionEnabled,
                fallbackReleaseUrl =
                    directPanelData?.bundle?.htmlUrl
                        ?: target?.releaseUrl.orEmpty(),
                targetAccent = targetAccent,
                summaryContainerColor = summaryContainerColor,
                summaryBorderColor = summaryBorderColor,
                onOpenExternalUrl = onOpenExternalUrl,
                onReloadAssets = {
                    if (directPanelData != null) {
                        onRefreshTrackedItem(item)
                    } else {
                        onLoadApkAssets(item, state, false, true, installFallbackMode)
                    }
                },
                context = context,
            )

            when {
                assetLoading -> {
                    GitHubTrackedItemAssetLoadingCard(
                        alwaysLatestReleaseDownload = alwaysLatestReleaseDownload,
                        targetAccent = targetAccent,
                        isDark = isDark,
                    )
                }

                assetError.isNotBlank() -> {
                    GitHubTrackedItemAssetErrorCard(
                        assetError = assetError,
                        isDark = isDark,
                    )
                }

                renderedAssetBundle != null -> {
                    val relativeTimeNowMillis =
                        remember(renderedAssetBundle) {
                            System.currentTimeMillis()
                        }
                    renderedAssetBundle.assets.forEach { asset ->
                        GitHubTrackedItemAssetRow(
                            asset = asset,
                            alwaysLatestReleaseDownload = alwaysLatestReleaseDownload,
                            targetAccent = targetAccent,
                            summaryContainerColor = summaryContainerColor,
                            summaryBorderColor = summaryBorderColor,
                            supportedAbis = supportedAbis,
                            relativeTimeNowMillis = relativeTimeNowMillis,
                            showApkTrustCheck =
                                lookupConfig.decisionAssistEnabled &&
                                    lookupConfig.apkTrustCheckEnabled,
                            managedInstallEnabled = lookupConfig.appManagedShareInstallEnabled,
                            managedInstallRunning =
                                managedInstallLoading[
                                    item.githubManagedInstallKey(asset),
                                ] == true,
                            installActionColor =
                                if (installFallbackMode) {
                                    GitHubStatusPalette.Install
                                } else {
                                    MiuixTheme.colorScheme.primary
                                },
                            context = context,
                            onOpenApkInfo = { onOpenApkInfo(item, asset) },
                            onInstallApk = { onInstallApk(item, asset) },
                            onOpenApkInDownloader = { onOpenApkInDownloader(item, asset) },
                            onShareApkLink = onShareApkLink,
                        )
                    }
                }
            }
        }
    }
}
