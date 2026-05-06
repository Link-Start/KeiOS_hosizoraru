package os.kei.feature.github.data.remote

import java.net.URLEncoder

internal class GitHubActionsApiUrlBuilder(
    private val apiBaseUrl: String,
    private val nightlyLinkBaseUrl: String
) {
    fun repository(owner: String, repo: String): String {
        return "${apiBaseUrl.trimEnd('/')}/repos/$owner/$repo"
    }

    fun workflows(owner: String, repo: String, limit: Int): String {
        return "${apiBaseUrl.trimEnd('/')}/repos/$owner/$repo/actions/workflows?per_page=${
            limit.coerceIn(
                1,
                100
            )
        }"
    }

    fun workflowRuns(
        owner: String,
        repo: String,
        workflowId: String,
        limit: Int,
        branch: String,
        event: String,
        status: String,
        actor: String,
        created: String,
        headSha: String,
        excludePullRequests: Boolean
    ): String {
        val encodedWorkflowId = workflowId.trim().urlEncode()
        val query = buildList {
            add("per_page=${limit.coerceIn(1, 100)}")
            branch.trim().takeIf { it.isNotBlank() }?.let { add("branch=${it.urlEncode()}") }
            event.trim().takeIf { it.isNotBlank() }?.let { add("event=${it.urlEncode()}") }
            status.trim().takeIf { it.isNotBlank() }?.let { add("status=${it.urlEncode()}") }
            actor.trim().takeIf { it.isNotBlank() }?.let { add("actor=${it.urlEncode()}") }
            created.trim().takeIf { it.isNotBlank() }?.let { add("created=${it.urlEncode()}") }
            headSha.trim().takeIf { it.isNotBlank() }?.let { add("head_sha=${it.urlEncode()}") }
            if (excludePullRequests) add("exclude_pull_requests=true")
        }.joinToString("&")
        return "${apiBaseUrl.trimEnd('/')}/repos/$owner/$repo/actions/workflows/$encodedWorkflowId/runs?$query"
    }

    fun workflowRun(owner: String, repo: String, runId: Long): String {
        return "${apiBaseUrl.trimEnd('/')}/repos/$owner/$repo/actions/runs/$runId"
    }

    fun runArtifacts(owner: String, repo: String, runId: Long, limit: Int): String {
        return "${apiBaseUrl.trimEnd('/')}/repos/$owner/$repo/actions/runs/$runId/artifacts?per_page=${
            limit.coerceIn(
                1,
                100
            )
        }"
    }

    fun artifactDownload(owner: String, repo: String, artifactId: Long): String {
        return "${apiBaseUrl.trimEnd('/')}/repos/$owner/$repo/actions/artifacts/$artifactId/zip"
    }

    fun nightlyRunArtifactDownload(
        owner: String,
        repo: String,
        runId: Long,
        artifactName: String
    ): String {
        return "${nightlyLinkBaseUrl.trimEnd('/')}/${owner.urlEncode()}/${repo.urlEncode()}/" +
                "actions/runs/$runId/${artifactName.urlEncode()}.zip"
    }

    fun isNightlyUrl(url: String): Boolean {
        val normalizedNightlyBaseUrl = nightlyLinkBaseUrl.trimEnd('/')
        return url.startsWith(normalizedNightlyBaseUrl, ignoreCase = true) ||
                url.contains("nightly.link", ignoreCase = true)
    }

    private fun String.urlEncode(): String {
        return URLEncoder.encode(this, Charsets.UTF_8.name()).replace("+", "%20")
    }
}
