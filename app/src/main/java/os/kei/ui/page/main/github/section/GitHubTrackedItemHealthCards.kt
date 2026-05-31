package os.kei.ui.page.main.github.section

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import os.kei.R
import os.kei.feature.github.model.GitHubDirectApkRemoteHealth
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.isDirectApkTrack
import os.kei.ui.page.main.github.GitHubRepositoryHealth
import os.kei.ui.page.main.github.GitHubStatusPalette
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.github.asset.formatReleaseUpdatedAtCompact
import os.kei.ui.page.main.github.repositoryHealthLabelRes
import os.kei.ui.page.main.github.repositoryHealthStatusColor
import os.kei.ui.page.main.widget.core.AppStatusPillSize
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.glass.LocalLiquidParentBackdrop
import os.kei.ui.page.main.widget.status.StatusPill
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Suppress("FunctionName")
@Composable
internal fun GitHubDirectApkRemoteHealthCard(
    item: GitHubTrackedApp,
    state: VersionCheckUi,
    onOpenExternalUrl: (String) -> Unit,
) {
    if (!item.isDirectApkTrack()) return
    val checkedAt = formatReleaseUpdatedAtCompact(state.directApkRemoteCheckedAtMillis)
    val reason = state.directApkRemoteHealthMessage.directApkRemoteHealthReason()
    val health =
        when {
            state.loading -> GitHubDirectApkRemoteHealth.Unknown
            else -> state.directApkRemoteHealth
        }
    val value =
        when {
            state.loading -> {
                stringResource(R.string.github_direct_apk_remote_health_checking)
            }

            health == GitHubDirectApkRemoteHealth.Available && checkedAt != null -> {
                stringResource(R.string.github_direct_apk_remote_health_available_at, checkedAt)
            }

            health == GitHubDirectApkRemoteHealth.Available -> {
                stringResource(R.string.github_direct_apk_remote_health_available)
            }

            health == GitHubDirectApkRemoteHealth.Degraded &&
                reason.isNotBlank() &&
                checkedAt != null -> {
                stringResource(
                    R.string.github_direct_apk_remote_health_degraded_reason_at,
                    reason,
                    checkedAt,
                )
            }

            health == GitHubDirectApkRemoteHealth.Degraded && reason.isNotBlank() -> {
                stringResource(R.string.github_direct_apk_remote_health_degraded_reason, reason)
            }

            health == GitHubDirectApkRemoteHealth.Degraded -> {
                stringResource(R.string.github_direct_apk_remote_health_degraded)
            }

            else -> {
                stringResource(R.string.github_direct_apk_remote_health_unknown)
            }
        }
    val color =
        when {
            state.loading -> GitHubStatusPalette.Active
            health == GitHubDirectApkRemoteHealth.Available -> GitHubStatusPalette.Update
            health == GitHubDirectApkRemoteHealth.Degraded -> GitHubStatusPalette.Cache
            else -> MiuixTheme.colorScheme.onBackgroundVariant
        }
    GitHubLinkedInfoCard(
        label = stringResource(R.string.github_item_label_direct_apk_remote_health),
        value = value,
        labelColor = color,
        valueColor = color,
        valueMaxLines = 2,
        onClick = { onOpenExternalUrl(item.repoUrl) },
    )
}

private fun String.directApkRemoteHealthReason(): String {
    val message = trim()
    if (message.isBlank()) return ""
    Regex("""HTTP\s+(\d{3})""", RegexOption.IGNORE_CASE)
        .find(message)
        ?.let { match -> return "HTTP ${match.groupValues[1]}" }
    return message
        .substringBefore('\n')
        .take(36)
        .trim()
}

@Suppress("FunctionName")
@Composable
internal fun GitHubHealthPreviewBlock(
    health: GitHubRepositoryHealth,
    onClick: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val localBackdrop = rememberLayerBackdrop()
    val parentBackdrop = LocalLiquidParentBackdrop.current
    val activeBackdrop = parentBackdrop ?: localBackdrop
    val color = health.level.repositoryHealthStatusColor()
    val surfaceColor =
        if (isDark) {
            MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.56f)
        } else {
            MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.76f)
        }
    GitHubInlineLiquidSurface(
        backdrop = activeBackdrop,
        captureBackdrop = if (parentBackdrop == null) localBackdrop else null,
        tint = color.copy(alpha = if (isDark) 0.16f else 0.10f),
        surfaceColor = surfaceColor,
        onClick = onClick,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.github_item_label_health_score),
                    color = MiuixTheme.colorScheme.primary,
                    fontSize = AppTypographyTokens.Body.fontSize,
                    lineHeight = AppTypographyTokens.Body.lineHeight,
                    fontWeight = AppTypographyTokens.BodyEmphasis.fontWeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                StatusPill(
                    label =
                        stringResource(
                            R.string.github_health_score_level_value,
                            health.score,
                            stringResource(health.level.repositoryHealthLabelRes()),
                        ),
                    color = color,
                    size = AppStatusPillSize.Compact,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}
