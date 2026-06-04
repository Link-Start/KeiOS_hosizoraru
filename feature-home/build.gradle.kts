plugins {
    id("com.android.library")
}

val composeVersion = "1.11.2"

android {
    namespace = "os.kei.feature.home"
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
    }
}

dependencies {
    implementation(project(":core-prefs"))
    implementation(project(":feature-mcp"))
    implementation(project(":feature-github"))

    implementation("androidx.compose.runtime:runtime:$composeVersion")
    implementation("com.tencent:mmkv:2.4.0")

    testImplementation("org.jetbrains.kotlin:kotlin-test:2.3.21")
    testImplementation("junit:junit:4.13.2")
}
