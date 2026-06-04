plugins {
    id("com.android.library")
}

android {
    namespace = "os.kei.feature.os"
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
    implementation(project(":feature-mcp"))

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}
