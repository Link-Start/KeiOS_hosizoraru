package os.kei.feature.github.domain

import org.junit.Test
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.model.GitHubApkPackageNameScanRequest
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubLookupStrategyOption
import os.kei.feature.github.model.GitHubPackageRepositoryScanRequest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GitHubTrackExportFixtureBidirectionalScanTest {
    @Test
    fun `exported tracks json imports all array items as valid tracked apps`() {
        val payload = GitHubTrackStore.parseTrackedItemsImport(GitHubTrackExportFixture.rawJson)

        assertEquals(31, payload.sourceCount)
        assertEquals(31, payload.items.size)
        assertEquals(0, payload.invalidCount)
        assertEquals(0, payload.duplicateCount)
        assertNotNull(payload.items.firstOrNull { item ->
            item.owner == "LibChecker" &&
                    item.repo == "LibChecker" &&
                    item.packageName == "com.absinthe.libchecker"
        })
        assertNotNull(payload.items.firstOrNull { item ->
            item.owner == "vvb2060" &&
                    item.repo == "PackageInstaller" &&
                    item.packageName == "io.github.vvb2060.packageinstaller"
        })
    }

    @Test
    fun `project address to package scanner resolves every exported tracked app`() {
        val items = GitHubTrackExportFixture.trackedItems
        val scanSource = GitHubTrackFixtureSources.packageScanSource(items)
        val scanner = GitHubApkPackageNameScanner(scanSource)

        items.forEach { item ->
            val apiResult = scanner.scan(
                GitHubApkPackageNameScanRequest(
                    repoUrl = item.repoUrl,
                    lookupConfig = GitHubLookupConfig(
                        selectedStrategy = GitHubLookupStrategyOption.GitHubApiToken,
                        apiToken = "token-123"
                    )
                )
            ).getOrThrow()
            val atomResult = scanner.scan(
                GitHubApkPackageNameScanRequest(
                    repoUrl = item.repoUrl,
                    lookupConfig = GitHubLookupConfig(
                        selectedStrategy = GitHubLookupStrategyOption.AtomFeed
                    )
                )
            ).getOrThrow()

            assertEquals(item.owner, apiResult.owner)
            assertEquals(item.repo, apiResult.repo)
            assertEquals(item.packageName, apiResult.packageName)
            assertEquals(item.packageName, atomResult.packageName)
        }
    }

    @Test
    fun `package to repository resolver confirms every exported tracked app from preferred repository`() {
        val items = GitHubTrackExportFixture.trackedItems
        val discovery = GitHubTrackFixtureSources.discoverySource(items)
        val scanSource = GitHubTrackFixtureSources.packageScanSource(items)
        val resolver = GitHubPackageRepositoryResolver(
            discoverySource = discovery,
            packageNameScanner = GitHubApkPackageNameScanner(scanSource)
        )

        items.forEach { item ->
            val result = resolver.scanRepositoriesForPackage(
                GitHubPackageRepositoryScanRequest(
                    packageName = item.packageName,
                    appLabel = item.appLabel,
                    preferredRepoUrl = item.repoUrl,
                    lookupConfig = GitHubLookupConfig(
                        selectedStrategy = GitHubLookupStrategyOption.GitHubApiToken,
                        apiToken = "token-123"
                    ),
                    candidateLimit = items.size,
                    verificationLimit = items.size
                )
            ).getOrThrow()
            val matched = result.matchedCandidates.firstOrNull { candidate ->
                candidate.repository.owner.equals(item.owner, ignoreCase = true) &&
                        candidate.repository.repo.equals(item.repo, ignoreCase = true)
            }

            assertNotNull(matched, "Expected reverse scan to find ${item.owner}/${item.repo}")
            assertEquals(item.repoUrl, matched.trackedApp.repoUrl)
            assertEquals(item.packageName, matched.trackedApp.packageName)
            assertEquals(item.appLabel, matched.trackedApp.appLabel)
            assertTrue(result.scannedCandidateCount >= result.matchedCandidates.size)
        }
    }

    @Test
    fun `package to repository resolver discovers every exported tracked app from package and label`() {
        val items = GitHubTrackExportFixture.trackedItems
        val discovery = GitHubTrackFixtureSources.discoverySource(items)
        val scanSource = GitHubTrackFixtureSources.packageScanSource(items)
        val resolver = GitHubPackageRepositoryResolver(
            discoverySource = discovery,
            packageNameScanner = GitHubApkPackageNameScanner(scanSource)
        )

        items.forEach { item ->
            val result = resolver.scanRepositoriesForPackage(
                GitHubPackageRepositoryScanRequest(
                    packageName = item.packageName,
                    appLabel = item.appLabel,
                    lookupConfig = GitHubLookupConfig(
                        selectedStrategy = GitHubLookupStrategyOption.GitHubApiToken,
                        apiToken = "token-123"
                    ),
                    candidateLimit = items.size,
                    verificationLimit = items.size
                )
            ).getOrThrow()
            val matched = result.matchedCandidates.firstOrNull { candidate ->
                candidate.repository.owner.equals(item.owner, ignoreCase = true) &&
                        candidate.repository.repo.equals(item.repo, ignoreCase = true)
            }

            assertNotNull(
                matched,
                "Expected package search to discover ${item.owner}/${item.repo}"
            )
            assertEquals(item.packageName, matched.trackedApp.packageName)
            assertTrue(result.queryCount >= 1)
        }
    }
}
