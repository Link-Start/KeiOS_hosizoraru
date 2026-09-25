package os.kei.feature.github.fixture

import os.kei.feature.github.data.remote.GitHubApiTokenReleaseStrategy
import os.kei.feature.github.model.GitHubAtomReleaseEntry

/** Captured release responses under `src/test/resources`, read by their classpath name. */
internal object ReleaseCorpusResources {
    fun text(name: String): String =
        requireNotNull(ReleaseCorpusResources::class.java.classLoader?.getResourceAsStream(name)) {
            "missing $name fixture"
        }.use { it.readBytes().decodeToString() }

    /** A captured Releases API list, parsed by the strategy's own parser. */
    fun entries(name: String, owner: String, repo: String): List<GitHubAtomReleaseEntry> =
        GitHubApiTokenReleaseStrategy().parseReleaseEntries(json = text(name), owner = owner, repo = repo)
}
