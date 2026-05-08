package os.kei.feature.github.data.remote

import os.kei.feature.github.model.GitHubRepositoryActivityProfile
import os.kei.feature.github.model.GitHubRepositoryCommunityProfile
import os.kei.feature.github.model.GitHubRepositoryIdentityProfile
import os.kei.feature.github.model.GitHubRepositoryLifecycleProfile
import os.kei.feature.github.model.GitHubRepositoryProfileConfidence
import os.kei.feature.github.model.GitHubRepositoryProfileSnapshot
import os.kei.feature.github.model.GitHubRepositoryProfileSource
import java.util.Locale

internal class GitHubHtmlRepositoryProfileSource(
    private val http: GitHubRepositoryProfileHttpClient
) {
    fun fetch(
        owner: String,
        repo: String,
        fetchedAtMillis: Long,
        sourceConfigSignature: String
    ): Result<GitHubRepositoryProfileSnapshot> {
        return http.fetchHtml(http.htmlRepositoryUrl(owner, repo)).mapCatching { html ->
            parse(
                html = html,
                owner = owner,
                repo = repo,
                fetchedAtMillis = fetchedAtMillis,
                sourceConfigSignature = sourceConfigSignature
            )
        }
    }

    fun parse(
        html: String,
        owner: String,
        repo: String,
        fetchedAtMillis: Long,
        sourceConfigSignature: String
    ): GitHubRepositoryProfileSnapshot {
        val source = GitHubRepositoryProfileSource.HtmlRepositoryPage
        val archived = html.contains("repository has been archived", ignoreCase = true) ||
                html.contains("This repository has been archived", ignoreCase = true) ||
                html.contains("archived by the owner", ignoreCase = true)
        val identity = GitHubRepositoryIdentityProfile(
            owner = profileField(
                owner,
                source,
                fetchedAtMillis,
                GitHubRepositoryProfileConfidence.Low
            ),
            name = profileField(
                repo,
                source,
                fetchedAtMillis,
                GitHubRepositoryProfileConfidence.Low
            ),
            fullName = profileField(
                "$owner/$repo",
                source,
                fetchedAtMillis,
                GitHubRepositoryProfileConfidence.Low
            ),
            htmlUrl = profileField(
                http.htmlRepositoryUrl(owner, repo),
                source,
                fetchedAtMillis,
                GitHubRepositoryProfileConfidence.Low
            ),
            defaultBranch = stringField(
                html.extractDefaultBranch(),
                source,
                fetchedAtMillis,
                GitHubRepositoryProfileConfidence.Low
            ),
            topics = listField(
                html.extractTopics(),
                source,
                fetchedAtMillis,
                GitHubRepositoryProfileConfidence.Low
            )
        )
        val lifecycle = GitHubRepositoryLifecycleProfile(
            archived = profileField(
                archived,
                source,
                fetchedAtMillis,
                GitHubRepositoryProfileConfidence.Low
            )
        )
        val activity = GitHubRepositoryActivityProfile(
            pushedAtMillis = longField(
                html.extractLatestRelativeTimeMillis(),
                source,
                fetchedAtMillis,
                GitHubRepositoryProfileConfidence.Low
            ),
            stargazersCount = html.extractVisibleCount(
                owner = owner,
                repo = repo,
                path = "stargazers"
            )?.let { intField(it, source, fetchedAtMillis, GitHubRepositoryProfileConfidence.Low) },
            forksCount = html.extractVisibleCount(
                owner = owner,
                repo = repo,
                path = "forks"
            )?.let { intField(it, source, fetchedAtMillis, GitHubRepositoryProfileConfidence.Low) },
            watchersCount = html.extractVisibleCount(
                owner = owner,
                repo = repo,
                path = "watchers"
            )?.let { intField(it, source, fetchedAtMillis, GitHubRepositoryProfileConfidence.Low) },
            openIssuesCount = html.extractVisibleCount(
                owner = owner,
                repo = repo,
                path = "issues"
            )?.let { intField(it, source, fetchedAtMillis, GitHubRepositoryProfileConfidence.Low) }
        )
        val community = GitHubRepositoryCommunityProfile(
            hasReadme = profileField(
                html.hasReadmeSection(),
                source,
                fetchedAtMillis,
                GitHubRepositoryProfileConfidence.Low
            )
        )
        return GitHubRepositoryProfileSnapshot(
            owner = owner,
            repo = repo,
            sourceConfigSignature = sourceConfigSignature,
            fetchedAtMillis = fetchedAtMillis,
            identity = identity,
            lifecycle = lifecycle,
            activity = activity,
            community = community,
            sourceAvailability = listOf(loaded(source, fetchedAtMillis))
        )
    }

    private fun String.extractTopics(): List<String> {
        val encoded = Regex("""href=["']/topics/([^"']+)["']""")
            .findAll(this)
            .map { it.groupValues[1].trim().lowercase(Locale.ROOT) }
            .filter { it.isNotBlank() }
        val json = Regex(""""topicNames"\s*:\s*\[(.*?)]""")
            .find(this)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
            .let { raw ->
                Regex(""""([^"]+)"""")
                    .findAll(raw)
                    .map { it.groupValues[1].trim().lowercase(Locale.ROOT) }
            }
        return (encoded + json).distinct().take(20).toList()
    }

    private fun String.extractDefaultBranch(): String {
        val patterns = listOf(
            Regex(""""defaultBranch"\s*:\s*"([^"]+)""""),
            Regex("""data-default-branch=["']([^"']+)["']"""),
            Regex("""href=["'][^"']+/tree/([^/"']+)["']""")
        )
        return patterns.firstNotNullOfOrNull { pattern ->
            pattern.find(this)?.groupValues?.getOrNull(1)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }.orEmpty()
    }

    private fun String.hasReadmeSection(): Boolean {
        return contains("id=\"readme\"", ignoreCase = true) ||
                contains("""id='readme'""", ignoreCase = true) ||
                contains("README.md", ignoreCase = true) ||
                contains("readme-toc", ignoreCase = true)
    }

    private fun String.extractLatestRelativeTimeMillis(): Long {
        return Regex("""<relative-time[^>]*datetime=["']([^"']+)["']""")
            .find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.parseIsoInstantOrDefault()
            ?: -1L
    }

    private fun String.extractVisibleCount(
        owner: String,
        repo: String,
        path: String
    ): Int? {
        val hrefPattern = Regex(
            """href=["'][^"']*/${Regex.escape(owner)}/${Regex.escape(repo)}/$path["'][^>]*>(.*?)</a>""",
            RegexOption.IGNORE_CASE
        )
        val hrefMatch = hrefPattern.find(this)?.groupValues?.getOrNull(1).orEmpty()
        return hrefMatch.parseCompactCountOrNull()
            ?: Regex("""([0-9][0-9,.]*\s*[kKmM]?)\s+$path""")
                .find(this)
                ?.groupValues
                ?.getOrNull(1)
                ?.parseCompactCountOrNull()
    }

    private fun String.parseCompactCountOrNull(): Int? {
        val raw = replace(Regex("<[^>]+>"), " ")
            .replace(",", "")
            .trim()
            .split(Regex("\\s+"))
            .firstOrNull { token -> token.any(Char::isDigit) }
            ?.lowercase(Locale.ROOT)
            ?: return null
        val multiplier = when {
            raw.endsWith("k") -> 1_000.0
            raw.endsWith("m") -> 1_000_000.0
            else -> 1.0
        }
        val number = raw.trimEnd('k', 'm').toDoubleOrNull() ?: return null
        return (number * multiplier).toInt().coerceAtLeast(0)
    }
}
