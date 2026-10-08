plugins {
    id("keios.android.library")
}

android {
    namespace = "os.kei.core.notification"

    defaultConfig {
        consumerProguardFiles("src/main/keepRules/core-notification-rules.keep")
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":core-log"))
    implementation(project(":core-prefs"))
    implementation(project(":core-system"))

    implementation(libs.androidx.core.ktx)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit4)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.robolectric)
}
