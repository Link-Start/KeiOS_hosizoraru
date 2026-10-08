import os.kei.buildlogic.configureAndroid
import os.kei.buildlogic.libs
import os.kei.buildlogic.version

plugins {
    id("com.android.test")
}

configureAndroid(android)

android {
    defaultConfig {
        targetSdk = project.libs.version("target-sdk").toInt()
    }
    // The producer plugin derives benchmark/nonMinified names itself; adding prefixed names
    // here would produce benchmarkBenchmarkReleaseDiagnostic and break the consumer pairing.
    buildTypes {
        maybeCreate("releaseDiagnostic").apply {
            matchingFallbacks += listOf("release")
        }
    }
}
