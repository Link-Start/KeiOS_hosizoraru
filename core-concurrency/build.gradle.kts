plugins {
    id("keios.android.library")
}

android {
    namespace = "os.kei.core.concurrency"

}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
}
