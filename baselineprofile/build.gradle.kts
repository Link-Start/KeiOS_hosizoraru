import os.kei.buildlogic.VerifyProfileCaptureTask

plugins {
    id("keios.android.test")
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    // com.android.test derives its self-instrumenting APK identity from namespace.
    namespace = "os.kei.baselineprofile.capture"

    defaultConfig {
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
    deviceSerials.set(providers.environmentVariable("ANDROID_SERIAL").orElse("").map { serials ->
        serials.split(',').map(String::trim).filter(String::isNotEmpty)
    })
}
tasks.matching { it.name == "collectNonMinifiedReleaseBaselineProfile" }.configureEach {
    dependsOn(verifyProfileCapture)
}
