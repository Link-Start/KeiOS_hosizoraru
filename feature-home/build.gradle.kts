plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "os.kei.feature.home"
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
    implementation(project(":core-prefs"))
    implementation(project(":feature-ba"))
    implementation(project(":feature-mcp"))
    implementation(project(":feature-github"))

    implementation(libs.androidx.compose.runtime)
    implementation(libs.mmkv)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit4)
}
