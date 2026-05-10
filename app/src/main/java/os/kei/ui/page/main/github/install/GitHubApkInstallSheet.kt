package os.kei.ui.page.main.github.install

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
@OptIn(ExperimentalLayoutApi::class)
private fun InstallHeader(state: GitHubApkInstallFlowState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            StatusPill(
                label = stringResource(state.phase.labelRes()),
                color = state.phase.statusColor()
            )
            if (
                state.phase == GitHubApkInstallPhase.RemoteReady ||
                state.phase == GitHubApkInstallPhase.ReadyToInstall
            ) {
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
                    text = stringResource(phase.stepLabelRes()),
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
    GitHubApkInstallPhase.RemoteReady,
    GitHubApkInstallPhase.Downloading,
    GitHubApkInstallPhase.ReadyToInstall,
    GitHubApkInstallPhase.Installing,
    GitHubApkInstallPhase.Success
)

private fun GitHubApkInstallFlowState.installStepIndex(): Int {
    return when (phase) {
        GitHubApkInstallPhase.RemoteResolving,
        GitHubApkInstallPhase.RemoteReady -> 0

        GitHubApkInstallPhase.Downloading -> 1
        GitHubApkInstallPhase.SelectingApk,
        GitHubApkInstallPhase.InspectingLocal,
        GitHubApkInstallPhase.ReadyToInstall -> 2
        GitHubApkInstallPhase.Installing,
        GitHubApkInstallPhase.PendingUserAction -> 3

        GitHubApkInstallPhase.Success -> installStepPhases.lastIndex
        GitHubApkInstallPhase.Failed,
        GitHubApkInstallPhase.Cancelled -> when {
            localArchiveInfo != null -> 3
            bytesDone > 0L || candidates.isNotEmpty() -> 1
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
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun InstallBottomResult(state: GitHubApkInstallFlowState) {
    if (state.phase !in bottomResultPhases) return
    state.trustSignal
        .takeIf {
            state.phase == GitHubApkInstallPhase.RemoteReady ||
                    state.phase == GitHubApkInstallPhase.ReadyToInstall
        }
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
            GitHubApkInstallPhase.RemoteReady -> InstallPrepareButtons(
                context = context,
                backdrop = backdrop
            )

            GitHubApkInstallPhase.ReadyToInstall -> InstallConfirmButtons(state, context, backdrop)
            GitHubApkInstallPhase.PendingUserAction -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InstallSheetActionButton(
                    backdrop = backdrop,
                    text = stringResource(R.string.common_stop),
                    onClick = { GitHubApkInstallFlowCoordinator.cancel(context) },
                    modifier = Modifier.weight(1f),
                )
                InstallSheetActionButton(
                    backdrop = backdrop,
                    text = stringResource(R.string.github_apk_install_action_open_system_confirm),
                    onClick = { GitHubApkInstallFlowCoordinator.launchPendingUserAction(context) },
                    modifier = Modifier.weight(1f),
                )
            }

            GitHubApkInstallPhase.Failed -> {
                InstallSheetActionButton(
                    backdrop = backdrop,
                    text = stringResource(R.string.github_apk_install_action_retry),
                    onClick = { GitHubApkInstallFlowCoordinator.retry(context) },
                    modifier = Modifier.fillMaxWidth(),
                )
                InstallSheetActionButton(
                    backdrop = backdrop,
                    text = stringResource(R.string.github_apk_install_action_external),
                    onClick = { GitHubApkInstallFlowCoordinator.openExternalCurrent(context) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            GitHubApkInstallPhase.Success,
            GitHubApkInstallPhase.Cancelled -> InstallSheetActionButton(
                backdrop = backdrop,
                text = stringResource(R.string.common_mark_read),
                onClick = { GitHubApkInstallFlowCoordinator.markRead(context) },
                modifier = Modifier.fillMaxWidth(),
            )

            else -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InstallSheetActionButton(
                    backdrop = backdrop,
                    text = stringResource(
                        if (state.phase == GitHubApkInstallPhase.RemoteResolving) {
                            R.string.common_cancel
                        } else {
                            R.string.common_stop
                        }
                    ),
                    onClick = { GitHubApkInstallFlowCoordinator.cancel(context) },
                    modifier = Modifier.weight(1f),
                )
                InstallSheetActionButton(
                    backdrop = backdrop,
                    text = stringResource(state.phase.passiveActionLabelRes()),
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun InstallPrepareButtons(
    context: android.content.Context,
    backdrop: LayerBackdrop
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        InstallSheetActionButton(
            backdrop = backdrop,
            text = stringResource(R.string.common_cancel),
            onClick = { GitHubApkInstallFlowCoordinator.cancel(context) },
            modifier = Modifier.weight(1f),
        )
        InstallSheetActionButton(
            backdrop = backdrop,
            text = stringResource(R.string.github_apk_install_action_prepare_install),
            onClick = GitHubApkInstallFlowCoordinator::prepareInstall,
            modifier = Modifier.weight(1f),
        )
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
        InstallSheetActionButton(
            backdrop = backdrop,
            text = stringResource(R.string.common_cancel),
            onClick = { GitHubApkInstallFlowCoordinator.cancel(context) },
            modifier = Modifier.weight(1f),
        )
        InstallSheetActionButton(
            backdrop = backdrop,
            text = stringResource(state.installPrimaryActionLabelRes()),
            onClick = GitHubApkInstallFlowCoordinator::confirmInstall,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun InstallSheetActionButton(
    backdrop: LayerBackdrop,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    AppLiquidTextButton(
        backdrop = backdrop,
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        variant = GlassVariant.SheetAction,
        minHeight = 46.dp,
        horizontalPadding = 12.dp,
        textMaxLines = 1,
        textOverflow = TextOverflow.Ellipsis,
        textSoftWrap = false,
        textSize = AppTypographyTokens.Supporting.fontSize,
        textLineHeight = AppTypographyTokens.Supporting.lineHeight
    )
}

private val bottomResultPhases = setOf(
    GitHubApkInstallPhase.RemoteReady,
    GitHubApkInstallPhase.ReadyToInstall,
    GitHubApkInstallPhase.PendingUserAction,
    GitHubApkInstallPhase.Success,
    GitHubApkInstallPhase.Failed,
    GitHubApkInstallPhase.Cancelled
)
