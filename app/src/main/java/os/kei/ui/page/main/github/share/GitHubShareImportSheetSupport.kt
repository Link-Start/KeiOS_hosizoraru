@file:Suppress("FunctionName", "ktlint:standard:property-naming")

package os.kei.ui.page.main.github.share

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import os.kei.ui.page.main.widget.core.AppInfoRow
import top.yukonga.miuix.kmp.layout.BottomSheetDefaults

internal val shareImportSheetInsideMargin =
    DpSize(
        BottomSheetDefaults.insideMargin.width,
        20.dp,
    )

private const val shareImportInfoLabelWeight = 0.24f

internal val GitHubShareImportManagedInstallProgress?.isRunningInstallPhase: Boolean
    get() =
        this?.phase in
            setOf(
                GitHubShareImportPhase.InstallDownloading,
                GitHubShareImportPhase.Installing,
                GitHubShareImportPhase.InstallCommitting,
            )

internal val GitHubShareImportManagedInstallProgress?.hasActiveInstallSession: Boolean
    get() =
        isRunningInstallPhase ||
            this?.phase == GitHubShareImportPhase.InstallReady

internal fun Modifier.shareImportSheetTags(): Modifier =
    this
        .fillMaxWidth()
        .semantics { testTagsAsResourceId = true }

internal fun Modifier.shareImportSheetSafeArea(): Modifier =
    this
        .shareImportSheetTags()
        .navigationBarsPadding()
        .imePadding()
        .padding(bottom = 12.dp)

@Composable
internal fun ShareImportCompactInfoRow(
    key: String,
    value: String,
) {
    AppInfoRow(
        label = key,
        value = value,
        labelWeight = shareImportInfoLabelWeight,
        valueWeight = 1f - shareImportInfoLabelWeight,
        valueTextAlign = TextAlign.Start,
        horizontalSpacing = 8.dp,
        rowVerticalPadding = 2.dp,
    )
}

internal fun compactShareImportProjectValue(preview: GitHubShareImportPreview): String {
    val owner = preview.owner.trim()
    val repo = preview.repo.trim()
    if (owner.isNotBlank() && repo.isNotBlank()) {
        return "$owner/$repo"
    }
    val rawProjectUrl = preview.projectUrl.trim()
    val compacted =
        rawProjectUrl
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .removePrefix("https://www.github.com/")
            .removePrefix("http://www.github.com/")
            .trim('/')
    return compacted.ifBlank { rawProjectUrl }
}
