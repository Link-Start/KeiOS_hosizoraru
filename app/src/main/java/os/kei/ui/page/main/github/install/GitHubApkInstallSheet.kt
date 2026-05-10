package os.kei.ui.page.main.github.install

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import os.kei.R
import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubApkTrustReason
import os.kei.feature.github.model.GitHubDecisionLevel
import os.kei.feature.github.model.GitHubInstalledPackageInfo
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
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
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        verticalSpacing = 6.dp
    ) {
        InstallHeader(state = state)
        if (state.phase in progressPhases) {
            LiquidLinearProgressBar(
                progress = { state.progress.coerceIn(0f, 1f) },
                height = 5.dp,
                modifier = Modifier.fillMaxWidth()
            )
        }
        InstallReferenceRows(state = state, context = context)
    }
}

@Composable
private fun InstallHeader(state: GitHubApkInstallFlowState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusPill(
            label = stringResource(state.phase.labelRes()),
            color = state.phase.statusColor()
        )
        if (state.phase in progressPhases) {
            Text(
                text = stringResource(
                    R.string.github_apk_install_summary_progress,
                    (state.progress * 100).toInt().coerceIn(0, 100)
                ),
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                fontSize = AppTypographyTokens.Supporting.fontSize,
                lineHeight = AppTypographyTokens.Supporting.lineHeight
            )
        }
    }
    Text(
        text = state.displayFileName(),
        color = MiuixTheme.colorScheme.onBackground,
        fontSize = AppTypographyTokens.Body.fontSize,
        lineHeight = AppTypographyTokens.Body.lineHeight,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
    state.message
        .takeIf { it.isNotBlank() && state.phase !in bottomResultPhases }
        ?.let { message ->
            SheetDescriptionText(text = message)
        }
}

@Composable
private fun InstallReferenceRows(
    state: GitHubApkInstallFlowState,
    context: android.content.Context
) {
    val displaySize = state.selectedCandidateSizeBytes
        .takeIf { it > 0L }
        ?: state.asset?.sizeBytes.orZero()
    val localSize = state.installedPackageInfo?.sourceSizeBytes.orZero()
    state.comparisonLabel(
        local = state.installedPackageInfo?.packageName.orEmpty(),
        candidate = state.candidatePackageName()
    ).takeIf { it.isNotBlank() }?.let {
        InfoRow(stringResource(R.string.github_apk_install_reference_package), it)
    }
    state.comparisonLabel(
        local = state.installedPackageInfo?.versionLabel().orEmpty(),
        candidate = state.candidateVersionLabel()
    ).takeIf { it.isNotBlank() }?.let {
        InfoRow(stringResource(R.string.github_apk_install_reference_version_compare), it)
    }
    state.comparisonLabel(
        local = state.installedPackageInfo?.sdkShortLabel().orEmpty(),
        candidate = state.candidateSdkShortLabel()
    ).takeIf { it.isNotBlank() }?.let {
        InfoRow(stringResource(R.string.github_apk_install_reference_sdk), it)
    }
    if (displaySize > 0L) {
        InfoRow(
            stringResource(R.string.github_apk_install_reference_size),
            state.comparisonLabel(
                local = localSize.takeIf { it > 0L }?.let { formatAssetSize(it, context) }
                    .orEmpty(),
                candidate = formatAssetSize(displaySize, context)
            )
        )
    }
    state.comparisonLabel(
        local = deviceAbiLabel(),
        candidate = state.candidateAbiLabel()
    ).takeIf { it.isNotBlank() }?.let {
        InfoRow(stringResource(R.string.github_apk_install_reference_abi), it)
    }
    state.comparisonLabel(
        local = state.installedPackageInfo?.signatureSha256?.firstOrNull()?.shortSha().orEmpty(),
        candidate = state.candidateSignatureShortLabel()
    ).takeIf { it.isNotBlank() }?.let {
        InfoRow(stringResource(R.string.github_apk_install_reference_signature), it)
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
private fun InfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.28f),
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            fontSize = AppTypographyTokens.Supporting.fontSize,
            lineHeight = AppTypographyTokens.Supporting.lineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            modifier = Modifier.weight(0.72f),
            color = MiuixTheme.colorScheme.primary,
            fontSize = AppTypographyTokens.Caption.fontSize,
            lineHeight = AppTypographyTokens.Caption.lineHeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
            GitHubApkInstallPhase.ReadyToInstall -> InstallConfirmButtons(context, backdrop)
            GitHubApkInstallPhase.PendingUserAction -> AppLiquidTextButton(
                backdrop = backdrop,
                text = stringResource(R.string.github_apk_install_action_open_system_confirm),
                onClick = { GitHubApkInstallFlowCoordinator.launchPendingUserAction(context) },
                modifier = Modifier.fillMaxWidth(),
                variant = GlassVariant.SheetAction
            )

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
    context: android.content.Context,
    backdrop: LayerBackdrop
) {
    Row(
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
            text = stringResource(R.string.github_apk_install_action_install),
            onClick = GitHubApkInstallFlowCoordinator::confirmInstall,
            modifier = Modifier.weight(1f),
            variant = GlassVariant.SheetAction
        )
    }
}

private val progressPhases = setOf(
    GitHubApkInstallPhase.Downloading,
    GitHubApkInstallPhase.Inspecting,
    GitHubApkInstallPhase.Installing
)

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

@Composable
private fun GitHubApkInstallFlowState.candidateVersionLabel(): String {
    val unknownValue = stringResource(R.string.github_apk_install_value_unknown)
    val archive = localArchiveInfo
    if (archive != null && (archive.versionName.isNotBlank() || archive.versionCode >= 0L)) {
        return stringResource(
            R.string.github_apk_install_reference_version_value,
            archive.versionName.ifBlank { unknownValue },
            archive.versionCode
        )
    }
    val remote = remoteManifestInfo ?: request.remoteManifestInfo
    return remote.versionLabel()
}

private fun GitHubApkInstallFlowState.candidatePackageName(): String {
    return localArchiveInfo?.packageName.orEmpty()
        .ifBlank { remoteManifestInfo?.packageName.orEmpty() }
        .ifBlank { request.remoteManifestInfo?.packageName.orEmpty() }
        .ifBlank { request.expectedPackageName }
}

private fun GitHubInstalledPackageInfo.versionLabel(): String {
    return when {
        versionName.isNotBlank() && versionCode >= 0L -> "$versionName ($versionCode)"
        versionName.isNotBlank() -> versionName
        versionCode >= 0L -> versionCode.toString()
        else -> ""
    }
}

@Composable
private fun GitHubApkInstallFlowState.comparisonLabel(
    local: String,
    candidate: String
): String {
    val localLabel = local.trim()
    val candidateLabel = candidate.trim()
    return when {
        localLabel.isNotBlank() && candidateLabel.isNotBlank() && localLabel == candidateLabel ->
            stringResource(
                R.string.github_apk_install_reference_same_value,
                candidateLabel
            )

        localLabel.isNotBlank() && candidateLabel.isNotBlank() -> stringResource(
            R.string.github_apk_install_reference_compare_value,
            localLabel,
            candidateLabel
        )

        candidateLabel.isNotBlank() -> candidateLabel
        localLabel.isNotBlank() -> localLabel
        else -> ""
    }
}

private fun GitHubInstalledPackageInfo.sdkShortLabel(): String {
    return sdkShortLabel(minSdk, targetSdk)
}

private fun GitHubApkInstallFlowState.candidateSdkShortLabel(): String {
    val archive = localArchiveInfo
    if (archive != null && (archive.minSdk >= 0 || archive.targetSdk >= 0)) {
        return sdkShortLabel(archive.minSdk, archive.targetSdk)
    }
    val remote = remoteManifestInfo ?: request.remoteManifestInfo
    val minSdk = remote?.minSdk?.trim().orEmpty()
    val targetSdk = remote?.targetSdk?.trim().orEmpty()
    return when {
        minSdk.isNotBlank() && targetSdk.isNotBlank() -> "$minSdk/$targetSdk"
        minSdk.isNotBlank() -> "min $minSdk"
        targetSdk.isNotBlank() -> "target $targetSdk"
        else -> ""
    }
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

private fun sdkShortLabel(minSdk: Int, targetSdk: Int): String {
    return when {
        minSdk >= 0 && targetSdk >= 0 -> "$minSdk/$targetSdk"
        minSdk >= 0 -> "min $minSdk"
        targetSdk >= 0 -> "target $targetSdk"
        else -> ""
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

private fun GitHubApkManifestInfo?.versionLabel(): String {
    if (this == null) return ""
    val name = versionName.trim()
    val code = versionCode.trim()
    return when {
        name.isNotBlank() && code.isNotBlank() -> "$name ($code)"
        name.isNotBlank() -> name
        code.isNotBlank() -> code
        else -> ""
    }
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
