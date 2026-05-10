package os.kei.ui.page.main.github.install

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.R
import os.kei.feature.github.model.GitHubApkTrustReason
import os.kei.feature.github.model.GitHubDecisionLevel
import os.kei.feature.github.model.GitHubInstalledPackageInfo
import os.kei.ui.page.main.github.AppIcon
import os.kei.ui.page.main.github.GitHubStatusPalette
import os.kei.ui.page.main.github.asset.formatAssetSize
import os.kei.ui.page.main.os.appLucideCloseIcon
import os.kei.ui.page.main.widget.core.AppStatusPillSize
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.glass.AppLiquidIconButton
import os.kei.ui.page.main.widget.glass.AppLiquidTextButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.glass.LiquidLinearProgressBar
import os.kei.ui.page.main.widget.sheet.SheetDescriptionText
import os.kei.ui.page.main.widget.sheet.SheetSectionCard
import os.kei.ui.page.main.widget.sheet.SheetSectionTitle
import os.kei.ui.page.main.widget.sheet.SnapshotWindowBottomSheet
import os.kei.ui.page.main.widget.status.StatusPill
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun GitHubApkInstallSheet(
    state: GitHubApkInstallFlowState,
    backdrop: LayerBackdrop,
    onDismissRequest: () -> Unit = GitHubApkInstallFlowCoordinator::hideSheet
) {
    if (!state.sheetVisible || !state.active) return
    val context = LocalContext.current
    SnapshotWindowBottomSheet(
        show = true,
        title = stringResource(R.string.github_apk_install_sheet_title),
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InstallIdentityHeader(state)
            InstallMainCard(state = state, context = context)

            when (state.phase) {
                GitHubApkInstallPhase.SelectingApk -> ApkCandidateSection(
                    state = state,
                    context = context
                )

                else -> Unit
            }
            InstallActionSection(state = state, backdrop = backdrop, context = context)
        }
    }
}

@Composable
private fun InstallMainCard(
    state: GitHubApkInstallFlowState,
    context: android.content.Context
) {
    SheetSectionCard(
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        verticalSpacing = 8.dp
    ) {
        InstallHeader(state = state)
        InstallStepRail(state)
        if (state.showsDeterminateDownloadProgress) {
            LiquidLinearProgressBar(
                progress = { state.stageProgress.coerceIn(0f, 1f) },
                height = 5.dp,
                modifier = Modifier.fillMaxWidth()
            )
        }
        InstallReferenceRows(state = state, context = context)
    }
}

@Composable
private fun InstallIdentityHeader(state: GitHubApkInstallFlowState) {
    val packageName = state.candidatePackageName()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AppIcon(
            packageName = packageName,
            size = 58.dp,
            localRefreshKey = state.sessionId
        )
        Text(
            text = state.identityName(),
            color = MiuixTheme.colorScheme.onBackground,
            fontSize = AppTypographyTokens.SectionTitle.fontSize,
            lineHeight = AppTypographyTokens.SectionTitle.lineHeight,
            fontWeight = AppTypographyTokens.SectionTitle.fontWeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        packageName.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                fontSize = AppTypographyTokens.Body.fontSize,
                lineHeight = AppTypographyTokens.Body.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun InstallHeader(state: GitHubApkInstallFlowState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusPill(
                label = stringResource(state.phase.labelRes()),
                color = state.phase.statusColor()
            )
            if (state.phase == GitHubApkInstallPhase.ReadyToInstall) {
                state.versionVerdict()?.let { verdict ->
                    StatusPill(
                        label = verdict.label,
                        color = verdict.color,
                        size = AppStatusPillSize.Compact,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
        if (state.showsDeterminateDownloadProgress) {
            Text(
                text = stringResource(
                    R.string.github_apk_install_summary_progress,
                    state.stageProgressPercent
                ),
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                fontSize = AppTypographyTokens.Supporting.fontSize,
                lineHeight = AppTypographyTokens.Supporting.lineHeight
            )
        }
    }
    Text(
        text = state.displayFileName(),
        color = MiuixTheme.colorScheme.onBackgroundVariant,
        fontSize = AppTypographyTokens.Caption.fontSize,
        lineHeight = AppTypographyTokens.Caption.lineHeight,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
    state.message
        .takeIf { it.isNotBlank() && state.phase !in bottomResultPhases }
        ?.let { message ->
            SheetDescriptionText(text = message)
        }
}

@Composable
private fun InstallStepRail(state: GitHubApkInstallFlowState) {
    val currentIndex = state.installStepIndex()
    val failed = state.phase == GitHubApkInstallPhase.Failed ||
            state.phase == GitHubApkInstallPhase.Cancelled
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            installStepPhases.forEachIndexed { index, phase ->
                val nodeColor = state.stepNodeColor(index, currentIndex, failed)
                Box(
                    modifier = Modifier
                        .size(if (index == currentIndex) 10.dp else 8.dp)
                        .background(nodeColor, CircleShape)
                )
                if (index < installStepPhases.lastIndex) {
                    val lineColor =
                        if (index < currentIndex && !failedAtLine(index, currentIndex, failed)) {
                            MiuixTheme.colorScheme.primary
                        } else {
                            MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.22f)
                        }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                            .height(2.dp)
                            .background(lineColor, CircleShape)
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            installStepPhases.forEach { phase ->
                Text(
                    text = stringResource(phase.labelRes()),
                    modifier = Modifier.weight(1f),
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    fontSize = AppTypographyTokens.Caption.fontSize,
                    lineHeight = AppTypographyTokens.Caption.lineHeight,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun GitHubApkInstallFlowState.stepNodeColor(
    index: Int,
    currentIndex: Int,
    failed: Boolean
): Color {
    return when {
        failed && index == currentIndex -> GitHubStatusPalette.Error
        phase == GitHubApkInstallPhase.Success -> GitHubStatusPalette.Update
        index < currentIndex -> MiuixTheme.colorScheme.primary
        index == currentIndex -> phase.statusColor()
        else -> MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.30f)
    }
}

private fun failedAtLine(
    index: Int,
    currentIndex: Int,
    failed: Boolean
): Boolean {
    return failed && index >= currentIndex
}

private val installStepPhases = listOf(
    GitHubApkInstallPhase.Downloading,
    GitHubApkInstallPhase.SelectingApk,
    GitHubApkInstallPhase.Inspecting,
    GitHubApkInstallPhase.ReadyToInstall,
    GitHubApkInstallPhase.Installing,
    GitHubApkInstallPhase.Success
)

private fun GitHubApkInstallFlowState.installStepIndex(): Int {
    return when (phase) {
        GitHubApkInstallPhase.Downloading -> 0
        GitHubApkInstallPhase.SelectingApk -> 1
        GitHubApkInstallPhase.Inspecting -> 2
        GitHubApkInstallPhase.ReadyToInstall -> 3
        GitHubApkInstallPhase.Installing,
        GitHubApkInstallPhase.PendingUserAction -> 4

        GitHubApkInstallPhase.Success -> installStepPhases.lastIndex
        GitHubApkInstallPhase.Failed,
        GitHubApkInstallPhase.Cancelled -> when {
            localArchiveInfo != null || selectedCandidateName.isNotBlank() -> 4
            candidates.isNotEmpty() -> 1
            else -> 0
        }

        GitHubApkInstallPhase.Idle -> 0
    }.coerceIn(0, installStepPhases.lastIndex)
}

@Composable
private fun InstallReferenceRows(
    state: GitHubApkInstallFlowState,
    context: android.content.Context
) {
    val rows = state.installComparisonRows(context)
    if (rows.isEmpty()) return
    InstallComparisonTable(rows)
}

@Composable
private fun GitHubApkInstallFlowState.installComparisonRows(
    context: android.content.Context
): List<InstallComparisonRowModel> {
    val displaySize = selectedCandidateSizeBytes
        .takeIf { it > 0L }
        ?: asset?.sizeBytes.orZero()
    val localSize = installedPackageInfo?.sourceSizeBytes.orZero()
    val notInstalled = stringResource(R.string.github_apk_install_value_not_installed)
    val unknown = stringResource(R.string.github_apk_install_value_unknown)
    val installed = installedPackageInfo != null
    fun local(value: String): String {
        return when {
            value.isNotBlank() -> value
            installed -> unknown
            else -> notInstalled
        }
    }

    fun candidate(value: String): String = value.ifBlank { unknown }
    fun row(label: String, localValue: String, candidateValue: String): InstallComparisonRowModel {
        return InstallComparisonRowModel(
            label = label,
            value = comparisonValue(
                context = context,
                localValue = localValue,
                candidateValue = candidateValue
            )
        )
    }
    return buildList {
        addIfUseful(
            row(
                label = stringResource(R.string.github_apk_install_reference_name),
                localValue = local(installedPackageInfo?.appLabel.orEmpty()),
                candidateValue = candidate(candidateAppLabel())
            )
        )
        addIfUseful(
            row(
                label = stringResource(R.string.github_apk_install_reference_package),
                localValue = local(installedPackageInfo?.packageName.orEmpty()),
                candidateValue = candidate(candidatePackageName())
            )
        )
        addIfUseful(
            row(
                label = stringResource(R.string.github_apk_install_reference_version_name),
                localValue = local(installedPackageInfo?.versionName.orEmpty()),
                candidateValue = candidate(candidateVersionName())
            )
        )
        addIfUseful(
            row(
                label = stringResource(R.string.github_apk_install_reference_version_code),
                localValue = local(installedPackageInfo?.versionCodeLabel().orEmpty()),
                candidateValue = candidate(candidateVersionCodeLabel())
            )
        )
        addIfUseful(
            row(
                label = stringResource(R.string.github_apk_install_reference_target_sdk),
                localValue = local(installedPackageInfo?.targetSdk.sdkLabel()),
                candidateValue = candidate(candidateTargetSdkLabel())
            )
        )
        addIfUseful(
            row(
                label = stringResource(R.string.github_apk_install_reference_min_sdk),
                localValue = local(installedPackageInfo?.minSdk.sdkLabel()),
                candidateValue = candidate(candidateMinSdkLabel())
            )
        )
        if (displaySize > 0L) {
            addIfUseful(
                row(
                    label = stringResource(R.string.github_apk_install_reference_size),
                    localValue = local(
                        localSize
                            .takeIf { it > 0L }
                            ?.let { formatAssetSize(it, context) }
                            .orEmpty()
                    ),
                    candidateValue = formatAssetSize(displaySize, context)
                )
            )
        }
        addIfUseful(
            row(
                label = stringResource(R.string.github_apk_install_reference_abi),
                localValue = deviceAbiLabel().ifBlank { unknown },
                candidateValue = candidate(candidateAbiLabel())
            )
        )
        addIfUseful(
            row(
                label = stringResource(R.string.github_apk_install_reference_signature),
                localValue = local(
                    installedPackageInfo?.signatureSha256?.firstOrNull()?.shortSha().orEmpty()
                ),
                candidateValue = candidate(candidateSignatureShortLabel())
            )
        )
    }
}

private data class InstallComparisonRowModel(
    val label: String,
    val value: String
)

private data class InstallComparisonVerdictModel(
    val label: String,
    val color: Color
)

private fun MutableList<InstallComparisonRowModel>.addIfUseful(
    row: InstallComparisonRowModel
) {
    if (row.value.isBlank()) return
    add(row)
}

@Composable
private fun InstallComparisonTable(rows: List<InstallComparisonRowModel>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        rows.forEach { row -> InstallComparisonRow(row) }
    }
}

@Composable
private fun InstallComparisonRow(row: InstallComparisonRowModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = row.label,
            modifier = Modifier.weight(0.30f),
            color = MiuixTheme.colorScheme.onBackground,
            fontSize = AppTypographyTokens.BodyEmphasis.fontSize,
            lineHeight = AppTypographyTokens.BodyEmphasis.lineHeight,
            fontWeight = AppTypographyTokens.BodyEmphasis.fontWeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = row.value,
            modifier = Modifier.weight(0.70f),
            color = MiuixTheme.colorScheme.primary,
            fontSize = AppTypographyTokens.Body.fontSize,
            lineHeight = AppTypographyTokens.Body.lineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun InstallBottomResult(state: GitHubApkInstallFlowState) {
    if (state.phase !in bottomResultPhases) return
    state.trustSignal
        .takeIf { state.phase == GitHubApkInstallPhase.ReadyToInstall }
        ?.let { signal ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPill(
                    label = stringResource(signal.level.labelRes()),
                    color = signal.level.statusColor()
                )
                Text(
                    text = state.message.ifBlank {
                        stringResource(R.string.github_apk_trust_detail_reason_empty)
                    },
                    modifier = Modifier.weight(1f),
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    fontSize = AppTypographyTokens.Supporting.fontSize,
                    lineHeight = AppTypographyTokens.Supporting.lineHeight,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            InstallTrustPills(signal = signal)
        } ?: state.message.takeIf { it.isNotBlank() }?.let { message ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusPill(
                label = stringResource(state.phase.labelRes()),
                color = state.phase.statusColor()
            )
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                fontSize = AppTypographyTokens.Supporting.fontSize,
                lineHeight = AppTypographyTokens.Supporting.lineHeight,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun InstallTrustPills(signal: os.kei.feature.github.model.GitHubApkTrustSignal) {
    val reasons = signal.reasons.take(6)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (reasons.isEmpty()) {
            StatusPill(
                label = stringResource(R.string.github_apk_trust_detail_reason_empty),
                color = GitHubStatusPalette.Update,
                size = AppStatusPillSize.Compact,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp)
            )
        } else {
            reasons.forEach { reason ->
                StatusPill(
                    label = stringResource(reason.labelRes()),
                    color = reason.statusColor(),
                    size = AppStatusPillSize.Compact,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun ApkCandidateSection(
    state: GitHubApkInstallFlowState,
    context: android.content.Context
) {
    SheetSectionTitle(stringResource(R.string.github_apk_install_phase_selecting))
    SheetSectionCard(
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        verticalSpacing = 6.dp
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(
                items = state.candidates,
                key = { candidate -> candidate.index }
            ) { candidate ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            GitHubApkInstallFlowCoordinator.selectCandidate(candidate.index)
                        }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = candidate.name,
                        modifier = Modifier.weight(1f),
                        color = MiuixTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatAssetSize(candidate.sizeBytes, context),
                        color = MiuixTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun InstallActionSection(
    state: GitHubApkInstallFlowState,
    context: android.content.Context,
    backdrop: LayerBackdrop
) {
    SheetSectionCard(verticalSpacing = 8.dp) {
        InstallBottomResult(state)
        when (state.phase) {
            GitHubApkInstallPhase.ReadyToInstall -> InstallConfirmButtons(state, context, backdrop)
            GitHubApkInstallPhase.PendingUserAction -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppLiquidTextButton(
                    backdrop = backdrop,
                    text = stringResource(R.string.common_cancel),
                    onClick = { GitHubApkInstallFlowCoordinator.cancel(context) },
                    modifier = Modifier.weight(1f),
                    variant = GlassVariant.SheetAction
                )
                AppLiquidTextButton(
                    backdrop = backdrop,
                    text = stringResource(R.string.github_apk_install_action_open_system_confirm),
                    onClick = { GitHubApkInstallFlowCoordinator.launchPendingUserAction(context) },
                    modifier = Modifier.weight(1f),
                    variant = GlassVariant.SheetAction
                )
            }

            GitHubApkInstallPhase.Failed -> {
                AppLiquidTextButton(
                    backdrop = backdrop,
                    text = stringResource(R.string.github_apk_install_action_retry),
                    onClick = { GitHubApkInstallFlowCoordinator.retry(context) },
                    modifier = Modifier.fillMaxWidth(),
                    variant = GlassVariant.SheetAction
                )
                AppLiquidTextButton(
                    backdrop = backdrop,
                    text = stringResource(R.string.github_apk_install_action_external),
                    onClick = { GitHubApkInstallFlowCoordinator.openExternalCurrent(context) },
                    modifier = Modifier.fillMaxWidth(),
                    variant = GlassVariant.SheetAction
                )
            }

            GitHubApkInstallPhase.Success -> AppLiquidTextButton(
                backdrop = backdrop,
                text = stringResource(R.string.common_mark_read),
                onClick = { GitHubApkInstallFlowCoordinator.markRead(context) },
                modifier = Modifier.fillMaxWidth(),
                variant = GlassVariant.SheetAction
            )

            else -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppLiquidTextButton(
                    backdrop = backdrop,
                    text = stringResource(R.string.common_cancel),
                    onClick = { GitHubApkInstallFlowCoordinator.cancel(context) },
                    modifier = Modifier.weight(1f),
                    variant = GlassVariant.SheetAction
                )
                AppLiquidTextButton(
                    backdrop = backdrop,
                    text = stringResource(state.phase.passiveActionLabelRes()),
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.weight(1f),
                    variant = GlassVariant.SheetAction
                )
            }
        }
    }
}

@Composable
private fun InstallConfirmButtons(
    state: GitHubApkInstallFlowState,
    context: android.content.Context,
    backdrop: LayerBackdrop
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AppLiquidTextButton(
            backdrop = backdrop,
            text = stringResource(R.string.github_apk_install_action_more),
            onClick = { GitHubApkInstallFlowCoordinator.openExternalCurrent(context) },
            modifier = Modifier.weight(1f),
            variant = GlassVariant.SheetAction
        )
        AppLiquidTextButton(
            backdrop = backdrop,
            text = stringResource(state.installPrimaryActionLabelRes()),
            onClick = GitHubApkInstallFlowCoordinator::confirmInstall,
            modifier = Modifier.weight(1f),
            variant = GlassVariant.SheetAction
        )
    }
}

private val bottomResultPhases = setOf(
    GitHubApkInstallPhase.ReadyToInstall,
    GitHubApkInstallPhase.PendingUserAction,
    GitHubApkInstallPhase.Success,
    GitHubApkInstallPhase.Failed
)

private fun GitHubApkInstallPhase.labelRes(): Int {
    return when (this) {
        GitHubApkInstallPhase.Downloading -> R.string.github_apk_install_phase_downloading
        GitHubApkInstallPhase.SelectingApk -> R.string.github_apk_install_phase_selecting
        GitHubApkInstallPhase.Inspecting -> R.string.github_apk_install_phase_inspecting
        GitHubApkInstallPhase.ReadyToInstall -> R.string.github_apk_install_phase_ready
        GitHubApkInstallPhase.Installing -> R.string.github_apk_install_phase_installing
        GitHubApkInstallPhase.PendingUserAction -> R.string.github_apk_install_phase_pending
        GitHubApkInstallPhase.Success -> R.string.github_apk_install_phase_success
        GitHubApkInstallPhase.Failed -> R.string.github_apk_install_phase_failed
        GitHubApkInstallPhase.Cancelled -> R.string.github_apk_install_cancelled
        GitHubApkInstallPhase.Idle -> R.string.github_apk_install_phase_installing
    }
}

private fun GitHubApkInstallPhase.statusColor(): Color {
    return when (this) {
        GitHubApkInstallPhase.Success -> GitHubStatusPalette.Update
        GitHubApkInstallPhase.Failed,
        GitHubApkInstallPhase.Cancelled -> GitHubStatusPalette.Error

        GitHubApkInstallPhase.ReadyToInstall,
        GitHubApkInstallPhase.PendingUserAction -> GitHubStatusPalette.Cache

        else -> GitHubStatusPalette.Active
    }
}

private fun Long?.orZero(): Long = this ?: 0L

private fun GitHubApkInstallFlowState.displayFileName(): String {
    return selectedCandidateName
        .ifBlank { asset?.name.orEmpty() }
        .ifBlank { request.externalFileName }
        .ifBlank { request.displayLabel }
}

private fun GitHubApkInstallFlowState.identityName(): String {
    return candidateAppLabel()
        .ifBlank { installedPackageInfo?.appLabel.orEmpty() }
        .ifBlank { request.sourceLabel }
        .ifBlank { request.displayLabel }
}

private fun GitHubApkInstallFlowState.candidateAppLabel(): String {
    return localArchiveInfo?.appLabel.orEmpty()
}

private fun GitHubApkInstallFlowState.candidateVersionName(): String {
    return localArchiveInfo
        ?.versionName
        .orEmpty()
        .ifBlank {
            (remoteManifestInfo ?: request.remoteManifestInfo)
                ?.versionName
                ?.trim()
                .orEmpty()
        }
}

@Composable
private fun GitHubApkInstallFlowState.versionVerdict(): InstallComparisonVerdictModel? {
    val candidateCode = candidateVersionCode() ?: return null
    val localCode = installedPackageInfo?.versionCode?.takeIf { it >= 0L }
    val (labelRes, color) = when {
        localCode == null ->
            R.string.github_apk_install_version_status_new to GitHubStatusPalette.Active

        candidateCode > localCode ->
            R.string.github_apk_install_version_status_upgrade to GitHubStatusPalette.Update

        candidateCode < localCode ->
            R.string.github_apk_install_version_status_downgrade to GitHubStatusPalette.Error

        else ->
            R.string.github_apk_install_version_status_same to GitHubStatusPalette.Cache
    }
    return InstallComparisonVerdictModel(
        label = stringResource(labelRes),
        color = color
    )
}

private fun GitHubApkInstallFlowState.candidateVersionCode(): Long? {
    return localArchiveInfo
        ?.versionCode
        ?.takeIf { it >= 0L }
        ?: (remoteManifestInfo ?: request.remoteManifestInfo)
            ?.versionCode
            ?.trim()
            ?.toLongOrNull()
}

private fun GitHubApkInstallFlowState.candidateVersionCodeLabel(): String {
    return candidateVersionCode()?.toString().orEmpty()
}

private fun GitHubInstalledPackageInfo.versionCodeLabel(): String {
    return versionCode.takeIf { it >= 0L }?.toString().orEmpty()
}

private fun GitHubApkInstallFlowState.candidatePackageName(): String {
    return localArchiveInfo?.packageName.orEmpty()
        .ifBlank { remoteManifestInfo?.packageName.orEmpty() }
        .ifBlank { request.remoteManifestInfo?.packageName.orEmpty() }
        .ifBlank { request.expectedPackageName }
}

private fun GitHubApkInstallFlowState.candidateTargetSdkLabel(): String {
    localArchiveInfo?.targetSdk?.takeIf { it >= 0 }?.let { return it.toString() }
    return (remoteManifestInfo ?: request.remoteManifestInfo)
        ?.targetSdk
        ?.trim()
        .orEmpty()
}

private fun GitHubApkInstallFlowState.candidateMinSdkLabel(): String {
    localArchiveInfo?.minSdk?.takeIf { it >= 0 }?.let { return it.toString() }
    return (remoteManifestInfo ?: request.remoteManifestInfo)
        ?.minSdk
        ?.trim()
        .orEmpty()
}

private fun Int?.sdkLabel(): String {
    return this?.takeIf { it >= 0 }?.toString().orEmpty()
}

@Composable
private fun GitHubApkInstallFlowState.candidateAbiLabel(): String {
    val universal = stringResource(R.string.github_apk_install_value_universal)
    val unknown = stringResource(R.string.github_apk_install_value_unknown)
    val archive = localArchiveInfo
    if (archive != null) {
        return archive.nativeAbis.shortAbiList().ifBlank { universal }
    }
    val remoteAbis = (remoteManifestInfo ?: request.remoteManifestInfo)?.nativeAbis.orEmpty()
    if (remoteAbis.isNotEmpty()) {
        return remoteAbis.shortAbiList()
    }
    return inferAbiNames(displayFileName()).shortAbiList().ifBlank { unknown }
}

private fun GitHubApkInstallFlowState.candidateSignatureShortLabel(): String {
    val localSignature = localArchiveInfo?.signatureSha256?.firstOrNull()
    if (!localSignature.isNullOrBlank()) {
        return localSignature.shortSha()
    }
    val remoteSignature = (remoteManifestInfo ?: request.remoteManifestInfo)
        ?.signatureInfo
        ?.sha256
        .orEmpty()
    if (remoteSignature.isNotBlank()) {
        return remoteSignature.shortSha()
    }
    return ""
}

private fun comparisonValue(
    context: android.content.Context,
    localValue: String,
    candidateValue: String
): String {
    val local = localValue.trim()
    val candidate = candidateValue.trim()
    return when {
        local.isNotBlank() && candidate.isNotBlank() && local != candidate ->
            context.getString(R.string.github_apk_install_reference_compare_value, local, candidate)

        candidate.isNotBlank() -> candidate
        else -> local
    }
}

private fun GitHubApkInstallFlowState.installPrimaryActionLabelRes(): Int {
    val candidateCode = candidateVersionCode()
    val localCode = installedPackageInfo?.versionCode?.takeIf { it >= 0L }
    return when {
        candidateCode != null && localCode != null && candidateCode > localCode ->
            R.string.github_apk_install_action_upgrade

        candidateCode != null && localCode != null && candidateCode < localCode ->
            R.string.github_apk_install_action_downgrade

        else -> R.string.github_apk_install_action_install
    }
}

private fun deviceAbiLabel(): String {
    return Build.SUPPORTED_ABIS
        .orEmpty()
        .toList()
        .shortAbiList()
}

private fun List<String>.shortAbiList(maxItems: Int = 2): String {
    val normalized = map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
    if (normalized.isEmpty()) return ""
    val head = normalized.take(maxItems).joinToString("/")
    val extra = normalized.size - maxItems
    return if (extra > 0) "$head +$extra" else head
}

private fun inferAbiNames(name: String): List<String> {
    val lowerName = name.lowercase()
    return buildList {
        if ("arm64-v8a" in lowerName || "arm64" in lowerName || "aarch64" in lowerName) {
            add("arm64-v8a")
        }
        if ("armeabi-v7a" in lowerName || "armv7" in lowerName || "arm-v7" in lowerName) {
            add("armeabi-v7a")
        }
        if ("x86_64" in lowerName || "x64" in lowerName) {
            add("x86_64")
        }
        if (Regex("""(?:^|[^a-z0-9])x86(?:[^a-z0-9]|$)""").containsMatchIn(lowerName)) {
            add("x86")
        }
    }
}

private fun String.shortSha(): String {
    return trim()
        .replace(":", "")
        .take(12)
        .takeIf { it.isNotBlank() }
        .orEmpty()
}

private fun GitHubApkInstallPhase.passiveActionLabelRes(): Int {
    return when (this) {
        GitHubApkInstallPhase.Installing -> R.string.github_apk_install_phase_installing
        GitHubApkInstallPhase.SelectingApk -> R.string.github_apk_install_phase_selecting
        else -> R.string.github_apk_install_action_preparing
    }
}

private fun GitHubDecisionLevel.statusColor(): Color {
    return when (this) {
        GitHubDecisionLevel.Good -> GitHubStatusPalette.Update
        GitHubDecisionLevel.Review -> GitHubStatusPalette.Cache
        GitHubDecisionLevel.Risk -> GitHubStatusPalette.Error
    }
}

private fun GitHubApkTrustReason.statusColor(): Color {
    return when (this) {
        GitHubApkTrustReason.PackageMismatch,
        GitHubApkTrustReason.SignatureMismatch,
        GitHubApkTrustReason.VersionDowngrade,
        GitHubApkTrustReason.MinSdkTooHigh,
        GitHubApkTrustReason.IncompatibleAbi,
        GitHubApkTrustReason.UnsignedBuild,
        GitHubApkTrustReason.SourceArchive,
        GitHubApkTrustReason.UnknownFormat -> GitHubStatusPalette.Error

        GitHubApkTrustReason.DebugBuild,
        GitHubApkTrustReason.TestOnly,
        GitHubApkTrustReason.SensitivePermission,
        GitHubApkTrustReason.ExportedComponent,
        GitHubApkTrustReason.SignatureUnknown -> GitHubStatusPalette.Cache

        GitHubApkTrustReason.PackageMatched,
        GitHubApkTrustReason.SignatureMatched,
        GitHubApkTrustReason.VersionUpgrade,
        GitHubApkTrustReason.PreferredAbi,
        GitHubApkTrustReason.UniversalAsset -> GitHubStatusPalette.Update

        GitHubApkTrustReason.ApkLike -> GitHubStatusPalette.Active
    }
}

private fun GitHubDecisionLevel.labelRes(): Int {
    return when (this) {
        GitHubDecisionLevel.Good -> R.string.github_apk_trust_good
        GitHubDecisionLevel.Review -> R.string.github_apk_trust_review
        GitHubDecisionLevel.Risk -> R.string.github_apk_trust_risk
    }
}

private fun GitHubApkTrustReason.labelRes(): Int {
    return when (this) {
        GitHubApkTrustReason.PreferredAbi -> R.string.github_apk_trust_reason_preferred_abi
        GitHubApkTrustReason.UniversalAsset -> R.string.github_apk_trust_reason_universal
        GitHubApkTrustReason.IncompatibleAbi -> R.string.github_apk_trust_reason_incompatible
        GitHubApkTrustReason.DebugBuild -> R.string.github_apk_trust_reason_debug
        GitHubApkTrustReason.UnsignedBuild -> R.string.github_apk_trust_reason_unsigned
        GitHubApkTrustReason.SourceArchive -> R.string.github_apk_trust_reason_source
        GitHubApkTrustReason.ApkLike -> R.string.github_apk_trust_reason_apk
        GitHubApkTrustReason.UnknownFormat -> R.string.github_apk_trust_reason_unknown_format
        GitHubApkTrustReason.PackageMatched -> R.string.github_apk_trust_reason_package_matched
        GitHubApkTrustReason.PackageMismatch -> R.string.github_apk_trust_reason_package_mismatch
        GitHubApkTrustReason.SignatureMatched -> R.string.github_apk_trust_reason_signature_matched
        GitHubApkTrustReason.SignatureMismatch -> R.string.github_apk_trust_reason_signature_mismatch
        GitHubApkTrustReason.SignatureUnknown -> R.string.github_apk_trust_reason_signature_unknown
        GitHubApkTrustReason.VersionUpgrade -> R.string.github_apk_trust_reason_version_upgrade
        GitHubApkTrustReason.VersionDowngrade -> R.string.github_apk_trust_reason_version_downgrade
        GitHubApkTrustReason.MinSdkTooHigh -> R.string.github_apk_trust_reason_min_sdk_high
        GitHubApkTrustReason.TestOnly -> R.string.github_apk_trust_reason_test_only
        GitHubApkTrustReason.SensitivePermission -> R.string.github_apk_trust_reason_sensitive_permission
        GitHubApkTrustReason.ExportedComponent -> R.string.github_apk_trust_reason_exported_component
    }
}
