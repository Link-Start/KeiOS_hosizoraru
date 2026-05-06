package os.kei.ui.page.main.github.sheet

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.R
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubApkManifestNode
import os.kei.feature.github.model.GitHubApkSignatureInfo
import os.kei.feature.github.model.GitHubInstalledPackageInfo
import os.kei.ui.page.main.github.GitHubStatusPalette
import os.kei.ui.page.main.os.appLucideCloseIcon
import os.kei.ui.page.main.widget.core.AppSurfaceCard
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.glass.AppLiquidIconButton
import os.kei.ui.page.main.widget.glass.AppLiquidSearchField
import os.kei.ui.page.main.widget.glass.AppLiquidTextButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.sheet.SheetContentColumn
import os.kei.ui.page.main.widget.sheet.SheetDescriptionText
import os.kei.ui.page.main.widget.sheet.SheetSectionCard
import os.kei.ui.page.main.widget.sheet.SheetSectionTitle
import os.kei.ui.page.main.widget.sheet.SheetSummaryCard
import os.kei.ui.page.main.widget.sheet.SnapshotWindowBottomSheet
import os.kei.ui.page.main.widget.status.StatusPill
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GitHubApkInfoSheet(
    asset: GitHubReleaseAssetFile?,
    info: GitHubApkManifestInfo?,
    installedInfo: GitHubInstalledPackageInfo?,
    loading: Boolean,
    error: String,
    backdrop: LayerBackdrop,
    onRefresh: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onDismissRequest: () -> Unit
) {
    asset ?: return
    var query by remember(asset.name) { mutableStateOf("") }
    val normalizedQuery = query.trim()
    SnapshotWindowBottomSheet(
        show = true,
        title = stringResource(R.string.github_apk_info_title),
        onDismissRequest = onDismissRequest,
        startAction = {
            AppLiquidIconButton(
                backdrop = backdrop,
                variant = GlassVariant.Bar,
                icon = appLucideCloseIcon(),
                contentDescription = stringResource(R.string.common_close),
                onClick = onDismissRequest
            )
        }
    ) {
        SheetContentColumn(verticalSpacing = 10.dp) {
            SheetSummaryCard(
                title = asset.name,
                badgeLabel = stringResource(R.string.github_apk_info_badge_manifest),
                badgeColor = MiuixTheme.colorScheme.primary
            ) {
                when {
                    loading -> DetailLine(stringResource(R.string.github_apk_info_loading))
                    error.isNotBlank() -> DetailLine(error)
                    info == null -> DetailLine(stringResource(R.string.github_apk_info_empty))
                    else -> {
                        InfoRow(
                            label = stringResource(R.string.github_apk_info_label_package),
                            value = info.packageName
                        )
                        InfoRow(
                            label = stringResource(R.string.github_apk_info_label_version),
                            value = listOf(info.versionName, info.versionCode)
                                .filter { it.isNotBlank() }
                                .joinToString(" / ")
                        )
                        InfoRow(
                            label = stringResource(R.string.github_apk_info_label_api),
                            value = stringResource(
                                R.string.github_apk_info_value_api,
                                info.minSdk.ifBlank { "-" },
                                info.targetSdk.ifBlank { "-" }
                            )
                        )
                    }
                }
            }
            if (info != null) {
                ApkInfoActionRow(
                    backdrop = backdrop,
                    loading = loading,
                    onRefresh = onRefresh,
                    onDownload = onDownload,
                    onShare = onShare
                )
                AppLiquidSearchField(
                    value = query,
                    onValueChange = { query = it },
                    label = stringResource(R.string.github_apk_info_search_label),
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth(),
                    variant = GlassVariant.SearchField,
                    singleLine = true
                )
                ApkTrustReportSection(
                    info = info,
                    installedInfo = installedInfo
                )
                ApkDifferenceSection(
                    info = info,
                    installedInfo = installedInfo
                )
                InstalledPackageSection(
                    info = info,
                    installedInfo = installedInfo,
                )
                InfoListSection(
                    title = stringResource(R.string.github_apk_info_section_abi),
                    empty = stringResource(R.string.github_apk_info_empty_abi),
                    values = info.nativeAbis.filterStringsByQuery(normalizedQuery)
                )
                InfoListSection(
                    title = stringResource(R.string.github_apk_info_section_permissions),
                    empty = stringResource(R.string.github_apk_info_empty_permissions),
                    values = info.permissions.filterStringsByQuery(normalizedQuery),
                    colors = info.permissions.associateWith { permissionRiskColor(it) }
                )
                InfoListSection(
                    title = stringResource(R.string.github_apk_info_section_features),
                    empty = stringResource(R.string.github_apk_info_empty_features),
                    values = info.features.filterStringsByQuery(normalizedQuery)
                )
                InfoListSection(
                    title = stringResource(R.string.github_apk_info_section_metadata),
                    empty = stringResource(R.string.github_apk_info_empty_metadata),
                    values = info.metadata.map { metadata ->
                        if (metadata.value.isBlank()) metadata.name else "${metadata.name}: ${metadata.value}"
                    }.filterStringsByQuery(normalizedQuery)
                )
                SignatureSection(info.signatureInfo, normalizedQuery)
                ManifestTreeSection(
                    nodes = info.manifestNodes.filterNodesByQuery(normalizedQuery),
                    query = normalizedQuery
                )
            }
        }
    }
}

@Composable
private fun ApkInfoActionRow(
    backdrop: LayerBackdrop,
    loading: Boolean,
    onRefresh: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AppLiquidTextButton(
            backdrop = backdrop,
            text = if (loading) {
                stringResource(R.string.github_apk_info_action_refreshing)
            } else {
                stringResource(R.string.github_apk_info_action_refresh)
            },
            onClick = onRefresh,
            modifier = Modifier.weight(1f),
            enabled = !loading,
            variant = GlassVariant.SheetAction
        )
        AppLiquidTextButton(
            backdrop = backdrop,
            text = stringResource(R.string.github_apk_info_action_download),
            onClick = onDownload,
            modifier = Modifier.weight(1f),
            variant = GlassVariant.SheetPrimaryAction
        )
        AppLiquidTextButton(
            backdrop = backdrop,
            text = stringResource(R.string.github_apk_info_action_share),
            onClick = onShare,
            modifier = Modifier.weight(1f),
            variant = GlassVariant.SheetAction
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ApkDifferenceSection(
    info: GitHubApkManifestInfo,
    installedInfo: GitHubInstalledPackageInfo?
) {
    val signals = buildApkDifferenceSignals(
        info = info,
        installedInfo = installedInfo,
        strings = apkDifferenceStrings()
    )
    if (signals.isEmpty()) return
    SheetSectionTitle(stringResource(R.string.github_apk_info_section_diff))
    SheetSectionCard(verticalSpacing = 6.dp) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            signals.forEach { signal ->
                StatusPill(
                    label = signal.label,
                    color = signal.color
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ApkTrustReportSection(
    info: GitHubApkManifestInfo,
    installedInfo: GitHubInstalledPackageInfo?
) {
    val strings = apkDifferenceStrings()
    val permissions = info.permissions
    val exportedComponents = info.manifestNodes.filter { node -> node.isExportedComponent() }
    val exportedWithoutPermission = exportedComponents.filter { node ->
        node.attributes["permission"].isNullOrBlank()
    }
    val signals = buildList {
        addAll(buildApkDifferenceSignals(info, installedInfo, strings))
        if (info.signatureInfo != null) {
            add(
                ApkDifferenceSignal(
                    stringResource(R.string.github_apk_info_trust_signed),
                    GitHubStatusPalette.Update
                )
            )
        } else {
            add(
                ApkDifferenceSignal(
                    stringResource(R.string.github_apk_info_trust_signature_unknown),
                    GitHubStatusPalette.Cache
                )
            )
        }
        val sensitiveCount =
            permissions.count { permissionRiskColor(it) == GitHubStatusPalette.Error }
        if (sensitiveCount > 0) {
            add(
                ApkDifferenceSignal(
                    stringResource(
                        R.string.github_apk_info_trust_sensitive_permissions,
                        sensitiveCount
                    ),
                    GitHubStatusPalette.Error
                )
            )
        }
        if (exportedWithoutPermission.isNotEmpty()) {
            add(
                ApkDifferenceSignal(
                    stringResource(
                        R.string.github_apk_info_trust_exported_without_permission,
                        exportedWithoutPermission.size
                    ),
                    GitHubStatusPalette.Error
                )
            )
        } else if (exportedComponents.isNotEmpty()) {
            add(
                ApkDifferenceSignal(
                    stringResource(
                        R.string.github_apk_info_trust_exported_components,
                        exportedComponents.size
                    ),
                    GitHubStatusPalette.Cache
                )
            )
        }
    }
    SheetSectionTitle(stringResource(R.string.github_apk_info_section_trust))
    SheetSectionCard(verticalSpacing = 6.dp) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            signals.forEach { signal ->
                StatusPill(label = signal.label, color = signal.color)
            }
        }
    }
}

@Composable
private fun InstalledPackageSection(
    info: GitHubApkManifestInfo,
    installedInfo: GitHubInstalledPackageInfo?
) {
    SheetSectionTitle(stringResource(R.string.github_apk_info_section_installed))
    SheetSectionCard(verticalSpacing = 6.dp) {
        if (installedInfo?.appLabel?.isNotBlank() == true) {
            InfoRow(
                label = stringResource(R.string.github_apk_info_label_app),
                value = installedInfo.appLabel
            )
        }
        val versionColors = compareVersionColors(
            remoteVersionCode = info.versionCode.toLongOrNull(),
            localVersionCode = installedInfo?.versionCode ?: -1L
        )
        ComparisonPillRow(
            label = stringResource(R.string.github_apk_info_label_version),
            remoteLabel = info.remoteVersionLabel(),
            localLabel = installedInfo?.localVersionLabel()
                ?: stringResource(R.string.github_apk_info_diff_not_installed),
            remoteColor = versionColors.remote,
            localColor = versionColors.local
        )
        val apiColors = compareApiColors(
            remoteTargetSdk = info.targetSdk.toIntOrNull(),
            localTargetSdk = installedInfo?.targetSdk ?: -1
        )
        ComparisonPillRow(
            label = stringResource(R.string.github_apk_info_label_api),
            remoteLabel = stringResource(
                R.string.github_apk_info_value_api,
                info.minSdk.ifBlank { "-" },
                info.targetSdk.ifBlank { "-" }
            ),
            localLabel = installedInfo?.let { local ->
                stringResource(
                    R.string.github_apk_info_value_api,
                    local.minSdk.takeIf { it >= 0 }?.toString().orEmpty().ifBlank { "-" },
                    local.targetSdk.takeIf { it >= 0 }?.toString().orEmpty().ifBlank { "-" }
                )
            } ?: stringResource(R.string.github_apk_info_diff_not_installed),
            remoteColor = apiColors.remote,
            localColor = apiColors.local
        )
        val abiSignal = buildAbiSignal(info.nativeAbis, apkDifferenceStrings())
        ComparisonPillRow(
            label = stringResource(R.string.github_apk_info_label_abi),
            remoteLabel = info.nativeAbis.takeIf { it.isNotEmpty() }?.joinToString(", ")
                ?: stringResource(R.string.github_apk_info_diff_abi_universal),
            localLabel = Build.SUPPORTED_ABIS.orEmpty().take(2).joinToString(", ").ifBlank { "-" },
            remoteColor = abiSignal.color,
            localColor = GitHubStatusPalette.Active
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ComparisonPillRow(
    label: String,
    remoteLabel: String,
    localLabel: String,
    remoteColor: Color,
    localColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.24f),
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            fontSize = AppTypographyTokens.Supporting.fontSize,
            lineHeight = AppTypographyTokens.Supporting.lineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        FlowRow(
            modifier = Modifier.weight(0.76f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StatusPill(
                label = stringResource(R.string.github_apk_info_compare_remote_format, remoteLabel),
                color = remoteColor
            )
            StatusPill(
                label = stringResource(R.string.github_apk_info_compare_local_format, localLabel),
                color = localColor
            )
        }
    }
}

@Composable
private fun SignatureSection(
    signature: GitHubApkSignatureInfo?,
    query: String
) {
    if (signature == null) {
        if (query.isNotBlank() && !stringResource(R.string.github_apk_info_section_signature).contains(
                query,
                true
            )
        ) return
        SheetSectionTitle(stringResource(R.string.github_apk_info_section_signature))
        SheetSectionCard { SheetDescriptionText(stringResource(R.string.github_apk_info_signature_empty)) }
        return
    }
    val lines = listOf(
        stringResource(R.string.github_apk_info_signature_entry, signature.entryName),
        stringResource(
            R.string.github_apk_info_signature_algorithm,
            signature.algorithm.ifBlank { "-" }),
        stringResource(R.string.github_apk_info_signature_sha256, signature.sha256.ifBlank { "-" }),
        stringResource(
            R.string.github_apk_info_signature_subject,
            signature.subject.ifBlank { "-" }),
        stringResource(R.string.github_apk_info_signature_issuer, signature.issuer.ifBlank { "-" }),
        stringResource(
            R.string.github_apk_info_signature_serial,
            signature.serialNumber.ifBlank { "-" })
    ).filterStringsByQuery(query)
    if (lines.isEmpty()) return
    SheetSectionTitle(stringResource(R.string.github_apk_info_section_signature))
    SheetSectionCard(verticalSpacing = 6.dp) {
        lines.forEach { DetailLine(it, maxLines = 3) }
    }
}

@Composable
private fun ManifestTreeSection(
    nodes: List<GitHubApkManifestNode>,
    query: String
) {
    if (nodes.isEmpty()) {
        if (query.isBlank()) {
            SheetSectionTitle(stringResource(R.string.github_apk_info_section_manifest_tree))
            SheetSectionCard { SheetDescriptionText(stringResource(R.string.github_apk_info_manifest_tree_empty)) }
        }
        return
    }
    val groups = nodes.groupBy { it.groupLabelRes() }
    val expanded = remember(nodes) {
        mutableStateMapOf<Int, Boolean>().apply {
            groups.keys.forEach { key -> put(key, key == R.string.github_apk_info_group_exported) }
        }
    }
    SheetSectionTitle(stringResource(R.string.github_apk_info_section_manifest_tree))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        groups.forEach { (titleRes, groupNodes) ->
            ManifestNodeGroupCard(
                title = stringResource(titleRes),
                nodes = groupNodes,
                expanded = expanded[titleRes] == true || query.isNotBlank(),
                onToggle = { expanded[titleRes] = !(expanded[titleRes] == true) }
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ManifestNodeGroupCard(
    title: String,
    nodes: List<GitHubApkManifestNode>,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    AppSurfaceCard(onClick = onToggle) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = title, color = MiuixTheme.colorScheme.onBackground)
                StatusPill(label = nodes.size.toString(), color = GitHubStatusPalette.Active)
            }
            if (expanded) {
                nodes.take(MANIFEST_NODE_LIMIT).forEach { node ->
                    DetailLine(node.displayLine(), maxLines = 3)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        node.riskPills().forEach { signal ->
                            StatusPill(signal.label, signal.color)
                        }
                    }
                }
                val hidden = nodes.size - MANIFEST_NODE_LIMIT
                if (hidden > 0) {
                    DetailLine(
                        stringResource(R.string.github_apk_info_more_count, hidden),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoListSection(
    title: String,
    empty: String,
    values: List<String>,
    colors: Map<String, Color> = emptyMap()
) {
    SheetSectionTitle(title)
    SheetSectionCard(verticalSpacing = 6.dp) {
        if (values.isEmpty()) {
            SheetDescriptionText(empty)
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                values.take(INFO_LIST_LIMIT).forEach { value ->
                    StatusPill(
                        label = value,
                        color = colors[value] ?: GitHubStatusPalette.Active
                    )
                }
            }
            if (values.size > INFO_LIST_LIMIT) {
                DetailLine(
                    stringResource(
                        R.string.github_apk_info_more_count,
                        values.size - INFO_LIST_LIMIT
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String
) {
    if (value.isBlank()) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.36f),
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            fontSize = AppTypographyTokens.Supporting.fontSize,
            lineHeight = AppTypographyTokens.Supporting.lineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            modifier = Modifier.weight(0.64f),
            color = MiuixTheme.colorScheme.onBackground,
            fontSize = AppTypographyTokens.Supporting.fontSize,
            lineHeight = AppTypographyTokens.Supporting.lineHeight,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DetailLine(
    text: String,
    maxLines: Int = 3
) {
    Text(
        text = text,
        color = MiuixTheme.colorScheme.onBackgroundVariant,
        fontSize = AppTypographyTokens.Supporting.fontSize,
        lineHeight = AppTypographyTokens.Supporting.lineHeight,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}

private const val INFO_LIST_LIMIT = 12

@Composable
private fun apkDifferenceStrings(): ApkDifferenceStrings {
    return ApkDifferenceStrings(
        notInstalled = stringResource(R.string.github_apk_info_diff_not_installed),
        remoteVersionHigher = stringResource(R.string.github_apk_info_diff_remote_version_higher),
        localVersionHigher = stringResource(R.string.github_apk_info_diff_local_version_higher),
        sameVersion = stringResource(R.string.github_apk_info_diff_same_version),
        versionManual = stringResource(R.string.github_apk_info_diff_version_manual),
        targetHigher = stringResource(R.string.github_apk_info_diff_target_higher),
        targetSame = stringResource(R.string.github_apk_info_diff_target_same),
        targetLower = stringResource(R.string.github_apk_info_diff_target_lower),
        abiUniversal = stringResource(R.string.github_apk_info_diff_abi_universal),
        abiMatch = stringResource(R.string.github_apk_info_diff_abi_match),
        abiMismatch = stringResource(R.string.github_apk_info_diff_abi_mismatch)
    )
}

private fun buildApkDifferenceSignals(
    info: GitHubApkManifestInfo,
    installedInfo: GitHubInstalledPackageInfo?,
    strings: ApkDifferenceStrings
): List<ApkDifferenceSignal> {
    val signals = mutableListOf<ApkDifferenceSignal>()
    val remoteVersionCode = info.versionCode.toLongOrNull()
    val remoteTargetSdk = info.targetSdk.toIntOrNull()
    if (installedInfo == null) {
        signals += ApkDifferenceSignal(
            label = strings.notInstalled,
            color = GitHubStatusPalette.Active
        )
    } else {
        signals += when {
            remoteVersionCode == null || installedInfo.versionCode < 0L ->
                ApkDifferenceSignal(strings.versionManual, GitHubStatusPalette.Cache)

            remoteVersionCode > installedInfo.versionCode ->
                ApkDifferenceSignal(strings.remoteVersionHigher, GitHubStatusPalette.Update)

            remoteVersionCode < installedInfo.versionCode ->
                ApkDifferenceSignal(strings.localVersionHigher, GitHubStatusPalette.PreRelease)

            else -> ApkDifferenceSignal(strings.sameVersion, GitHubStatusPalette.Stable)
        }
        if (remoteTargetSdk != null && installedInfo.targetSdk >= 0) {
            signals += when {
                remoteTargetSdk > installedInfo.targetSdk ->
                    ApkDifferenceSignal(strings.targetHigher, GitHubStatusPalette.Update)

                remoteTargetSdk < installedInfo.targetSdk ->
                    ApkDifferenceSignal(strings.targetLower, GitHubStatusPalette.Cache)

                else -> ApkDifferenceSignal(strings.targetSame, GitHubStatusPalette.Stable)
            }
        }
    }
    signals += buildAbiSignal(info.nativeAbis, strings)
    return signals
}

private fun compareVersionColors(
    remoteVersionCode: Long?,
    localVersionCode: Long
): ComparisonColors {
    return when {
        remoteVersionCode == null || localVersionCode < 0L ->
            ComparisonColors(GitHubStatusPalette.Cache, GitHubStatusPalette.Cache)

        remoteVersionCode > localVersionCode ->
            ComparisonColors(GitHubStatusPalette.Update, GitHubStatusPalette.Stable)

        remoteVersionCode < localVersionCode ->
            ComparisonColors(GitHubStatusPalette.Cache, GitHubStatusPalette.PreRelease)

        else -> ComparisonColors(GitHubStatusPalette.Stable, GitHubStatusPalette.Stable)
    }
}

private fun compareApiColors(
    remoteTargetSdk: Int?,
    localTargetSdk: Int
): ComparisonColors {
    return when {
        remoteTargetSdk == null || localTargetSdk < 0 ->
            ComparisonColors(GitHubStatusPalette.Cache, GitHubStatusPalette.Cache)

        remoteTargetSdk > localTargetSdk ->
            ComparisonColors(GitHubStatusPalette.Update, GitHubStatusPalette.Stable)

        remoteTargetSdk < localTargetSdk ->
            ComparisonColors(GitHubStatusPalette.Cache, GitHubStatusPalette.Active)

        else -> ComparisonColors(GitHubStatusPalette.Stable, GitHubStatusPalette.Stable)
    }
}

private fun GitHubApkManifestInfo.remoteVersionLabel(): String {
    return listOf(versionName, versionCode).filter { it.isNotBlank() }.joinToString(" / ").ifBlank { "-" }
}

private fun GitHubInstalledPackageInfo.localVersionLabel(): String {
    return listOf(
        versionName,
        versionCode.takeIf { it >= 0L }?.toString().orEmpty()
    ).filter { it.isNotBlank() }.joinToString(" / ").ifBlank { "-" }
}

private fun List<String>.filterStringsByQuery(query: String): List<String> {
    val normalized = query.trim()
    if (normalized.isBlank()) return this
    return filter { it.contains(normalized, ignoreCase = true) }
}

private fun List<GitHubApkManifestNode>.filterNodesByQuery(query: String): List<GitHubApkManifestNode> {
    val normalized = query.trim()
    if (normalized.isBlank()) return this
    return filter { node ->
        node.tagName.contains(normalized, ignoreCase = true) ||
                node.displayName.contains(normalized, ignoreCase = true) ||
                node.attributes.any { (key, value) ->
                    key.contains(normalized, ignoreCase = true) ||
                            value.contains(normalized, ignoreCase = true)
                }
    }
}

private fun permissionRiskColor(permission: String): Color {
    val lower = permission.lowercase()
    return when {
        sensitivePermissionSignals.any { lower.contains(it) } -> GitHubStatusPalette.Error
        reviewPermissionSignals.any { lower.contains(it) } -> GitHubStatusPalette.Cache
        else -> GitHubStatusPalette.Active
    }
}

private fun GitHubApkManifestNode.isExportedComponent(): Boolean {
    return tagName in exportedComponentTags && attributes["exported"].equals(
        "true",
        ignoreCase = true
    )
}

private fun GitHubApkManifestNode.groupLabelRes(): Int {
    return when {
        isExportedComponent() -> R.string.github_apk_info_group_exported
        tagName in componentTags -> R.string.github_apk_info_group_components
        tagName in queryTags -> R.string.github_apk_info_group_queries
        tagName in intentTags -> R.string.github_apk_info_group_intents
        tagName == "application" -> R.string.github_apk_info_group_application
        else -> R.string.github_apk_info_group_other_manifest
    }
}

private fun GitHubApkManifestNode.displayLine(): String {
    val useful = listOf("exported", "permission", "authorities", "process", "enabled")
        .mapNotNull { key -> attributes[key]?.takeIf { it.isNotBlank() }?.let { "$key=$it" } }
    return buildString {
        append('<')
        append(tagName)
        append("> ")
        append(displayName)
        if (useful.isNotEmpty()) {
            append(" · ")
            append(useful.joinToString(" · "))
        }
    }
}

private fun GitHubApkManifestNode.riskPills(): List<ApkDifferenceSignal> {
    val signals = mutableListOf<ApkDifferenceSignal>()
    if (isExportedComponent()) {
        signals += ApkDifferenceSignal("exported", GitHubStatusPalette.Cache)
        if (attributes["permission"].isNullOrBlank()) {
            signals += ApkDifferenceSignal("no permission", GitHubStatusPalette.Error)
        }
    }
    attributes["permission"]?.takeIf { it.isNotBlank() }?.let { permission ->
        signals += ApkDifferenceSignal("perm $permission", permissionRiskColor(permission))
    }
    return signals
}

private fun buildAbiSignal(
    nativeAbis: List<String>,
    strings: ApkDifferenceStrings
): ApkDifferenceSignal {
    if (nativeAbis.isEmpty()) {
        return ApkDifferenceSignal(strings.abiUniversal, GitHubStatusPalette.Stable)
    }
    val supportedAbis = Build.SUPPORTED_ABIS.orEmpty().map { it.lowercase() }.toSet()
    val hasDeviceAbi = nativeAbis.any { abi -> abi.lowercase() in supportedAbis }
    return if (hasDeviceAbi) {
        ApkDifferenceSignal(strings.abiMatch, GitHubStatusPalette.Update)
    } else {
        ApkDifferenceSignal(strings.abiMismatch, GitHubStatusPalette.Error)
    }
}

private data class ApkDifferenceSignal(
    val label: String,
    val color: Color
)

private data class ComparisonColors(
    val remote: Color,
    val local: Color
)

private data class ApkDifferenceStrings(
    val notInstalled: String,
    val remoteVersionHigher: String,
    val localVersionHigher: String,
    val sameVersion: String,
    val versionManual: String,
    val targetHigher: String,
    val targetSame: String,
    val targetLower: String,
    val abiUniversal: String,
    val abiMatch: String,
    val abiMismatch: String
)

private val sensitivePermissionSignals = listOf(
    "install_packages",
    "delete_packages",
    "manage_external_storage",
    "read_sms",
    "send_sms",
    "receive_sms",
    "read_contacts",
    "record_audio",
    "camera",
    "access_fine_location",
    "bind_accessibility_service",
    "system_alert_window",
    "request_ignore_battery_optimizations",
    "query_all_packages"
)

private val reviewPermissionSignals = listOf(
    "post_notifications",
    "read_external_storage",
    "write_external_storage",
    "read_media",
    "access_coarse_location",
    "bluetooth",
    "nearby",
    "wake_lock"
)

private val componentTags = setOf("activity", "activity-alias", "service", "receiver", "provider")
private val exportedComponentTags = componentTags
private val queryTags = setOf("queries", "package", "intent")
private val intentTags = setOf("intent-filter", "action", "category", "data")
private const val MANIFEST_NODE_LIMIT = 8
