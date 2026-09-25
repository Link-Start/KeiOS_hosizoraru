package os.kei.core.notification.live

import org.junit.Test
import kotlin.test.assertEquals
import os.kei.core.notification.live.builder.NotificationRenderStyle

class MiIslandRenderPolicyTest {
    @Test
    fun `render policy picks the island only when the device and the user allow it`() {
        data class Case(
            val label: String,
            val preferSuperIsland: Boolean = true,
            val bypass: Boolean = false,
            val isHyperOS: Boolean = true,
            val protocol: Int = 3,
            val islandFeature: Boolean = true,
            val focusPermission: Boolean = true,
            val style: NotificationRenderStyle,
            val magic: Boolean,
            val reason: MiIslandRenderReason,
        )
        val liveUpdate = NotificationRenderStyle.LIVE_UPDATE
        val island = NotificationRenderStyle.MI_ISLAND
        listOf(
            Case("disabled by the user, even with bypass", preferSuperIsland = false, bypass = true,
                style = liveUpdate, magic = false, reason = MiIslandRenderReason.DisabledByUser),
            Case("not HyperOS", isHyperOS = false,
                style = liveUpdate, magic = false, reason = MiIslandRenderReason.HyperOsUnavailable),
            Case("focus protocol below 3", protocol = 2,
                style = liveUpdate, magic = false, reason = MiIslandRenderReason.ProtocolUnavailable),
            Case("missing focus permission without bypass", focusPermission = false,
                style = liveUpdate, magic = false, reason = MiIslandRenderReason.FocusPermissionRequired),
            Case("focus permission, no magic needed",
                style = island, magic = false, reason = MiIslandRenderReason.Selected),
            Case("bypass stands in for a missing permission", bypass = true, focusPermission = false,
                style = island, magic = true, reason = MiIslandRenderReason.Selected),
            Case("bypass tolerates a missing island property on compatible HyperOS", bypass = true,
                islandFeature = false, focusPermission = false,
                style = island, magic = true, reason = MiIslandRenderReason.Selected),
        ).forEach { case ->
            val decision = MiIslandRenderPolicy.resolve(
                preferSuperIsland = case.preferSuperIsland,
                bypassRestriction = case.bypass,
                capability = MiIslandCapability(
                    isHyperOS = case.isHyperOS,
                    focusProtocolVersion = case.protocol,
                    supportsIslandFeature = case.islandFeature,
                    hasFocusPermission = case.focusPermission,
                ),
            )

            assertEquals(case.style, decision.style, case.label)
            assertEquals(case.magic, decision.useXiaomiMagic, case.label)
            assertEquals(case.reason, decision.reason, case.label)
        }
    }
}
