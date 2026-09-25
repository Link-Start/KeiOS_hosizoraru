package os.kei.core.platform

import org.junit.Test
import os.kei.core.platform.PredictiveBackOemCompat.ActivityBackPipeline
import os.kei.core.platform.PredictiveBackOemCompat.DeviceSignals
import os.kei.core.platform.PredictiveBackOemCompat.LocalBackPipeline
import os.kei.core.platform.PredictiveBackOemCompat.RomFamily
import kotlin.test.assertEquals

class PredictiveBackOemCompatTest {
    private data class Case(
        val name: String,
        val signals: DeviceSignals,
        val predictiveBackSetting: Boolean = true,
        val romFamily: RomFamily,
        val frameworkAnimationsEnabled: Boolean = true,
        val localBackPipeline: LocalBackPipeline = LocalBackPipeline.CommitOnly,
        val activityBackPipeline: ActivityBackPipeline = ActivityBackPipeline.FrameworkFinish,
    )

    /** The device strings are real-device readings, so they are the contract, not placeholders. */
    private val cases = listOf(
        Case(
            name = "hyperos commits local back and finishes activities through the framework",
            signals = DeviceSignals(
                brand = "Xiaomi",
                manufacturer = "Xiaomi",
                display = "OS3.0.306.4.WBLCNXM",
                model = "Xiaomi 17 Pro",
                properties = mapOf(
                    "ro.mi.os.version.name" to "OS3.0",
                    "ro.mi.os.version.incremental" to "OS3.0.306.4.WBLCNXM"
                )
            ),
            romFamily = RomFamily.HyperOs,
        ),
        Case(
            name = "aosp drives local back through compose predictive back",
            signals = DeviceSignals(
                brand = "google",
                manufacturer = "Google",
                display = "CP21.260330.005",
                model = "Pixel 10 Pro",
                properties = emptyMap()
            ),
            romFamily = RomFamily.Aosp,
            localBackPipeline = LocalBackPipeline.ComposePredictive,
        ),
        Case(
            name = "coloros commits local back and finishes activities through the framework",
            signals = DeviceSignals(
                brand = "OnePlus",
                manufacturer = "OPPO",
                display = "ColorOS",
                model = "PKR110",
                properties = mapOf("ro.build.version.oplusrom" to "V17")
            ),
            romFamily = RomFamily.ColorOs,
        ),
        Case(
            name = "miui commits local back",
            signals = DeviceSignals(
                brand = "Xiaomi",
                manufacturer = "Xiaomi",
                display = "MIUI",
                model = "Xiaomi",
                properties = mapOf("ro.miui.ui.version.name" to "V15")
            ),
            romFamily = RomFamily.Miui,
        ),
        Case(
            name = "redmi without rom properties is the xiaomi family and commits local back",
            signals = DeviceSignals(
                brand = "Redmi",
                manufacturer = "Xiaomi",
                display = "Android",
                model = "Redmi",
                properties = emptyMap()
            ),
            romFamily = RomFamily.Xiaomi,
        ),
        Case(
            name = "disabled user setting disables framework predictive animations",
            signals = DeviceSignals(
                brand = "Xiaomi",
                manufacturer = "Xiaomi",
                display = "OS3.0",
                model = "Xiaomi",
                properties = mapOf("ro.mi.os.version.name" to "OS3.0")
            ),
            predictiveBackSetting = false,
            romFamily = RomFamily.HyperOs,
            frameworkAnimationsEnabled = false,
            activityBackPipeline = ActivityBackPipeline.CommitCallback,
        ),
    )

    @Test
    fun `each rom family resolves its back pipelines`() {
        cases.forEach { case ->
            val policy = PredictiveBackOemCompat.resolvePolicy(
                transitionAnimationsEnabled = true,
                predictiveBackAnimationsEnabled = case.predictiveBackSetting,
                signals = case.signals
            )

            assertEquals(case.romFamily, policy.romFamily, case.name)
            assertEquals(case.frameworkAnimationsEnabled, policy.frameworkAnimationsEnabled, case.name)
            assertEquals(case.localBackPipeline, policy.localBackPipeline, case.name)
            assertEquals(case.activityBackPipeline, policy.activityBackPipeline, case.name)
            assertEquals(
                case.localBackPipeline == LocalBackPipeline.ComposePredictive,
                policy.localPredictiveBackEnabled,
                case.name
            )
        }
    }
}
