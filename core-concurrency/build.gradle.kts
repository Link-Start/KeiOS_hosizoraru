plugins {
    id("com.android.library")
}

android {
    namespace = "os.kei.core.concurrency"
    compileSdk = 37

    defaultConfig {
        minSdk = 35
    }

    buildTypes {
        create("benchmarkRelease") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
        }
        create("nonMinifiedRelease") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
}
