package os.kei.feature.github.data.remote.fdroid

import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.StringReader
import java.io.Reader
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class FdroidIndexV2StreamParserTest {
    @Test
    fun `searchIndex parses only matching packages from repository index`() = runBlocking {
        val snapshot = FdroidIndexV2StreamParser.searchIndex(
            repoUrl = "https://apt.izzysoft.de/fdroid/repo",
            reader = StringReader(FdroidIndexV2Fixtures.index),
            query = "Pixiv",
            packageName = "",
            limit = 12
        ).getOrThrow()

        assertEquals("IzzyOnDroid", snapshot.repoName)
        assertEquals(3, snapshot.packageCount)
        assertEquals(listOf("com.perol.pixez"), snapshot.packages.keys.toList())
        assertEquals("PixEz", snapshot.packageSnapshot("com.perol.pixez")?.appName)
        assertEquals("0.9.104 wsv", snapshot.packageSnapshot("com.perol.pixez")?.versions?.first()?.versionName)
        assertNull(snapshot.packageSnapshot("dev.imranr.obtainium"))
    }

    @Test
    fun `searchIndex can stop early for exact package lookup`() = runBlocking {
        val snapshot = FdroidIndexV2StreamParser.searchIndex(
            repoUrl = "https://apt.izzysoft.de/fdroid/repo",
            reader = StringReader(FdroidIndexV2Fixtures.index),
            query = "",
            packageName = "com.perol.pixez",
            limit = 12
        ).getOrThrow()

        assertEquals(listOf("com.perol.pixez"), snapshot.packages.keys.toList())
        assertEquals("PixEz", snapshot.packageSnapshot("com.perol.pixez")?.appName)
    }

    @Test
    fun `searchIndex exact stop remains scoped to packages object`() = runBlocking {
        val cases = listOf(
            // name to (query, packageName)
            "exact package" to ("" to "com.perol.pixez"),
            "exact app name" to ("PixEz" to "")
        )

        cases.forEach { (case, lookup) ->
            val snapshot = FdroidIndexV2StreamParser.searchIndex(
                repoUrl = "https://apt.izzysoft.de/fdroid/repo",
                reader = StringReader(indexWithRepoAfterPackagesFixture),
                query = lookup.first,
                packageName = lookup.second,
                limit = 12
            ).getOrThrow()

            assertEquals("Late Repo", snapshot.repoName, case)
            assertEquals(listOf("com.perol.pixez"), snapshot.packages.keys.toList(), case)
            assertEquals(1, snapshot.packageCount, case)
        }
    }

    @Test
    fun `searchIndex rethrows cancellation while reading index stream`() {
        runBlocking {
            assertFailsWith<CancellationException> {
                FdroidIndexV2StreamParser.searchIndex(
                    repoUrl = "https://apt.izzysoft.de/fdroid/repo",
                    reader = CancellingReader(FdroidIndexV2Fixtures.index, cancelAfterChars = 96),
                    query = "PixEz",
                    packageName = "",
                    limit = 12
                )
            }
        }
    }

    @Test
    fun `loadPackages scans one repo while materializing only requested packages`() = runBlocking {
        val snapshot = FdroidIndexV2StreamParser.loadPackages(
            repoUrl = "https://apt.izzysoft.de/fdroid/repo",
            reader = StringReader(FdroidIndexV2Fixtures.index),
            packageNames = setOf("dev.imranr.obtainium", "com.perol.pixez")
        ).getOrThrow()

        assertEquals(3, snapshot.packageCount)
        assertEquals(
            listOf("com.perol.pixez", "dev.imranr.obtainium").sorted(),
            snapshot.packages.keys.sorted()
        )
        assertNull(snapshot.packageSnapshot("org.fdroid.fdroid"))
    }

    private val indexWithRepoAfterPackagesFixture: String =
        """
        {
          "packages": {
            "com.perol.pixez": {
              "metadata": {
                "name": {
                  "en-US": "PixEz"
                },
                "summary": {
                  "en-US": "Pixiv client"
                },
                "suggestedVersionCode": 10010040
              },
              "versions": {
                "com.perol.pixez_10010040.apk": {
                  "manifest": {
                    "versionName": "0.9.104 wsv",
                    "versionCode": 10010040
                  },
                  "file": {
                    "name": "/repo/com.perol.pixez_10010040.apk",
                    "sha256": "pixez-sha256"
                  }
                }
              }
            },
            "dev.imranr.obtainium": {
              "metadata": {
                "name": {
                  "en-US": "Obtainium"
                }
              },
              "versions": {}
            }
          },
          "repo": {
            "name": {
              "en-US": "Late Repo"
            },
            "timestamp": 1780000000000
          }
        }
        """.trimIndent()

    private class CancellingReader(
        private val raw: String,
        private val cancelAfterChars: Int
    ) : Reader() {
        private var position = 0

        override fun read(
            cbuf: CharArray,
            off: Int,
            len: Int
        ): Int {
            if (position >= cancelAfterChars) {
                throw CancellationException("cancelled")
            }
            if (position >= raw.length) return -1
            val count = minOf(len, raw.length - position, cancelAfterChars - position)
            raw.toCharArray(
                destination = cbuf,
                destinationOffset = off,
                startIndex = position,
                endIndex = position + count
            )
            position += count
            return count
        }

        override fun close() = Unit
    }
}
