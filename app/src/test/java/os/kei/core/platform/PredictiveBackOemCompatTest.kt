package os.kei.core.platform

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PredictiveBackOemCompatTest {
    @Test
    fun `hyperos uses swipe edge aware predictive back policy`() {
        val policy = PredictiveBackOemCompat.resolvePolicy(
            transitionAnimationsEnabled = true,
            predictiveBackAnimationsEnabled = true,
            signals = PredictiveBackOemCompat.DeviceSignals(
                brand = "Xiaomi",
                manufacturer = "Xiaomi",
                display = "OS3.0.306.4.WBLCNXM",
                model = "Xiaomi 17 Pro",
                properties = mapOf(
                    "ro.mi.os.version.name" to "OS3.0",
                    "ro.mi.os.version.incremental" to "OS3.0.306.4.WBLCNXM"
                )
            )
        )

        assertEquals(PredictiveBackOemCompat.RomFamily.HyperOs, policy.romFamily)
        assertTrue(policy.frameworkAnimationsEnabled)
        assertTrue(policy.popDirectionFollowsSwipeEdge)
        assertEquals(
            PredictiveBackOemCompat.RouteBackPipeline.NavigationEvent,
            policy.routeBackPipeline
        )
        assertEquals(
            PredictiveBackOemCompat.LocalBackPipeline.CommitOnly,
            policy.localBackPipeline
        )
        assertEquals(
            PredictiveBackOemCompat.ActivityBackPipeline.FrameworkFinish,
            policy.activityBackPipeline
        )
        assertTrue(policy.activityFrameworkFinishEnabled)
    }

    @Test
    fun `google api image keeps default predictive back direction`() {
        val policy = PredictiveBackOemCompat.resolvePolicy(
            transitionAnimationsEnabled = true,
            predictiveBackAnimationsEnabled = true,
            signals = PredictiveBackOemCompat.DeviceSignals(
                brand = "google",
                manufacturer = "Google",
                display = "CP21.260330.005",
                model = "Pixel 10 Pro",
                properties = emptyMap()
            )
        )

        assertEquals(PredictiveBackOemCompat.RomFamily.Aosp, policy.romFamily)
        assertTrue(policy.frameworkAnimationsEnabled)
        assertFalse(policy.popDirectionFollowsSwipeEdge)
        assertEquals(
            PredictiveBackOemCompat.RouteBackPipeline.NavigationEvent,
            policy.routeBackPipeline
        )
        assertEquals(
            PredictiveBackOemCompat.LocalBackPipeline.ComposePredictive,
            policy.localBackPipeline
        )
        assertEquals(
            PredictiveBackOemCompat.ActivityBackPipeline.FrameworkFinish,
            policy.activityBackPipeline
        )
        assertTrue(policy.activityFrameworkFinishEnabled)
    }

    @Test
    fun `coloros family uses swipe edge aware predictive back policy`() {
        val policy = PredictiveBackOemCompat.resolvePolicy(
            transitionAnimationsEnabled = true,
            predictiveBackAnimationsEnabled = true,
            signals = PredictiveBackOemCompat.DeviceSignals(
                brand = "OnePlus",
                manufacturer = "OPPO",
                display = "ColorOS",
                model = "PKR110",
                properties = mapOf("ro.build.version.oplusrom" to "V17")
            )
        )

        assertEquals(PredictiveBackOemCompat.RomFamily.ColorOs, policy.romFamily)
        assertTrue(policy.frameworkAnimationsEnabled)
        assertTrue(policy.popDirectionFollowsSwipeEdge)
        assertEquals(
            PredictiveBackOemCompat.RouteBackPipeline.NavigationEvent,
            policy.routeBackPipeline
        )
        assertEquals(
            PredictiveBackOemCompat.LocalBackPipeline.CommitOnly,
            policy.localBackPipeline
        )
        assertEquals(
            PredictiveBackOemCompat.ActivityBackPipeline.FrameworkFinish,
            policy.activityBackPipeline
        )
        assertTrue(policy.activityFrameworkFinishEnabled)
    }

    @Test
    fun `miui and xiaomi families keep route predictive and commit local back`() {
        val miuiPolicy = PredictiveBackOemCompat.resolvePolicy(
            transitionAnimationsEnabled = true,
            predictiveBackAnimationsEnabled = true,
            signals = PredictiveBackOemCompat.DeviceSignals(
                brand = "Xiaomi",
                manufacturer = "Xiaomi",
                display = "MIUI",
                model = "Xiaomi",
                properties = mapOf("ro.miui.ui.version.name" to "V15")
            )
        )
        val xiaomiPolicy = PredictiveBackOemCompat.resolvePolicy(
            transitionAnimationsEnabled = true,
            predictiveBackAnimationsEnabled = true,
            signals = PredictiveBackOemCompat.DeviceSignals(
                brand = "Redmi",
                manufacturer = "Xiaomi",
                display = "Android",
                model = "Redmi",
                properties = emptyMap()
            )
        )

        listOf(miuiPolicy, xiaomiPolicy).forEach { policy ->
            assertTrue(policy.routePredictiveBackEnabled)
            assertFalse(policy.localPredictiveBackEnabled)
            assertEquals(
                PredictiveBackOemCompat.RouteBackPipeline.NavigationEvent,
                policy.routeBackPipeline
            )
            assertEquals(
                PredictiveBackOemCompat.LocalBackPipeline.CommitOnly,
                policy.localBackPipeline
            )
            assertEquals(
                PredictiveBackOemCompat.ActivityBackPipeline.FrameworkFinish,
                policy.activityBackPipeline
            )
            assertTrue(policy.activityFrameworkFinishEnabled)
        }
        assertEquals(PredictiveBackOemCompat.RomFamily.Miui, miuiPolicy.romFamily)
        assertEquals(PredictiveBackOemCompat.RomFamily.Xiaomi, xiaomiPolicy.romFamily)
    }

    @Test
    fun `disabled user setting disables framework predictive animations`() {
        val policy = PredictiveBackOemCompat.resolvePolicy(
            transitionAnimationsEnabled = true,
            predictiveBackAnimationsEnabled = false,
            signals = PredictiveBackOemCompat.DeviceSignals(
                brand = "Xiaomi",
                manufacturer = "Xiaomi",
                display = "OS3.0",
                model = "Xiaomi",
                properties = mapOf("ro.mi.os.version.name" to "OS3.0")
            )
        )

        assertEquals(PredictiveBackOemCompat.RomFamily.HyperOs, policy.romFamily)
        assertFalse(policy.frameworkAnimationsEnabled)
        assertFalse(policy.popDirectionFollowsSwipeEdge)
        assertEquals(
            PredictiveBackOemCompat.RouteBackPipeline.CommitOnly,
            policy.routeBackPipeline
        )
        assertEquals(
            PredictiveBackOemCompat.LocalBackPipeline.CommitOnly,
            policy.localBackPipeline
        )
        assertEquals(
            PredictiveBackOemCompat.ActivityBackPipeline.CommitCallback,
            policy.activityBackPipeline
        )
        assertFalse(policy.activityFrameworkFinishEnabled)
    }
}
