plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "os.kei.feature.ba"
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
    implementation(project(":feature-mcp"))

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit4)
}
