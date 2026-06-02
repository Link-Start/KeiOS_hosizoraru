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
        McpNotificationSnapshotStore.clear(ACCOUNT_SCOPED_ID)
        McpNotificationActiveStateCache.clear(TEST_ID)
        McpNotificationActiveStateCache.clear(ACCOUNT_SCOPED_ID)
    }

    @Test
    fun `snapshot store reports unchanged payloads`() {
        val snapshot = snapshot(port = 38888)

        assertTrue(McpNotificationSnapshotStore.putIfChanged(TEST_ID, snapshot))
        assertFalse(McpNotificationSnapshotStore.putIfChanged(TEST_ID, snapshot))
        assertTrue(McpNotificationSnapshotStore.putIfChanged(TEST_ID, snapshot(port = 38889)))
    }

    @Test
    fun `snapshot store keeps account scoped notification ids`() {
        val snapshot = snapshot(port = 120)

        McpNotificationSnapshotStore.put(ACCOUNT_SCOPED_ID, snapshot)

        assertEquals(snapshot, McpNotificationSnapshotStore.get(ACCOUNT_SCOPED_ID))
        assertTrue(
            McpNotificationSnapshotStore.entries().any { (notificationId, storedSnapshot) ->
                notificationId == ACCOUNT_SCOPED_ID && storedSnapshot == snapshot
            }
        )
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

    @Test
    fun `mi focus order id keeps explicit value`() {
        assertEquals(
            "ba-ap-cn-main",
            McpNotificationHelper.resolveMiFocusOrderId(
                serverName = "BlueArchive AP",
                notificationId = ACCOUNT_SCOPED_ID,
                miFocusOrderId = " ba-ap-cn-main ",
            ),
        )
    }

    @Test
    fun `mi focus order id falls back to normalized server and notification id`() {
        assertEquals(
            "bluearchive_ap-$ACCOUNT_SCOPED_ID",
            McpNotificationHelper.resolveMiFocusOrderId(
                serverName = "BlueArchive AP",
                notificationId = ACCOUNT_SCOPED_ID,
                miFocusOrderId = " ",
            ),
        )
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
        const val ACCOUNT_SCOPED_ID = 243_001
    }
}
