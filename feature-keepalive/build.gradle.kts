plugins {
    id("keios.android.library")
}

android {
    namespace = "os.kei.feature.keepalive"

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":core-concurrency"))
    implementation(project(":core-json"))
    implementation(project(":core-log"))
    implementation(project(":core-prefs"))
    implementation(project(":core-system"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.mmkv)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit4)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.robolectric)
}
