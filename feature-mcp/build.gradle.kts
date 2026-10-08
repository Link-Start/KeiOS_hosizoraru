plugins {
    id("keios.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "os.kei.feature.mcp"

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("okhttp.platform", "jdk9")
        }
    }
}

dependencies {
    implementation(project(":core-concurrency"))
    implementation(project(":core-io"))
    implementation(project(":core-json"))
    implementation(project(":core-log"))
    implementation(project(":core-notification"))
    implementation(project(":core-prefs"))
    implementation(project(":core-system"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.mmkv)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.hidden.api.bypass)
    api(libs.mcp.kotlin.sdk)
    implementation(platform(libs.ktor.bom))
    implementation(libs.ktor.server.cio)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit4)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.okhttp)
    testImplementation(libs.robolectric)
}
