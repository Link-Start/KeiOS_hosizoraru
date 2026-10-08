package os.kei.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import javax.xml.parsers.DocumentBuilderFactory

/** A transport failure can currently produce a successful connected task with zero tests. */
@DisableCachingByDefault(because = "Verifies fresh device outputs and their modification times for each capture")
abstract class VerifyProfileCaptureTask : DefaultTask() {
    @get:InputFiles
    abstract val captureFiles: ConfigurableFileCollection

    @get:InputFile
    abstract val captureStartedAt: RegularFileProperty

    @get:InputFiles
    abstract val testResults: ConfigurableFileCollection

    @get:Input
    abstract val journeys: ListProperty<String>

    @get:Input
    abstract val deviceSerials: ListProperty<String>

    @TaskAction
    fun verify() {
        val startedAt = captureStartedAt.get().asFile.readText().trim().toLong()
        val files = captureFiles.files
        val expectedJourneys = journeys.get().toSet()
        val parser = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }.newDocumentBuilder()
        val selectedDevices = deviceSerials.get().toSet()
        check(selectedDevices.isNotEmpty()) {
            "Bind profile collection to the intended devices with ANDROID_SERIAL (comma-separated for multiple devices)."
        }
        val reports = testResults.files
            .filter { it.lastModified() >= startedAt }
            .map { file ->
                val document = parser.parse(file)
                val properties = document.getElementsByTagName("property")
                val serial = (0 until properties.length)
                    .map { properties.item(it) as org.w3c.dom.Element }
                    .singleOrNull { it.getAttribute("name") == "device" }
                    ?.getAttribute("value")
                val cases = document.getElementsByTagName("testcase")
                val completed = (0 until cases.length)
                    .map { cases.item(it) as org.w3c.dom.Element }
                    .filter { it.getAttribute("classname") == "os.kei.baselineprofile.BaselineProfileGenerator" }
                Triple(file, serial, completed)
            }
        check(reports.size == selectedDevices.size &&
            reports.map { it.second }.toSet() == selectedDevices &&
            reports.all { (_, _, completed) ->
                completed.size == expectedJourneys.size &&
                    completed.map { it.getAttribute("name") }.toSet() == expectedJourneys &&
                    completed.none { testcase ->
                        listOf("failure", "error", "skipped").any { testcase.getElementsByTagName(it).length > 0 }
                    }
            }
        ) {
            "Incomplete Baseline Profile capture: all six journeys must pass in fresh test results on every selected device. " +
                "Keep the accepted source profiles; inspect connected-device/test results."
        }
        // Benchmark 1.5 emits a startup file instead of a second baseline file for this journey.
        // The consumer plugin includes startup rules in the merged baseline as well.
        val expected = expectedJourneys.map { journey ->
            val kind = if (journey == "startupAndFirstScroll") "startup" else "baseline"
            "BaselineProfileGenerator_$journey-$kind-prof"
        }
        reports.forEach { (report, serial, _) ->
            val deviceOutputName = report.name.removePrefix("TEST-").removeSuffix(".xml")
            expected.forEach { name ->
                // Benchmark 1.5 attaches its dated export to the test result; the undated sibling
                // remains on the device and is not pulled by every AGP/device combination.
                val fileName = Regex(Regex.escape(name) + "(?:-\\d{4}(?:-\\d{2}){5})?\\.txt")
                check(files.any { file ->
                    file.parentFile.name == deviceOutputName && fileName.matches(file.name) &&
                        file.lastModified() >= startedAt &&
                        file.useLines { lines -> lines.any { it.isNotBlank() && !it.startsWith("#") } }
                }) {
                    "Incomplete Baseline Profile capture: missing fresh nonempty $name for $serial. " +
                        "Keep the accepted source profiles; inspect connected-device/test results."
                }
            }
        }
    }
}
