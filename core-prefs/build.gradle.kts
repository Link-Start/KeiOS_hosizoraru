plugins {
    id("keios.android.library")
}

android {
    namespace = "os.kei.core.prefs"

}

dependencies {
    implementation(project(":core-concurrency"))
    implementation(project(":core-log"))

    api(libs.mmkv)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit4)
}
