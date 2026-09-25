package os.kei.core.prefs

import org.junit.Test
import kotlin.test.assertEquals

class CacheFreshnessSnapshotTest {
    @Test
    fun `snapshot is fresh inside the ttl, stale after it, and empty without data`() {
        data class Case(
            val label: String,
            val ageMs: Long?,
            val bytes: Long,
            val hasData: Boolean,
            val fresh: Boolean,
            val stale: Boolean,
        )
        listOf(
            Case("inside ttl", ageMs = 1_000L, bytes = 128L, hasData = true, fresh = true, stale = false),
            Case("after ttl", ageMs = 120_000L, bytes = 128L, hasData = true, fresh = false, stale = true),
            Case("no timestamp and no bytes", ageMs = null, bytes = 0L, hasData = false, fresh = false, stale = false),
        ).forEach { case ->
            val snapshot = CacheFreshnessSnapshot.from(
                lastUpdatedAtMs = case.ageMs?.let { NOW_MS - it } ?: 0L,
                bytes = case.bytes,
                rebuildable = true,
                ttlMs = 60_000L,
                nowMs = NOW_MS
            )

            assertEquals(case.hasData, snapshot.hasData, case.label)
            assertEquals(case.fresh, snapshot.fresh, case.label)
            assertEquals(case.stale, snapshot.stale, case.label)
        }
    }

    private companion object {
        private const val NOW_MS = 1_777_392_000_000L
    }
}
