plugins {
    id("keios.android.library")
}

android {
    namespace = "os.kei.core.system"

    buildFeatures {
        aidl = true
    }

    defaultConfig {
        consumerProguardFiles("src/main/keepRules/core-system-rules.keep")
    }

}

dependencies {
    implementation(project(":core-concurrency"))
    implementation(project(":core-log"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.hidden.api.bypass)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}
