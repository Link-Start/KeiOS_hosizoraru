import com.android.build.api.dsl.LibraryExtension

plugins {
    id("com.android.application") version "9.2.1" apply false
    id("io.github.takahirom.roborazzi") version "1.63.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.21" apply false
    id("com.android.test") version "9.2.1" apply false
    id("androidx.baselineprofile") version "1.5.0-alpha06" apply false
}

subprojects {
    plugins.withId("com.android.library") {
        extensions.configure<LibraryExtension>("android") {
            buildTypes {
                maybeCreate("benchmarkRelease").apply {
                    initWith(getByName("release"))
                    matchingFallbacks += listOf("release")
                }
            }
        }
    }
}
