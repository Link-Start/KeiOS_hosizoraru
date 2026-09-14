package os.kei.ui.page.main.github.section

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import os.kei.R
import os.kei.feature.github.data.remote.GitHubVersionUtils
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubReleaseDecisionBasis
import os.kei.feature.github.model.GitHubReleaseDecisionNote
import os.kei.feature.github.model.GitHubReleaseRejection
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.ui.page.main.github.VersionCheckUi
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Why this release, in one sentence, for the cards where the answer would otherwise surprise.
 *
 * The full selection record is kept for diagnostics; almost none of it changes what a reader would
 * do. These are the parts that do, and each of them is a report this pipeline actually received:
 * a project that restarted its numbering shows a release whose number is lower than one further
 * down its own list, and a repository whose tags cannot be parsed shows a release chosen on time
 * alone. An ordinary history ranked by version says nothing here, because saying so on every card
 * would bury the two that need it.
 */
@Composable
internal fun githubSelectionBasisText(note: GitHubReleaseDecisionNote): String? {
    if (!note.explainsStableChoice) return null
    val over = note.stableRunnerUpTag.takeIf { it.isNotBlank() }
    return when (note.stableBasis) {
        GitHubReleaseDecisionBasis.ForgeLatest -> when (over) {
            null -> stringResource(R.string.github_item_selection_forge_latest)
            else -> stringResource(R.string.github_item_selection_forge_latest_over, over)
        }
        GitHubReleaseDecisionBasis.VersioningReset -> when (over) {
            null -> stringResource(R.string.github_item_selection_reset)
            else -> stringResource(R.string.github_item_selection_reset_over, over)
        }
        GitHubReleaseDecisionBasis.FeedOnly ->
            stringResource(R.string.github_item_selection_feed_only)
        GitHubReleaseDecisionBasis.UpdateTime ->
            stringResource(R.string.github_item_selection_freshness)
        GitHubReleaseDecisionBasis.ListOrder ->
            stringResource(R.string.github_item_selection_tie)
        GitHubReleaseDecisionBasis.Ranked -> null
    }
}

/**
 * Whether to account for a pre-release row that is missing rather than absent.
 *
 * Only the retired line qualifies. A pre-release superseded by the stable that followed it, or
 * wearing the same version as one, is the ordinary shape of a release history and explaining it
 * every time would be noise -- both are still recorded, they are simply not news. A line nobody has
 * published to for a fortnight is news, because the row was there yesterday.
 */
@Composable
internal fun githubRetiredPreReleaseText(
    item: GitHubTrackedApp,
    itemLookupConfig: GitHubLookupConfig,
    note: GitHubReleaseDecisionNote,
): String? {
    if (note.preReleaseRejection != GitHubReleaseRejection.AbandonedLine) return null
    // Only for a reader who asked to see pre-releases. To anyone else the row was never there.
    val tracksPreReleases = item.preferPreRelease || itemLookupConfig.checkAllTrackedPreReleases
    if (!tracksPreReleases) return null
    return stringResource(R.string.github_item_prerelease_retired_reason)
}

/** The sentence, with the release list it was decided from one tap away. */
@Suppress("FunctionName")
@Composable
internal fun GitHubSelectionBasisCard(
    item: GitHubTrackedApp,
    text: String,
    actions: GitHubTrackedItemsActions,
) {
    GitHubLinkedInfoCard(
        label = stringResource(R.string.github_item_label_selection_basis),
        value = text,
        labelColor = MiuixTheme.colorScheme.onBackgroundVariant,
        valueColor = MiuixTheme.colorScheme.onBackgroundVariant,
        valueMaxLines = 3,
        onClick = {
            actions.onOpenExternalUrl(GitHubVersionUtils.buildReleaseUrl(item.owner, item.repo))
        },
    )
}

/** Stands where the pre-release row would be, for the one case where its absence is a change. */
@Suppress("FunctionName")
@Composable
internal fun GitHubRetiredPreReleaseCard(
    item: GitHubTrackedApp,
    state: VersionCheckUi,
    text: String,
    actions: GitHubTrackedItemsActions,
) {
    GitHubLinkedInfoCard(
        label = stringResource(R.string.github_item_label_prerelease_retired),
        value = text,
        labelColor = MiuixTheme.colorScheme.onBackgroundVariant,
        valueColor = MiuixTheme.colorScheme.onBackgroundVariant,
        valueMaxLines = 3,
        onClick = {
            actions.onOpenExternalUrl(
                state.latestPreUrl.trim().ifBlank {
                    GitHubVersionUtils.buildReleaseUrl(item.owner, item.repo)
                },
            )
        },
    )
}
