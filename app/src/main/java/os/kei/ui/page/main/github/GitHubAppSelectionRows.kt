package os.kei.ui.page.main.github

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.capsule.ContinuousCapsule
import kotlinx.coroutines.withContext
import os.kei.R
import os.kei.core.concurrency.AppDispatchers
import os.kei.feature.github.data.local.AppIconCache
import os.kei.feature.github.model.InstalledAppItem
import os.kei.ui.page.main.widget.core.AppStatusPillSize
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.core.rememberAppStatusPillMetrics
import os.kei.ui.page.main.widget.sheet.SheetSurfaceCard
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun GitHubSelectedAppCard(
    selectedApp: InstalledAppItem,
    showInstallSource: Boolean = false
) {
    SheetSurfaceCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = GitHubStatusPalette.tonedSurface(
            GitHubStatusPalette.Update,
            isDark = isSystemInDarkTheme()
        ),
        borderColor = GitHubStatusPalette.Update.copy(alpha = 0.28f),
        verticalSpacing = 0.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(packageName = selectedApp.packageName, size = 38.dp)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = selectedApp.label,
                    color = MiuixTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    fontSize = AppTypographyTokens.Body.fontSize,
                    lineHeight = AppTypographyTokens.Body.lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = selectedApp.packageName,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    fontSize = AppTypographyTokens.Supporting.fontSize,
                    lineHeight = AppTypographyTokens.Supporting.lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (showInstallSource) {
                InstallSourcePill(label = selectedApp.installSourceDisplayLabel())
            }
        }
    }
}

@Composable
internal fun GitHubAppCandidateRow(
    app: InstalledAppItem,
    selected: Boolean,
    showInstallSource: Boolean,
    onClick: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val accent = if (selected) GitHubStatusPalette.Update else MiuixTheme.colorScheme.primary
    SheetSurfaceCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = if (selected) {
            GitHubStatusPalette.tonedSurface(GitHubStatusPalette.Update, isDark)
        } else {
            null
        },
        borderColor = if (selected) {
            GitHubStatusPalette.Update.copy(alpha = 0.3f)
        } else {
            MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.12f)
        },
        verticalSpacing = 0.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(packageName = app.packageName, size = 32.dp)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = app.label,
                    color = accent,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = AppTypographyTokens.Body.fontSize,
                    lineHeight = AppTypographyTokens.Body.lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    fontSize = AppTypographyTokens.Supporting.fontSize,
                    lineHeight = AppTypographyTokens.Supporting.lineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (showInstallSource) {
                InstallSourcePill(
                    label = app.installSourceDisplayLabel(),
                    selected = selected
                )
            }
        }
    }
}

@Composable
private fun InstalledAppItem.installSourceDisplayLabel(): String {
    return installSourceLabel
        .ifBlank { installSourcePackageName }
        .ifBlank { stringResource(R.string.github_track_sheet_app_install_source_unknown) }
}

@Composable
private fun InstallSourcePill(
    label: String,
    selected: Boolean = false
) {
    val color = if (selected) GitHubStatusPalette.Update else MiuixTheme.colorScheme.primary
    val isDark = isSystemInDarkTheme()
    val metrics = rememberAppStatusPillMetrics(AppStatusPillSize.Compact)
    Box(
        modifier = Modifier
            .widthIn(max = 156.dp)
            .clip(ContinuousCapsule)
            .background(color.copy(alpha = if (isDark) 0.16f else 0.2f))
            .border(
                width = 0.8.dp,
                color = color.copy(alpha = if (isDark) 0.32f else 0.4f),
                shape = ContinuousCapsule
            )
            .padding(metrics.contentPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isDark) color else color.copy(alpha = 0.96f),
            fontSize = metrics.typography.fontSize,
            lineHeight = metrics.typography.lineHeight,
            fontWeight = metrics.typography.fontWeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun AppIcon(
    packageName: String,
    size: Dp,
    localRefreshKey: Any? = Unit
) {
    val normalizedPackageName = packageName.trim()
    val context = LocalContext.current
    val bitmapState = produceState<Bitmap?>(
        initialValue = AppIconCache.get(normalizedPackageName),
        normalizedPackageName,
        localRefreshKey
    ) {
        if (normalizedPackageName.isBlank()) return@produceState
        if (value == null) {
            value = withContext(AppDispatchers.githubNetwork) {
                AppIconCache.getOrLoad(context, normalizedPackageName)
            }
        }
    }
    val bitmap = bitmapState.value
    when {
        bitmap != null -> {
            val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
            Image(
                bitmap = imageBitmap,
                contentDescription = normalizedPackageName,
                modifier = Modifier
                    .width(size)
                    .height(size)
                    .clip(ContinuousCapsule)
            )
        }

        else -> AppIconFallback(size = size)
    }
}

@Composable
private fun AppIconFallback(size: Dp) {
    Box(
        modifier = Modifier
            .width(size)
            .height(size)
            .clip(ContinuousCapsule),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.github_strategy_app_fallback),
            color = MiuixTheme.colorScheme.primary,
            fontSize = AppTypographyTokens.Caption.fontSize,
            lineHeight = AppTypographyTokens.Caption.lineHeight
        )
    }
}
