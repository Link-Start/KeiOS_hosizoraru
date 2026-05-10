package os.kei.ui.page.main.github.sheet

import androidx.annotation.StringRes
import os.kei.R

internal enum class GitHubCheckSheetCategory(
    @param:StringRes val titleRes: Int,
    @param:StringRes val menuLabelRes: Int,
    @param:StringRes val summaryRes: Int
) {
    UpdateChecks(
        titleRes = R.string.github_check_sheet_title_checks,
        menuLabelRes = R.string.github_check_sheet_section_checks,
        summaryRes = R.string.github_check_sheet_section_checks_summary
    ),
    InstallFlow(
        titleRes = R.string.github_check_sheet_title_download_install,
        menuLabelRes = R.string.github_check_sheet_section_transfer,
        summaryRes = R.string.github_check_sheet_section_transfer_summary
    ),
    ShareImport(
        titleRes = R.string.github_check_sheet_title_share_import,
        menuLabelRes = R.string.github_check_sheet_section_share_import,
        summaryRes = R.string.github_check_sheet_section_share_import_summary
    ),
    Insights(
        titleRes = R.string.github_check_sheet_title_enhancements,
        menuLabelRes = R.string.github_check_sheet_section_enhancements,
        summaryRes = R.string.github_check_sheet_section_enhancements_summary
    ),
    TrackData(
        titleRes = R.string.github_check_sheet_title_tracks,
        menuLabelRes = R.string.github_check_sheet_section_tracks,
        summaryRes = R.string.github_check_sheet_section_tracks_summary
    ),
    Notes(
        titleRes = R.string.github_check_sheet_title_notes,
        menuLabelRes = R.string.github_check_sheet_section_notes,
        summaryRes = R.string.github_check_sheet_section_notes_summary
    )
}
