package os.kei.mcp.notification

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Test
import os.kei.mcp.framework.notification.builder.NotificationRenderStyle

class McpNotificationSnapshotStoreTest {
    @After
    fun tearDown() {
        McpNotificationSnapshotStore.clear(TEST_ID)
        McpNotificationActiveStateCache.clear(TEST_ID)
    }

    @Test
    fun `snapshot store reports unchanged payloads`() {
        val snapshot = snapshot(port = 38888)

        assertTrue(McpNotificationSnapshotStore.putIfChanged(TEST_ID, snapshot))
        assertFalse(McpNotificationSnapshotStore.putIfChanged(TEST_ID, snapshot))
        assertTrue(McpNotificationSnapshotStore.putIfChanged(TEST_ID, snapshot(port = 38889)))
    }

    @Test
    fun `active state cache reuses short lived probes`() {
        var probeCount = 0

        assertTrue(
            McpNotificationActiveStateCache.isActive(TEST_ID, nowMs = 10_000L) {
                probeCount += 1
                true
            }
        )
        assertTrue(
            McpNotificationActiveStateCache.isActive(TEST_ID, nowMs = 10_500L) {
                probeCount += 1
                false
            }
        )
        assertFalse(
            McpNotificationActiveStateCache.isActive(TEST_ID, nowMs = 12_500L) {
                probeCount += 1
                false
            }
        )

        assertEquals(2, probeCount)
    }

    private fun snapshot(port: Int): McpNotificationSnapshot {
        return McpNotificationSnapshot(
            serverName = "KeiOS MCP",
            running = true,
            port = port,
            path = "/mcp",
            clients = 1,
            ongoing = true,
            onlyAlertOnce = true,
            style = NotificationRenderStyle.LIVE_UPDATE,
            useXiaomiMagic = false
        )
    }

    private companion object {
        const val TEST_ID = McpNotificationHelper.KEEPALIVE_NOTIFICATION_ID
    }
}
