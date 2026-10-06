import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction
import javax.xml.parsers.DocumentBuilderFactory

/** A transport failure can currently produce a successful connected task with zero tests. */
abstract class VerifyProfileCaptureTask : DefaultTask() {
    @get:InputFiles
    abstract val captureFiles: ConfigurableFileCollection

    @get:InputFile
    abstract val captureStartedAt: RegularFileProperty

    @get:InputFiles
    abstract val testResults: ConfigurableFileCollection

    @get:Input
    abstract val journeys: ListProperty<String>

    @TaskAction
    fun verify() {
        val startedAt = captureStartedAt.get().asFile.readText().trim().toLong()
        val files = captureFiles.files
        val expectedJourneys = journeys.get().toSet()
        val parser = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }.newDocumentBuilder()
        val completed = testResults.files
            .filter { it.lastModified() >= startedAt }
            .flatMap { file ->
                val cases = parser.parse(file).getElementsByTagName("testcase")
                (0 until cases.length).map { cases.item(it) as org.w3c.dom.Element }
            }
            .filter { it.getAttribute("classname") == "os.kei.baselineprofile.BaselineProfileGenerator" }
        check(completed.size == expectedJourneys.size &&
            completed.map { it.getAttribute("name") }.toSet() == expectedJourneys &&
            completed.none { testcase ->
                listOf("failure", "error", "skipped").any { testcase.getElementsByTagName(it).length > 0 }
            }
        ) {
            "Incomplete Baseline Profile capture: all six journeys must pass in fresh test results. " +
                "Keep the accepted source profiles; inspect connected-device/test results."
        }
        // Benchmark 1.5 emits a startup file instead of a second baseline file for this journey.
        // The consumer plugin includes startup rules in the merged baseline as well.
        val expected = expectedJourneys.map { journey ->
            val kind = if (journey == "startupAndFirstScroll") "startup" else "baseline"
            "BaselineProfileGenerator_$journey-$kind-prof.txt"
        }
        expected.forEach { name ->
            check(files.any { file ->
                file.name == name && file.lastModified() >= startedAt &&
                    file.useLines { lines -> lines.any { it.isNotBlank() && !it.startsWith("#") } }
            }) {
                "Incomplete Baseline Profile capture: missing fresh nonempty $name. " +
                    "Keep the accepted source profiles; inspect connected-device/test results."
            }
        }
    }
}

plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    // com.android.test derives its self-instrumenting APK identity from namespace.
    namespace = "os.kei.baselineprofile.capture"
    compileSdk = libs.versions.compile.sdk.get().toInt()

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.java.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.java.get())
    }

    defaultConfig {
        minSdk = libs.versions.min.sdk.get().toInt()
        targetSdk = libs.versions.target.sdk.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

}

// This is the configuration block for the Baseline Profile plugin.
// You can specify to run the generators on a managed devices or connected devices.
baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.espresso.core)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
}

androidComponents {
    onVariants { v ->
        val artifactsLoader = v.artifacts.getBuiltArtifactsLoader()
        v.instrumentationRunnerArguments.put(
            "targetAppId",
            v.testedApks.map { artifactsLoader.load(it)?.applicationId }
        )
        if (v.buildType == "nonMinifiedRelease") {
            // Generation should run the six collectors, not execute/skip the frame benchmarks.
            v.instrumentationRunnerArguments.put("class", "os.kei.baselineprofile.BaselineProfileGenerator")
        }
    }
}

val captureStartMarker = layout.buildDirectory.file("intermediates/profile-capture-start.txt")
tasks.matching { it.name == "connectedNonMinifiedReleaseAndroidTest" }.configureEach {
    val marker = captureStartMarker
    doFirst {
        marker.get().asFile.apply {
            parentFile.mkdirs()
            writeText(System.currentTimeMillis().toString())
        }
    }
}
val verifyProfileCapture = tasks.register<VerifyProfileCaptureTask>("verifyNonMinifiedReleaseProfileCapture") {
    dependsOn("connectedNonMinifiedReleaseAndroidTest")
    captureStartedAt.set(captureStartMarker)
    captureFiles.from(fileTree(layout.buildDirectory.dir(
        "outputs/connected_android_test_additional_output/nonMinifiedRelease",
    )))
    testResults.from(fileTree(layout.buildDirectory.dir(
        "outputs/androidTest-results/connected/nonMinifiedRelease",
    )) { include("TEST-*.xml") })
    journeys.set(listOf(
        "startupAndFirstScroll", "mainPagesAndNavigation", "commonRoutesAndChrome",
        "gitHubTrackingCore", "baOfficeAndCatalogCore", "adaptiveLargeScreenCore",
    ))
}
tasks.matching { it.name == "collectNonMinifiedReleaseBaselineProfile" }.configureEach {
    dependsOn(verifyProfileCapture)
}
