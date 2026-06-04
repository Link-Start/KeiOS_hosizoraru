plugins {
    id("com.android.library")
}

android {
    namespace = "os.kei.core.prefs"
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

    api("com.tencent:mmkv:2.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")

    testImplementation("org.jetbrains.kotlin:kotlin-test:2.3.21")
    testImplementation("junit:junit:4.13.2")
}
