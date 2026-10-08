package os.kei.buildlogic

import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class VerifyProfileCaptureTaskTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val journeys = listOf(
        "startupAndFirstScroll", "mainPagesAndNavigation", "commonRoutesAndChrome",
        "gitHubTrackingCore", "baOfficeAndCatalogCore", "adaptiveLargeScreenCore",
    )

    @Test
    fun completePhoneAndTabletCapturePasses() {
        val fixture = fixture(listOf("phone", "tablet"))
        fixture.writeDevice("phone")
        fixture.writeDevice("tablet")
        fixture.task.verify()
    }

    @Test
    fun successfulTransportWithZeroTestsIsRejected() {
        val fixture = fixture(listOf("phone"))
        fixture.writeDevice("phone", completedJourneys = emptyList())
        assertThrows(IllegalStateException::class.java) { fixture.task.verify() }
    }

    @Test
    fun missingProfileOutputIsRejected() {
        val fixture = fixture(listOf("phone"))
        fixture.writeDevice("phone")
        fixture.outputs.walkTopDown().first { it.isFile }.delete()
        assertThrows(IllegalStateException::class.java) { fixture.task.verify() }
    }

    @Test
    fun stalePreviousCaptureCannotReplaceCurrentProfiles() {
        val fixture = fixture(listOf("phone"))
        fixture.writeDevice("phone")
        fixture.marker.writeText((System.currentTimeMillis() + 60_000L).toString())
        assertThrows(IllegalStateException::class.java) { fixture.task.verify() }
    }

    @Test
    fun missingSelectedDeviceIsRejected() {
        val fixture = fixture(listOf("phone", "tablet"))
        fixture.writeDevice("phone")
        assertThrows(IllegalStateException::class.java) { fixture.task.verify() }
    }

    @Test
    fun failedJourneyIsRejectedEvenWithAllOutputFiles() {
        val fixture = fixture(listOf("phone"))
        fixture.writeDevice("phone", failedJourney = journeys.first())
        assertThrows(IllegalStateException::class.java) { fixture.task.verify() }
    }

    private fun fixture(devices: List<String>): Fixture {
        val directory = temporaryFolder.newFolder()
        val project = ProjectBuilder.builder().withProjectDir(directory).build()
        val outputs = File(directory, "outputs").apply { mkdirs() }
        val results = File(directory, "results").apply { mkdirs() }
        val marker = File(directory, "start.txt").apply { writeText("1") }
        val task = project.tasks.register("verifyCapture", VerifyProfileCaptureTask::class.java).get()
        task.captureFiles.from(project.fileTree(outputs))
        task.testResults.from(project.fileTree(results))
        task.captureStartedAt.set(marker)
        task.journeys.set(journeys)
        task.deviceSerials.set(devices)
        return Fixture(task, outputs, results, marker)
    }

    private inner class Fixture(
        val task: VerifyProfileCaptureTask,
        val outputs: File,
        val results: File,
        val marker: File,
    ) {
        fun writeDevice(
            serial: String,
            completedJourneys: List<String> = journeys,
            failedJourney: String? = null,
        ) {
            File(results, "TEST-$serial.xml").writeText(
                """<testsuite><properties><property name="device" value="$serial"/></properties>""" +
                    completedJourneys.joinToString("") { journey ->
                        """<testcase classname="os.kei.baselineprofile.BaselineProfileGenerator" name="$journey">""" +
                            (if (journey == failedJourney) "<failure/>" else "") + "</testcase>"
                    } + "</testsuite>",
            )
            val deviceOutputs = File(outputs, serial).apply { mkdirs() }
            journeys.forEach { journey ->
                val kind = if (journey == "startupAndFirstScroll") "startup" else "baseline"
                File(deviceOutputs, "BaselineProfileGenerator_$journey-$kind-prof-2026-10-08-00-00-00.txt")
                    .writeText("HSPLos/kei/MainActivity;->onCreate(Landroid/os/Bundle;)V\n")
            }
        }
    }
}
