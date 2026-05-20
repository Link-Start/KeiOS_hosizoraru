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
    api("com.tencent:mmkv:2.4.0")
}
