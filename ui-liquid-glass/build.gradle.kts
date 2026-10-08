import os.kei.buildlogic.miuixVersion

plugins {
    id("keios.android.library")
    id("keios.android.compose")
    id("keios.miuix")
}

val miuixVersion = miuixVersion()

android {
    namespace = "os.kei.ui.liquidglass"

    lint {
        abortOnError = true
        checkDependencies = false
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    api(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.ui.tooling.preview)
    api("top.yukonga.miuix.kmp:miuix-ui-android:$miuixVersion")
    api("top.yukonga.miuix.kmp:miuix-icons-android:$miuixVersion")
    api("top.yukonga.miuix.kmp:miuix-squircle-android:$miuixVersion")
    api("top.yukonga.miuix.kmp:miuix-blur-android:$miuixVersion")
    api(libs.kyant.backdrop)
    api(libs.kyant.capsule)
    api(libs.kyant.shapes)
    api(libs.lucide.icons)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit4)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.robolectric)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
