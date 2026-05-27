@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.sheet

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubApkManifestNode
import os.kei.feature.github.model.GitHubApkSignatureInfo
import os.kei.feature.github.model.GitHubInstalledPackageInfo
import os.kei.ui.page.main.github.GitHubStatusPalette
import os.kei.ui.page.main.os.appLucideChevronDownIcon
import os.kei.ui.page.main.os.appLucideChevronUpIcon
import os.kei.ui.page.main.os.appLucideInfoIcon
import os.kei.ui.page.main.widget.core.AppSurfaceCard
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.glass.AppLiquidIconButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.sheet.SheetDescriptionText
import os.kei.ui.page.main.widget.sheet.SheetSectionCard
import os.kei.ui.page.main.widget.sheet.SheetSectionTitle
import os.kei.ui.page.main.widget.status.StatusPill
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Immutable
private data class ManifestNodeRowUiState(
    val id: String,
    val displayLine: String,
    val riskSignals: List<ApkDifferenceSignal>,
)

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun ApkDifferenceSection(
    info: GitHubApkManifestInfo,
    installedInfo: GitHubInstalledPackageInfo?,
) {
    val signals =
        buildApkDifferenceSignals(
            info = info,
            installedInfo = installedInfo,
            strings = apkDifferenceStrings(),
        )
    if (signals.isEmpty()) return
    SheetSectionTitle(stringResource(R.string.github_apk_info_section_diff))
    SheetSectionCard(verticalSpacing = 6.dp) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            signals.forEach { signal ->
                StatusPill(
                    label = signal.label,
                    color = signal.color,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun ApkTrustReportSection(
    info: GitHubApkManifestInfo,
    installedInfo: GitHubInstalledPackageInfo?,
) {
    val strings = apkDifferenceStrings()
    val trustStats =
        remember(info) {
            ApkTrustReportStats(
                sensitivePermissionCount =
                    info.permissions.count { permission ->
                        permissionRiskColor(permission) == GitHubStatusPalette.Error
                    },
                exportedComponentCount = info.manifestNodes.count { node -> node.isExportedComponent() },
                exportedWithoutPermissionCount =
                    info.manifestNodes.count { node ->
                        node.isExportedComponent() && node.attributes["permission"].isNullOrBlank()
                    },
            )
        }
    val signals =
        buildList {
            addAll(buildApkDifferenceSignals(info, installedInfo, strings))
            if (info.signatureInfo != null) {
                add(
                    ApkDifferenceSignal(
                        stringResource(R.string.github_apk_info_trust_signed),
                        GitHubStatusPalette.Update,
                    ),
                )
            } else {
                add(
                    ApkDifferenceSignal(
                        stringResource(R.string.github_apk_info_trust_signature_unknown),
                        GitHubStatusPalette.Cache,
                    ),
                )
            }
            val sensitiveCount = trustStats.sensitivePermissionCount
            if (sensitiveCount > 0) {
                add(
                    ApkDifferenceSignal(
                        stringResource(
                            R.string.github_apk_info_trust_sensitive_permissions,
                            sensitiveCount,
                        ),
                        GitHubStatusPalette.Error,
                    ),
                )
            }
            if (trustStats.exportedWithoutPermissionCount > 0) {
                add(
                    ApkDifferenceSignal(
                        stringResource(
                            R.string.github_apk_info_trust_exported_without_permission,
                            trustStats.exportedWithoutPermissionCount,
                        ),
                        GitHubStatusPalette.Error,
                    ),
                )
            } else if (trustStats.exportedComponentCount > 0) {
                add(
                    ApkDifferenceSignal(
                        stringResource(
                            R.string.github_apk_info_trust_exported_components,
                            trustStats.exportedComponentCount,
                        ),
                        GitHubStatusPalette.Cache,
                    ),
                )
            }
        }
    SheetSectionTitle(stringResource(R.string.github_apk_info_section_trust))
    SheetSectionCard(verticalSpacing = 6.dp) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            signals.forEach { signal ->
                StatusPill(label = signal.label, color = signal.color)
            }
        }
    }
}

@Composable
internal fun InstalledPackageSection(
    info: GitHubApkManifestInfo,
    installedInfo: GitHubInstalledPackageInfo?,
) {
    SheetSectionTitle(stringResource(R.string.github_apk_info_section_installed))
    SheetSectionCard(verticalSpacing = 6.dp) {
        if (installedInfo?.appLabel?.isNotBlank() == true) {
            InfoRow(
                label = stringResource(R.string.github_apk_info_label_app),
                value = installedInfo.appLabel,
            )
        }
        val versionColors =
            compareVersionColors(
                remoteVersionCode = info.versionCode.toLongOrNull(),
                localVersionCode = installedInfo?.versionCode ?: -1L,
            )
        ComparisonPillRow(
            label = stringResource(R.string.github_apk_info_label_version),
            remoteLabel = info.remoteVersionLabel(),
            localLabel =
                installedInfo?.localVersionLabel()
                    ?: stringResource(R.string.github_apk_info_diff_not_installed),
            remoteColor = versionColors.remote,
            localColor = versionColors.local,
        )
        val apiColors =
            compareApiColors(
                remoteTargetSdk = info.targetSdk.toIntOrNull(),
                localTargetSdk = installedInfo?.targetSdk ?: -1,
            )
        ComparisonPillRow(
            label = stringResource(R.string.github_apk_info_label_api),
            remoteLabel =
                stringResource(
                    R.string.github_apk_info_value_api,
                    info.minSdk.ifBlank { "-" },
                    info.targetSdk.ifBlank { "-" },
                ),
            localLabel =
                installedInfo?.let { local ->
                    stringResource(
                        R.string.github_apk_info_value_api,
                        local.minSdk
                            .takeIf { it >= 0 }
                            ?.toString()
                            .orEmpty()
                            .ifBlank { "-" },
                        local.targetSdk
                            .takeIf { it >= 0 }
                            ?.toString()
                            .orEmpty()
                            .ifBlank { "-" },
                    )
                } ?: stringResource(R.string.github_apk_info_diff_not_installed),
            remoteColor = apiColors.remote,
            localColor = apiColors.local,
        )
        val abiSignal = buildAbiSignal(info.nativeAbis, apkDifferenceStrings())
        ComparisonPillRow(
            label = stringResource(R.string.github_apk_info_label_abi),
            remoteLabel =
                info.nativeAbis.takeIf { it.isNotEmpty() }?.joinToString(", ")
                    ?: stringResource(R.string.github_apk_info_diff_abi_universal),
            localLabel =
                Build.SUPPORTED_ABIS
                    .orEmpty()
                    .take(2)
                    .joinToString(", ")
                    .ifBlank { "-" },
            remoteColor = abiSignal.color,
            localColor = GitHubStatusPalette.Active,
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun ComparisonPillRow(
    label: String,
    remoteLabel: String,
    localLabel: String,
    remoteColor: Color,
    localColor: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.26f),
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            fontSize = AppTypographyTokens.Supporting.fontSize,
            lineHeight = AppTypographyTokens.Supporting.lineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        FlowRow(
            modifier = Modifier.weight(0.74f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StatusPill(
                label = stringResource(R.string.github_apk_info_compare_remote_format, remoteLabel),
                color = remoteColor,
            )
            StatusPill(
                label = stringResource(R.string.github_apk_info_compare_local_format, localLabel),
                color = localColor,
            )
        }
    }
}

@Composable
internal fun SignatureSection(
    signature: GitHubApkSignatureInfo?,
    query: String,
) {
    if (signature == null) {
        if (query.isNotBlank() &&
            !stringResource(R.string.github_apk_info_section_signature).contains(
                query,
                true,
            )
        ) {
            return
        }
        SheetSectionTitle(stringResource(R.string.github_apk_info_section_signature))
        SheetSectionCard { SheetDescriptionText(stringResource(R.string.github_apk_info_signature_empty)) }
        return
    }
    val lines =
        listOf(
            stringResource(R.string.github_apk_info_signature_entry, signature.entryName),
            stringResource(
                R.string.github_apk_info_signature_algorithm,
                signature.algorithm.ifBlank { "-" },
            ),
            stringResource(R.string.github_apk_info_signature_sha256, signature.sha256.ifBlank { "-" }),
            stringResource(
                R.string.github_apk_info_signature_subject,
                signature.subject.ifBlank { "-" },
            ),
            stringResource(R.string.github_apk_info_signature_issuer, signature.issuer.ifBlank { "-" }),
            stringResource(
                R.string.github_apk_info_signature_serial,
                signature.serialNumber.ifBlank { "-" },
            ),
        ).filterStringsByQuery(query)
    if (lines.isEmpty()) return
    SheetSectionTitle(stringResource(R.string.github_apk_info_section_signature))
    SheetSectionCard(verticalSpacing = 6.dp) {
        lines.forEach { DetailLine(it, maxLines = 3) }
    }
}

@Composable
internal fun ManifestTreeSection(
    nodeGroups: Map<Int, List<GitHubApkManifestNode>>,
    query: String,
) {
    if (nodeGroups.isEmpty()) {
        if (query.isBlank()) {
            SheetSectionTitle(stringResource(R.string.github_apk_info_section_manifest_tree))
            SheetSectionCard { SheetDescriptionText(stringResource(R.string.github_apk_info_manifest_tree_empty)) }
        }
        return
    }
    val expanded =
        remember(nodeGroups.keys) {
            mutableStateMapOf<Int, Boolean>().apply {
                nodeGroups.keys.forEach { key -> put(key, key == R.string.github_apk_info_group_exported) }
            }
        }
    SheetSectionTitle(stringResource(R.string.github_apk_info_section_manifest_tree))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        nodeGroups.forEach { (titleRes, groupNodes) ->
            ManifestNodeGroupCard(
                title = stringResource(titleRes),
                nodes = groupNodes,
                expanded = expanded[titleRes] == true || query.isNotBlank(),
                onToggle = { expanded[titleRes] = !(expanded[titleRes] == true) },
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun ManifestNodeGroupCard(
    title: String,
    nodes: List<GitHubApkManifestNode>,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val nodeRows =
        remember(nodes) {
            nodes
                .take(MANIFEST_NODE_LIMIT)
                .mapIndexed { index, node ->
                    ManifestNodeRowUiState(
                        id = node.manifestNodeStableId(index),
                        displayLine = node.displayLine(),
                        riskSignals = node.riskPills(),
                    )
                }
        }
    val hiddenNodeCount = remember(nodes) { (nodes.size - MANIFEST_NODE_LIMIT).coerceAtLeast(0) }
    AppSurfaceCard(onClick = onToggle) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    color = MiuixTheme.colorScheme.onBackground,
                    fontSize = AppTypographyTokens.Body.fontSize,
                    lineHeight = AppTypographyTokens.Body.lineHeight,
                    fontWeight = AppTypographyTokens.BodyEmphasis.fontWeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                StatusPill(label = nodes.size.toString(), color = GitHubStatusPalette.Active)
                AppLiquidIconButton(
                    backdrop = null,
                    icon = if (expanded) appLucideChevronUpIcon() else appLucideChevronDownIcon(),
                    contentDescription = title,
                    onClick = onToggle,
                    width = 32.dp,
                    height = 32.dp,
                    variant = GlassVariant.Content,
                )
            }
            if (expanded) {
                nodeRows.forEach { row ->
                    key(row.id) {
                        ManifestNodeRow(row)
                    }
                }
                if (hiddenNodeCount > 0) {
                    DetailLine(
                        stringResource(R.string.github_apk_info_more_count, hiddenNodeCount),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ManifestNodeRow(row: ManifestNodeRowUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        DetailLine(row.displayLine, maxLines = 3)
        if (row.riskSignals.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.riskSignals.forEach { signal ->
                    StatusPill(signal.label, signal.color)
                }
            }
        }
    }
}

@Composable
internal fun InfoListSection(
    title: String,
    empty: String,
    values: List<String>,
    colors: Map<String, Color> = emptyMap(),
) {
    SheetSectionTitle(title)
    SheetSectionCard(verticalSpacing = 6.dp) {
        if (values.isEmpty()) {
            SheetDescriptionText(empty)
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                values.take(INFO_LIST_LIMIT).forEach { value ->
                    StatusPill(
                        label = value,
                        color = colors[value] ?: GitHubStatusPalette.Active,
                    )
                }
            }
            if (values.size > INFO_LIST_LIMIT) {
                DetailLine(
                    stringResource(
                        R.string.github_apk_info_more_count,
                        values.size - INFO_LIST_LIMIT,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun InfoRow(
    label: String,
    value: String,
) {
    if (value.isBlank()) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.36f),
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            fontSize = AppTypographyTokens.Supporting.fontSize,
            lineHeight = AppTypographyTokens.Supporting.lineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            modifier = Modifier.weight(0.64f),
            color = MiuixTheme.colorScheme.onBackground,
            fontSize = AppTypographyTokens.Supporting.fontSize,
            lineHeight = AppTypographyTokens.Supporting.lineHeight,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun ApkInfoMeaningSection() {
    var expanded by remember { mutableStateOf(false) }
    SheetSectionTitle(stringResource(R.string.github_apk_info_section_meaning))
    SheetSectionCard(verticalSpacing = 8.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppLiquidIconButton(
                backdrop = null,
                icon = appLucideInfoIcon(),
                contentDescription = stringResource(R.string.github_apk_info_section_meaning),
                onClick = { expanded = !expanded },
                width = 32.dp,
                height = 32.dp,
                variant = GlassVariant.Content,
            )
            Text(
                text = stringResource(R.string.github_apk_info_meaning_summary),
                modifier = Modifier.weight(1f),
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                fontSize = AppTypographyTokens.Supporting.fontSize,
                lineHeight = AppTypographyTokens.Supporting.lineHeight,
            )
            AppLiquidIconButton(
                backdrop = null,
                icon = if (expanded) appLucideChevronUpIcon() else appLucideChevronDownIcon(),
                contentDescription = stringResource(R.string.github_apk_info_section_meaning),
                onClick = { expanded = !expanded },
                width = 32.dp,
                height = 32.dp,
                variant = GlassVariant.Content,
            )
        }
        if (expanded) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                apkMeaningEntries().forEach { entry ->
                    StatusPill(entry, GitHubStatusPalette.Active)
                }
            }
        }
    }
}

@Composable
internal fun apkMeaningEntries(): List<String> =
    listOf(
        stringResource(R.string.github_apk_info_meaning_version),
        stringResource(R.string.github_apk_info_meaning_target_api),
        stringResource(R.string.github_apk_info_meaning_abi),
        stringResource(R.string.github_apk_info_meaning_exported),
        stringResource(R.string.github_apk_info_meaning_permission),
        stringResource(R.string.github_apk_info_meaning_queries),
        stringResource(R.string.github_apk_info_meaning_signature),
    )

@Composable
internal fun apkInfoSourceLabel(source: String): String =
    when (source) {
        "api" -> stringResource(R.string.github_asset_fetch_source_api)
        "html" -> stringResource(R.string.github_asset_fetch_source_html)
        else -> stringResource(R.string.common_unknown)
    }

private fun GitHubApkManifestNode.manifestNodeStableId(index: Int): String =
    listOf(
        tagName,
        displayName,
        attributes["name"].orEmpty(),
        attributes["authorities"].orEmpty(),
        attributes["permission"].orEmpty(),
        attributes["process"].orEmpty(),
        index.toString(),
    ).joinToString("|")

@Composable
internal fun DetailLine(
    text: String,
    maxLines: Int = 3,
) {
    Text(
        text = text,
        color = MiuixTheme.colorScheme.onBackgroundVariant,
        fontSize = AppTypographyTokens.Supporting.fontSize,
        lineHeight = AppTypographyTokens.Supporting.lineHeight,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun apkDifferenceStrings(): ApkDifferenceStrings =
    ApkDifferenceStrings(
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
        abiMismatch = stringResource(R.string.github_apk_info_diff_abi_mismatch),
    )

private data class ApkTrustReportStats(
    val sensitivePermissionCount: Int,
    val exportedComponentCount: Int,
    val exportedWithoutPermissionCount: Int,
)
