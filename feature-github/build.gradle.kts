plugins {
    alias(libs.plugins.android.library)
}

// Opt-in GitHubStrategyLiveBenchmarkTest switches. A -D on the Gradle command line reaches the test JVM
// only through this list; the test also reads env vars and ~/.gradle/gradle.properties.
// keios.github.api.token is deliberately not forwarded: a value read here is stored in the
// configuration cache (on in gradle.properties), so a token would land on disk in plain text. Give
// the token through the environment or ~/.gradle/gradle.properties, which the test reads itself.
val liveBenchmarkSystemPropertyKeys =
    listOf(
        "keios.github.liveBenchmark",
        "keios.github.liveTargets",
        "keios.github.forceGuest",
    )

android {
    namespace = "os.kei.feature.github"
    compileSdk = libs.versions.compile.sdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.min.sdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.java.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.java.get())
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            test.systemProperty("okhttp.platform", "jdk9")
            liveBenchmarkSystemPropertyKeys.forEach { key ->
                providers.systemProperty(key).orNull?.let { value ->
                    test.systemProperty(key, value)
                }
            }
        }
    }
}

dependencies {
    implementation(project(":core-concurrency"))
    implementation(project(":core-download"))
    implementation(project(":core-io"))
    implementation(project(":core-json"))
    implementation(project(":core-log"))
    implementation(project(":core-prefs"))
    implementation(project(":core-system"))
    implementation(project(":core-versioning"))
    api(project(":feature-github-engine"))
    implementation(project(":feature-mcp"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.hidden.api.bypass)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit4)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.xmlpull)
    testImplementation(libs.kxml2)
}
