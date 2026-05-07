package os.kei.core.prefs

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CacheFreshnessSnapshotTest {
    @Test
    fun `snapshot marks data fresh inside ttl`() {
        val snapshot = CacheFreshnessSnapshot.from(
            lastUpdatedAtMs = NOW_MS - 1_000L,
            bytes = 128L,
            rebuildable = true,
            ttlMs = 60_000L,
            nowMs = NOW_MS
        )

        assertTrue(snapshot.hasData)
        assertTrue(snapshot.fresh)
        assertFalse(snapshot.stale)
        assertTrue(snapshot.rebuildable)
    }

    @Test
    fun `snapshot marks data stale after ttl`() {
        val snapshot = CacheFreshnessSnapshot.from(
            lastUpdatedAtMs = NOW_MS - 120_000L,
            bytes = 128L,
            rebuildable = true,
            ttlMs = 60_000L,
            nowMs = NOW_MS
        )

        assertTrue(snapshot.hasData)
        assertFalse(snapshot.fresh)
        assertTrue(snapshot.stale)
    }

    @Test
    fun `snapshot treats no timestamp and no bytes as empty`() {
        val snapshot = CacheFreshnessSnapshot.from(
            lastUpdatedAtMs = 0L,
            bytes = 0L,
            rebuildable = false,
            ttlMs = 60_000L,
            nowMs = NOW_MS
        )

        assertFalse(snapshot.hasData)
        assertFalse(snapshot.fresh)
        assertFalse(snapshot.stale)
    }

    private companion object {
        private const val NOW_MS = 1_777_392_000_000L
    }
}
