package os.kei.ui.page.main.github.share

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.ui.page.main.github.asset.assetIsPreferredForDevice

@Stable
internal class GitHubShareImportAssetPickerState(
    val supportedAbis: List<String>,
    private val assetsProvider: () -> List<GitHubReleaseAssetFile>,
    initialIndex: Int,
) {
    var selectedIndex by mutableIntStateOf(initialIndex)
        private set

    val safeSelectedIndex: Int
        get() {
            val assets = assetsProvider()
            if (assets.isEmpty()) return 0
            return selectedIndex.coerceIn(0, assets.lastIndex)
        }

    val selectedAsset: GitHubReleaseAssetFile?
        get() = assetsProvider().getOrNull(safeSelectedIndex)

    fun select(index: Int) {
        selectedIndex = index
    }
}

@Composable
internal fun rememberGitHubShareImportAssetPickerState(preview: GitHubShareImportPreview): GitHubShareImportAssetPickerState {
    val supportedAbis =
        remember {
            Build.SUPPORTED_ABIS?.toList().orEmpty()
        }
    val devicePreferredAssetIndex =
        remember(preview.assets, supportedAbis) {
            preview.assets.indexOfFirst { asset ->
                assetIsPreferredForDevice(asset.name, supportedAbis)
            }
        }
    val initialIndex =
        remember(
            preview.sourceUrl,
            preview.releaseTag,
            preview.assets,
            preview.preferredAssetName,
            devicePreferredAssetIndex,
        ) {
            when {
                preview.preferredAssetName.isNotBlank() -> preview.defaultSelectedIndex
                devicePreferredAssetIndex >= 0 -> devicePreferredAssetIndex
                else -> preview.defaultSelectedIndex
            }.coerceAtLeast(0)
        }
    return remember(
        preview.sourceUrl,
        preview.releaseTag,
        preview.assets,
        supportedAbis,
        initialIndex,
    ) {
        GitHubShareImportAssetPickerState(
            supportedAbis = supportedAbis,
            assetsProvider = { preview.assets },
            initialIndex = initialIndex,
        )
    }
}
