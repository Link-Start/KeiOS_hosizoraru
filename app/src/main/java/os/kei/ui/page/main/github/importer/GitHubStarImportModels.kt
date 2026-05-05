package os.kei.ui.page.main.github.importer

import androidx.annotation.StringRes
import os.kei.R
import os.kei.feature.github.model.GitHubStarredRepositoryImportSource

internal enum class StarImportViewFilter(@param:StringRes val labelRes: Int) {
    All(R.string.github_star_import_filter_all),
    Importable(R.string.github_star_import_filter_importable),
    Selected(R.string.github_star_import_filter_selected),
    Tracked(R.string.github_star_import_filter_tracked)
}

internal enum class StarImportUiSource(
    @param:StringRes val labelRes: Int,
    @param:StringRes val requirementMessageRes: Int,
    @param:StringRes val sampleRes: Int
) {
    MyStars(
        labelRes = R.string.github_star_import_source_my_stars,
        requirementMessageRes = R.string.github_star_import_requirement_token,
        sampleRes = R.string.github_star_import_sample_token
    ),
    PublicUser(
        labelRes = R.string.github_star_import_source_user,
        requirementMessageRes = R.string.github_star_import_requirement_username,
        sampleRes = R.string.github_star_import_sample_username
    ),
    ListUrl(
        labelRes = R.string.github_star_import_source_list_url,
        requirementMessageRes = R.string.github_star_import_requirement_list_url,
        sampleRes = R.string.github_star_import_sample_list_url
    );

    fun isReady(
        token: String,
        username: String,
        listUrl: String
    ): Boolean {
        return when (this) {
            MyStars -> token.trim().isNotBlank()
            PublicUser -> username.trim().isNotBlank()
            ListUrl -> listUrl.trim().isValidGitHubStarListInput()
        }
    }

    fun toRequestSource(): GitHubStarredRepositoryImportSource {
        return when (this) {
            MyStars -> GitHubStarredRepositoryImportSource.AuthenticatedUser
            PublicUser -> GitHubStarredRepositoryImportSource.PublicUser
            ListUrl -> GitHubStarredRepositoryImportSource.StarListUrl
        }
    }
}

internal fun String.isValidGitHubStarListInput(): Boolean {
    val value = trim().trimEnd('/')
    return value.startsWith("/stars/", ignoreCase = true) ||
            value.startsWith("stars/", ignoreCase = true) ||
            value.startsWith("https://github.com/stars/", ignoreCase = true) ||
            value.startsWith("http://github.com/stars/", ignoreCase = true)
}

internal fun Int.formatStarCount(): String {
    return when {
        this >= 1_000_000 -> String.format("%.1fm", this / 1_000_000.0).trimTrailingZeroDecimal()
        this >= 1_000 -> String.format("%.1fk", this / 1_000.0).trimTrailingZeroDecimal()
        else -> toString()
    }
}

private fun String.trimTrailingZeroDecimal(): String {
    return replace(".0", "")
}
