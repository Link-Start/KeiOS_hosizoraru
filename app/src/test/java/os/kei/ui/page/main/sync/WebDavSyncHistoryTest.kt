package os.kei.ui.page.main.sync

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebDavSyncHistoryTest {
    @Test
    fun `append history keeps newest entries first and caps stored records`() {
        val existing =
            (0 until WebDavSyncHistoryMaxEntries).map { index ->
                webDavSyncHistoryEntry(id = "old-$index", finishedAtMs = index.toLong())
            }

        val updated =
            appendWebDavSyncHistory(
                current = existing,
                entry = webDavSyncHistoryEntry(id = "new", finishedAtMs = 10_000L),
            )

        assertEquals(WebDavSyncHistoryMaxEntries, updated.size)
        assertEquals("new", updated.first().id)
        assertEquals("old-1", updated.last().id)
    }

    @Test
    fun `append history replaces matching id instead of duplicating records`() {
        val existing =
            listOf(
                webDavSyncHistoryEntry(id = "same", reason = "launch", finishedAtMs = 1_000L),
                webDavSyncHistoryEntry(id = "other", reason = "manual", finishedAtMs = 900L),
            )

        val updated =
            appendWebDavSyncHistory(
                current = existing,
                entry = webDavSyncHistoryEntry(id = "same", reason = "background", finishedAtMs = 2_000L),
            )

        assertEquals(listOf("same", "other"), updated.map { it.id })
        assertEquals("background", updated.first().reason)
        assertEquals(2, updated.size)
    }

    @Test
    fun `skipped pending review item keeps item details and marks history for review`() {
        val entry =
            buildWebDavSyncHistoryEntry(
                source = WebDavSyncHistorySource.Auto,
                kind = null,
                reason = "alarm",
                startedAtMs = 1_000L,
                finishedAtMs = 2_000L,
                targetCount = 6,
                outcomes =
                    listOf(
                        WebDavSyncItem.GitHubTracked to WebDavItemOutcome(WebDavItemStatus.UpToDate),
                        WebDavSyncItem.BaAccounts to WebDavItemOutcome(WebDavItemStatus.UpToDate),
                        WebDavSyncItem.BaCatalogFavorites to WebDavItemOutcome(WebDavItemStatus.UpToDate),
                        WebDavSyncItem.BaBgmFavorites to WebDavItemOutcome(WebDavItemStatus.UpToDate),
                        WebDavSyncItem.OsShellCards to WebDavItemOutcome(WebDavItemStatus.UpToDate),
                    ),
                skippedCount = 1,
                skippedOutcomes =
                    listOf(
                        WebDavSyncItem.OsActivityCards to WebDavItemOutcome(WebDavItemStatus.ConflictUnresolved),
                    ),
            )

        assertEquals(WebDavAutoSyncStatus.NeedsReview, entry.status)
        assertEquals(5, entry.succeededCount)
        assertEquals(0, entry.failedCount)
        assertEquals(1, entry.skippedCount)
        assertTrue(
            entry.items.any {
                it.item == WebDavSyncItem.OsActivityCards &&
                    it.status == WebDavItemStatus.ConflictUnresolved
            },
        )
    }

    @Test
    fun `history payload without runtime diagnostics remains readable`() {
        val payload =
            Json.decodeFromString<WebDavSyncHistoryPayload>(
                """{"version":1,"entries":[{"id":"legacy","source":"Auto","reason":"job","status":"Success","startedAtMs":1000,"finishedAtMs":2000,"targetCount":1,"succeededCount":1,"failedCount":0,"skippedCount":0}]}""",
            )

        assertEquals("legacy", payload.entries.single().id)
        assertNull(payload.entries.single().runtimeDiagnostics)
    }
}

private fun webDavSyncHistoryEntry(
    id: String,
    reason: String = "manual",
    finishedAtMs: Long = 1_000L,
): WebDavSyncHistoryEntry =
    WebDavSyncHistoryEntry(
        id = id,
        source = WebDavSyncHistorySource.Manual,
        kind = WebDavSyncHistoryKind.Sync,
        reason = reason,
        status = WebDavAutoSyncStatus.Success,
        startedAtMs = finishedAtMs - 100L,
        finishedAtMs = finishedAtMs,
        targetCount = 1,
        succeededCount = 1,
        failedCount = 0,
        skippedCount = 0,
        items =
            listOf(
                WebDavSyncHistoryItem(
                    item = WebDavSyncItem.GitHubTracked,
                    status = WebDavItemStatus.UpToDate,
                ),
            ),
    )
