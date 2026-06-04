plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val okhttpVersion = "5.3.2"

android {
    namespace = "os.kei.feature.mcp"
    compileSdk = 37

    defaultConfig {
        minSdk = 35
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

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
    implementation(project(":core-prefs"))
    implementation(project(":core-system"))

    implementation("androidx.core:core-ktx:1.18.0")
    implementation("com.tencent:mmkv:2.4.0")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")
    api("io.modelcontextprotocol:kotlin-sdk:0.13.0")
    implementation("io.ktor:ktor-server-cio:3.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("com.xzakota.hyper.notification:focus-api:1.4")

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("androidx.test.ext:junit:1.3.0")
    testImplementation("com.squareup.okhttp3:okhttp:$okhttpVersion")
    testImplementation("org.robolectric:robolectric:4.16.1")
}
