plugins {
    id("keios.android.library")
}

android {
    namespace = "os.kei.feature.github.engine"

}

dependencies {
    implementation(project(":core-concurrency"))
    implementation(project(":core-download"))
    implementation(project(":core-io"))
    implementation(project(":core-json"))
    implementation(project(":core-versioning"))

    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit4)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.xmlpull)
    testImplementation(libs.kxml2)
}
