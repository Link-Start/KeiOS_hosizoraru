plugins {
    id("keios.android.library")
}

android {
    namespace = "os.kei.core.io"

}

dependencies {
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.okhttp.mockwebserver)
}
