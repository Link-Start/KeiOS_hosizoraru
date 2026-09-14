plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "os.kei.core.io"
    compileSdk = libs.versions.compile.sdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.min.sdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.java.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.java.get())
    }
}

dependencies {
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.okhttp.mockwebserver)
}
