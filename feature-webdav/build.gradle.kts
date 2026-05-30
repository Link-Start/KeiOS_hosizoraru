plugins {
    id("com.android.library")
}

android {
    namespace = "os.kei.feature.webdav"
    compileSdk = 37

    defaultConfig {
        minSdk = 35
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":core-concurrency"))
    implementation(project(":core-log"))
    implementation(project(":core-io"))
    implementation(project(":core-prefs"))

    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")

    // WebDAV client (used by DAVx⁵, production-grade)
    implementation("com.github.bitfireAT:dav4jvm:main-SNAPSHOT")
}
