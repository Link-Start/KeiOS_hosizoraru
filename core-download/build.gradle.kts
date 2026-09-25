plugins {
    alias(libs.plugins.android.library)
}

val liveBenchmarkSystemPropertyKeys =
    listOf(
        "keios.download.liveBenchmark",
        "keios.download.liveUrl",
        "keios.download.liveBytes",
        "keios.download.liveSha256",
        "keios.download.liveRuns",
        "keios.download.maxConnections",
        "keios.download.partMiB",
        "keios.download.protocol",
        "keios.download.controlledBenchmark",
    )

android {
    namespace = "os.kei.core.download"
    compileSdk = libs.versions.compile.sdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.min.sdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.java.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.java.get())
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.all { test ->
            test.systemProperty("okhttp.platform", "jdk9")
            liveBenchmarkSystemPropertyKeys.forEach { key ->
                providers.systemProperty(key).orNull?.let { value ->
                    test.systemProperty(key, value)
                }
            }
            test.testLogging.showStandardStreams =
                providers.systemProperty("keios.download.liveBenchmark")
                    .orNull
                    ?.equals("true", ignoreCase = true) == true ||
                providers.systemProperty("keios.download.controlledBenchmark")
                    .orNull
                    ?.equals("true", ignoreCase = true) == true
        }
    }
}

dependencies {
    implementation(project(":core-io"))

    api(libs.okhttp)
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit4)
    testImplementation(libs.okhttp.mockwebserver)
}
