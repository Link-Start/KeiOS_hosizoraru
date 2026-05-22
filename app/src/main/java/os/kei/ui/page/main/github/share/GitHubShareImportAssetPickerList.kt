@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.share

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.ui.page.main.github.GitHubStatusPalette
import os.kei.ui.page.main.github.asset.assetIsPreferredForDevice
import os.kei.ui.page.main.github.asset.assetLikelyCompatibleWithDevice
import os.kei.ui.page.main.github.asset.formatAssetSize
import os.kei.ui.page.main.widget.sheet.SheetControlRow
import os.kei.ui.page.main.widget.sheet.SheetLiquidChoiceIndicator
import os.kei.ui.page.main.widget.status.StatusPill

@Composable
internal fun GitHubShareImportAssetPickerList(
    assets: List<GitHubReleaseAssetFile>,
    supportedAbis: List<String>,
    selectedIndex: Int,
    selectionEnabled: Boolean,
    onSelect: (Int) -> Unit,
) {
    LazyColumn(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        itemsIndexed(
            items = assets,
            key = { _, asset -> asset.name },
            contentType = { _, _ -> "github_share_asset" },
        ) { index, asset ->
            GitHubShareImportAssetPickerRow(
                asset = asset,
                supportedAbis = supportedAbis,
                selected = selectedIndex == index,
                selectionEnabled = selectionEnabled,
                onSelect = { onSelect(index) },
            )
        }
    }
}

@Composable
private fun GitHubShareImportAssetPickerRow(
    asset: GitHubReleaseAssetFile,
    supportedAbis: List<String>,
    selected: Boolean,
    selectionEnabled: Boolean,
    onSelect: () -> Unit,
) {
    val context = LocalContext.current
    val preferredForDevice = assetIsPreferredForDevice(asset.name, supportedAbis)
    val likelyCompatible = assetLikelyCompatibleWithDevice(asset.name, supportedAbis)
    val compatibilityHint =
        if (!likelyCompatible) {
            stringResource(R.string.github_share_import_dialog_asset_hint_maybe_incompatible)
        } else {
            null
        }
    val baseAssetSummary =
        stringResource(
            R.string.github_share_import_dialog_asset_summary,
            formatAssetSize(asset.sizeBytes, context),
            if (asset.apiAssetUrl.isNotBlank()) {
                stringResource(R.string.github_asset_fetch_source_api)
            } else {
                stringResource(R.string.github_asset_transport_direct)
            },
        )
    val assetSummary =
        compatibilityHint?.let { hint ->
            "$baseAssetSummary · $hint"
        } ?: baseAssetSummary
    SheetControlRow(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = selectionEnabled, onClick = onSelect),
        label = asset.name,
        summary = assetSummary,
    ) {
        GitHubShareImportAssetCompatibilityBadge(
            preferredForDevice = preferredForDevice,
            likelyCompatible = likelyCompatible,
        )
        SheetLiquidChoiceIndicator(
            selected = selected,
            onSelect = {
                if (selectionEnabled) onSelect()
            },
            accentColor = GitHubStatusPalette.Active,
        )
    }
}

@Composable
private fun GitHubShareImportAssetCompatibilityBadge(
    preferredForDevice: Boolean,
    likelyCompatible: Boolean,
) {
    when {
        preferredForDevice -> {
            StatusPill(
                label = stringResource(R.string.github_share_import_dialog_asset_badge_recommended),
                color = GitHubStatusPalette.Update,
            )
            Spacer(modifier = Modifier.width(6.dp))
        }

        !likelyCompatible -> {
            StatusPill(
                label = stringResource(R.string.github_share_import_dialog_asset_badge_incompatible),
                color = GitHubStatusPalette.PreRelease,
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
    }
}
